package kiso.worker

import kiso.worker.config.WorkerConfig
import kiso.worker.docker.pool.DockerContainerPool
import kiso.worker.queues.JobQueueProcessor
import kiso.worker.runner.Runner
import kotlinx.coroutines.*
import mu.KLogging

class Worker(
    private val config: WorkerConfig,
    private val containerPool: DockerContainerPool,
    private val jobQueueProcessor: JobQueueProcessor,
    private val runner: Runner
) {
    companion object : KLogging()

    private lateinit var creators: List<Job>
    private lateinit var processors: List<Job>

    suspend fun init() {
        if(config.buildLanguageImages) {
            containerPool.buildImages()
        }
        containerPool.removeStaledContainers()
    }

    suspend fun start() {
        creators = (1..config.creationParallelism).map {
            logger.info { "Starting container pool creator $it" }
            containerPool.startContainerCreator()
        }
        processors = (1..config.executionParallelism).map {
            logger.info { "Starting jobs queue processor $it" }
            startJobProcessor()
        }
    }

    suspend fun stop() {
        logger.info { "Cancelling container creation" }
        creators.forEach { it.cancelAndJoin() }

        logger.info { "Cancelling job execution" }
        processors.forEach { it.cancelAndJoin() }

        containerPool.removeStaledContainers()
    }

    private suspend fun startJobProcessor(): Job =
        jobQueueProcessor.startJobProcessor(config.instanceName) { job ->
            val container = containerPool.takeOrCreate(job.language.imageConfig)
            try {
                withContext(NonCancellable) {
                    runner.run(job, container)
                }
            } finally {
                container.removeContainer()
            }
        }
}