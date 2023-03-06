package com.example

import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kiso.core.models.ExecutionStep
import kiso.core.models.progress.*
import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.serializeEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatformTools
import java.util.*
import kotlin.test.assertEquals

class WebSocketJobProgressIntegrationTest {
    private val jobId = UUID.randomUUID().toString()

    @After
    fun tearDown() = runBlocking {
        getRedisStreamClient().deleteStream(StreamName.jobProgress(jobId))
        getExecutionJobDao().deleteJob(jobId)
        stopKoin()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun `websocket realtime logs`() = testApplication {
        val client = createClient {
            install(WebSockets)
        }

        startApplication()
        getExecutionJobDao().insertJob(jobId)

        val numberOfMessages = 1000

        GlobalScope.launch {
            /// 999 logs and 1 completion
            repeat(numberOfMessages - 1) {
                addEventToRedis(
                    ExecutionLogEvent(
                        ExecutionStep.Compile,
                        LogType.Stdout, "message $it"
                    )
                )
            }
            addEventToRedis(ExecutionCompletedEvent)
        }

        var receivedMessages = 0
        client.webSocket("/ws/job/$jobId") {
            for (message in incoming) {
                receivedMessages++
            }
        }

        assertEquals(numberOfMessages, receivedMessages)
    }

    private suspend fun addEventToRedis(event: ExecutionProgressEvent) {
        val redisStreamClient = getRedisStreamClient()
        redisStreamClient.add(StreamName.jobProgress(jobId), serializeEvent(event))
    }

    private fun getRedisStreamClient(): RedisStreamClient =
        KoinPlatformTools.defaultContext().get().get<RedisStreamClient>()

    private fun getExecutionJobDao(): ExecutionJobDao =
        KoinPlatformTools.defaultContext().get().get<ExecutionJobDao>()
}