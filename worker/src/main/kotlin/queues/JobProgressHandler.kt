package kiso.worker.queues

import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.serializeCompletedJob
import kiso.core.persistence.serialization.serializeEvent
import kiso.core.models.progress.ExecutionCompletedEvent
import kiso.core.models.progress.ExecutionProgressEvent

import mu.KLogging
import kotlin.time.Duration.Companion.hours

class JobProgressHandler(
    private val redisStreamClient: RedisStreamClient,
) {
    private val progressEventTtl = 1.hours

    companion object : KLogging()

    suspend fun report(jobId: String, event: ExecutionProgressEvent) {
        redisStreamClient.add(StreamName.jobProgress(jobId), serializeEvent(event), progressEventTtl)

        if (event is ExecutionCompletedEvent) {
            redisStreamClient.add(StreamName.completedJobs, serializeCompletedJob(jobId))
        }
    }
}