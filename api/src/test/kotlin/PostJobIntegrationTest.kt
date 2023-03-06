package com.example

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.decodeFromString
import io.ktor.server.testing.*
import kiso.api.models.request.CreateExecutionJob
import kiso.api.models.response.ExecutionJobCreated
import kiso.core.models.Package
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.deserializeJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class PostJobIntegrationTest {
    @After
    fun tearDown() = runBlocking {
        getRedisStreamClient().deleteStream(StreamName.jobsQueue)
        stopKoin()
    }

    @Test
    fun `post request with invalid language`() = testApplication {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/job") {
            contentType(ContentType.Application.Json)
            setBody(CreateExecutionJob("Java", "", emptyList()))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Invalid language")
    }

    @Test
    fun `post request with invalid package name`() = testApplication {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/job") {
            contentType(ContentType.Application.Json)
            setBody(CreateExecutionJob("CSharp", "", listOf(Package("Inv@lid\"Name"))))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "Invalid package name")
    }

    @Test
    fun `post request with valid data`() = testApplication {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.post("/job") {
            contentType(ContentType.Application.Json)
            setBody(CreateExecutionJob("CSharp", "Hello world", listOf(Package("Newtonsoft.Json"))))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val responseDto = Json.decodeFromString<ExecutionJobCreated>(response.bodyAsText())

        val jobInTheQueue = getRedisStreamClient()
            .readMessages(StreamName.jobsQueue)
            .map { deserializeJob(it.body) }
            .filter { it.id == responseDto.id }
            .firstOrNull()
        assertNotNull(jobInTheQueue)
    }

    private fun getRedisStreamClient(): RedisStreamClient =
        KoinPlatformTools.defaultContext().get().get<RedisStreamClient>()
}
