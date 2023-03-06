package com.example

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kiso.core.models.ExecutionStep
import kiso.core.models.progress.*
import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.serializeEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools
import java.util.*
import kotlin.test.assertContains
import kotlin.test.assertEquals

class GetJobProgressIntegrationTest {
    private val jobId = UUID.randomUUID().toString()
    @After
    fun tearDown() = runBlocking {
        getRedisStreamClient().deleteStream(StreamName.jobProgress(jobId))
        getExecutionJobDao().deleteJob(jobId)
        stopKoin()
    }

    @Test
    fun `get not existing job`() = testApplication {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/job/$jobId")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get empty just created job`() = testApplication {
        startApplication()
        getExecutionJobDao().insertJob(jobId)

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/job/$jobId")
        assertEquals(HttpStatusCode.OK, response.status)

        val responseBody = response.bodyAsText()
        assertEquals("", responseBody)
    }

    @Test
    fun `get completed job by id with events in mongo`() = testApplication {
        startApplication()
        getExecutionJobDao().insertJob(jobId)
        getExecutionJobDao().updateJobEvents(jobId, getExecutionProgressEvents())

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/job/$jobId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContainsFormattedEvents(response.bodyAsText())
    }

    @Test
    fun `get completed job by id with events in redis`() = testApplication {
        startApplication()
        getExecutionJobDao().insertJob(jobId)
        val redisStreamClient = getRedisStreamClient()
        getExecutionProgressEvents().forEach { redisStreamClient.add(StreamName.jobProgress(jobId), serializeEvent(it)) }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/job/$jobId")

        assertEquals(HttpStatusCode.OK, response.status)
        assertContainsFormattedEvents(response.bodyAsText())
    }

    private fun getExecutionProgressEvents() = listOf(
        ExecutionStartedEvent,
        ExecutionStepStartedEvent(ExecutionStep.Setup),
        ExecutionStepCompletedEvent(ExecutionStep.Setup, success = true, duration = 42, errorMessage = null),
        ExecutionStepStartedEvent(ExecutionStep.Compile),
        ExecutionLogEvent(ExecutionStep.Compile, LogType.Stdout, "Build Progress 50%"),
        ExecutionLogEvent(ExecutionStep.Compile, LogType.Stdout, "Build Progress 75%"),
        ExecutionLogEvent(ExecutionStep.Compile, LogType.Stderr, "Something went wrong"),
        ExecutionStepCompletedEvent(ExecutionStep.Compile, success = false, duration = 11, errorMessage = "Build failed"),
        ExecutionCompletedEvent
    )

    private fun assertContainsFormattedEvents(body: String) {
        listOf(
            "Execution started",
            "Step[name=Setup] started",
            "Step[name=Setup] completed in 42 ms",
            "Step[name=Compile] started",
            "Build Progress 50%",
            "Build Progress 75%",
            "STDERR:Something went wrong",
            "Step[name=Compile] failed with 'Build failed' error message in 11 ms",
            "Execution completed"
        ).forEach{
            assertContains(body, it)
        }
    }

    private fun getRedisStreamClient(): RedisStreamClient =
        KoinPlatformTools.defaultContext().get().get<RedisStreamClient>()

    private fun getExecutionJobDao(): ExecutionJobDao =
        KoinPlatformTools.defaultContext().get().get<ExecutionJobDao>()
}