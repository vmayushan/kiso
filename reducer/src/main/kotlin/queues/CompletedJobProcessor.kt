package kiso.reducer.queues

import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.RedisStreamProcessorConfig
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.deserializeCompletedJob

import kotlinx.coroutines.*
import mu.KLogging
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class CompletedJobProcessor(
    private val redisStreamClient: RedisStreamClient
) {
    companion object : KLogging()

    private val streamConsumerGroup = "reducer"
    private val streamProcessorConfig = RedisStreamProcessorConfig(
        readInterval = 1.seconds,
        claimMessagesAfter = 1.minutes,
        consumerHeartbeatInterval = 30.seconds,
        maxDeliveryCount = 3,
        deleteOnAck = true
    )

    init {
        runBlocking {
            redisStreamClient.createConsumerGroup(StreamName.completedJobs, streamConsumerGroup)
        }
    }

    suspend fun startCompletedJobProcessor(
        consumer: String,
        completedJobHandler: suspend (jobId: String) -> Unit
    ): Job {
        val processor = redisStreamClient.createProcessor(
            StreamName.completedJobs, streamConsumerGroup, consumer, streamProcessorConfig
        )

        logger.info { "Starting completed jobs processor" }

        return processor.startProcessing {
            val jobId = deserializeCompletedJob(it)

            logger.info { "Processing job $jobId" }

            completedJobHandler(jobId)
        }
    }
}