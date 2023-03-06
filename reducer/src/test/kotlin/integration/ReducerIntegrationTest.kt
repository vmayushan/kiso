package integration

import kiso.core.di.mongoModule
import kiso.core.di.redisModule
import kiso.core.models.ExecutionStep
import kiso.core.models.progress.*
import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.serializeCompletedJob
import kiso.core.persistence.serialization.serializeEvent
import kiso.reducer.Reducer
import kiso.reducer.di.mainModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReducerIntegrationTest {
    companion object {
        private lateinit var redisStreamClient: RedisStreamClient
        private lateinit var executionJobDao: ExecutionJobDao
        private lateinit var reducer: Reducer

        @BeforeAll
        @JvmStatic
        fun startReducer(): Unit = runBlocking {
            val app = startKoin { modules(redisModule, mongoModule, mainModule) }
            executionJobDao = app.koin.get()
            redisStreamClient = app.koin.get()
            redisStreamClient.deleteStream(StreamName.completedJobs)

            reducer = app.koin.get()
            reducer.start()
        }

        @AfterAll
        @JvmStatic
        fun stopReducer(): Unit = runBlocking {
            reducer.stop()
        }
    }

    @Test
    fun `should move job progress from redis to mongo`(): Unit = runBlocking {
        // Arrange
        val jobId = UUID.randomUUID().toString()
        executionJobDao.insertJob(jobId)

        val events = listOf(
            ExecutionStartedEvent,
            ExecutionStepStartedEvent(ExecutionStep.Setup),
            ExecutionStepCompletedEvent(ExecutionStep.Setup, success = true, duration = 42L, errorMessage = null),
            ExecutionCompletedEvent
        )
        events.forEach {
            redisStreamClient.add(StreamName.jobProgress(jobId), serializeEvent(it))
        }

        // Act
        redisStreamClient.add(StreamName.completedJobs, serializeCompletedJob(jobId))

        // Assert
        val isRedisStreamRemoved = waitForStreamRemoval(jobId, 10.seconds)
        assertTrue { isRedisStreamRemoved }

        val eventsInDb = executionJobDao.getJobById(jobId)
        assertEquals(events.count(), eventsInDb?.count())
    }

    private suspend fun waitForStreamRemoval(jobId: String, timeout: Duration): Boolean {
        var isRemoved = false
        withTimeout(timeout) {
            while (!isRemoved) {
                val message = redisStreamClient.readMessages(StreamName.jobProgress(jobId)).firstOrNull()
                isRemoved = message == null
                if (isRemoved) {
                    break
                }
                delay(100.milliseconds)
            }
        }

        return isRemoved
    }
}