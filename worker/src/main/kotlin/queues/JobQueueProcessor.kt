package kiso.worker.queues

import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.RedisStreamProcessorConfig
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.deserializeJob
import kiso.core.models.ExecutionJob

import kotlinx.coroutines.*
import mu.KLogging
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class JobQueueProcessor(
    private val redisStreamClient: RedisStreamClient
) {
    companion object : KLogging()

    private val streamConsumerGroup = "worker"
    private val streamProcessorConfig = RedisStreamProcessorConfig(
        readInterval = 1.seconds,
        claimMessagesAfter = 1.minutes,
        consumerHeartbeatInterval = 30.seconds,
        maxDeliveryCount = 3,
        deleteOnAck = true
    )

    init {
        runBlocking {
            redisStreamClient.createConsumerGroup(StreamName.jobsQueue, streamConsumerGroup)
        }
    }

    suspend fun startJobProcessor(consumer: String, jobHandler: suspend (job: ExecutionJob) -> Unit): Job {
        val processor = redisStreamClient.createProcessor(
            StreamName.jobsQueue, streamConsumerGroup, consumer, streamProcessorConfig
        )

        return processor.startProcessing {
            val job = deserializeJob(it)
            logger.info { "Processing job ${job.id}" }
            jobHandler(job)
        }
    }
}