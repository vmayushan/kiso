package kiso.reducer

import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.deserializeEvent
import kiso.reducer.config.ReducerConfig
import kiso.reducer.queues.CompletedJobProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

class Reducer(
    private val redisStreamClient: RedisStreamClient,
    private val mongoDao: ExecutionJobDao,
    private val completedJobProcessor: CompletedJobProcessor
) {
    private lateinit var coroutineJob: Job

    suspend fun start() {
        coroutineJob = completedJobProcessor.startCompletedJobProcessor(ReducerConfig.instanceName) { jobId ->
            val events = redisStreamClient.readMessages(StreamName.jobProgress(jobId))
                .map { message -> deserializeEvent(message.body) }.toList()
            withContext(NonCancellable) {
                if(!mongoDao.updateJobEvents(jobId, events)) {
                    throw Throwable("Could not save job events to database")
                }
                redisStreamClient.deleteStream(StreamName.jobProgress(jobId))
            }
        }
    }

    suspend fun stop() {
        coroutineJob.cancelAndJoin()
    }
}