package kiso.api.services

import kiso.core.models.progress.ExecutionCompletedEvent
import kiso.core.models.progress.ExecutionProgressEvent
import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.deserializeEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class ExecutionJobProgressService(
    private val executionJobDao: ExecutionJobDao,
    private val redisStreamClient: RedisStreamClient
) {
    suspend fun getByJobId(jobId: String): List<ExecutionProgressEvent>? {
        val jobEventsInDb = executionJobDao.getJobById(jobId)
            ?: return null
        if (jobEventsInDb.isNotEmpty()) {
            return jobEventsInDb
        }

        // if progress is not found in DB - fallback to redis
        return redisStreamClient
            .readMessages(StreamName.jobProgress(jobId))
            .map { deserializeEvent(it.body) }
            .toList()
    }

    suspend fun listenByJobId(jobId: String): Flow<ExecutionProgressEvent>? {
        val jobEventsInDb = executionJobDao.getJobById(jobId)
            ?: return null
        if (jobEventsInDb.isNotEmpty()) {
            return jobEventsInDb.asFlow()
        }

        // if progress is not found in DB - fallback to redis
        return listenFromRedisByJobId(jobId)
    }

    private suspend fun listenFromRedisByJobId(jobId: String) = flow {
        var offset = "0"
        var isCompleted = false
        while (!isCompleted) {
            val messages = redisStreamClient.readMessages(StreamName.jobProgress(jobId), offset).toList()
            if (messages.isNotEmpty()) {
                offset = messages.last().id
                val deserialized = messages.map { deserializeEvent(it.body) }
                isCompleted = deserialized.any { it is ExecutionCompletedEvent }
                deserialized.forEach { emit(it) }
            } else {
                delay(100L)
            }
        }
    }
}