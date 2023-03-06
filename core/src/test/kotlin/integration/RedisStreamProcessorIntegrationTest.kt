package integration

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.RedisStreamProcessor
import kiso.core.persistence.redis.RedisStreamProcessorConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisStreamProcessorIntegrationTest {
    companion object {
        private lateinit var redisClient: StatefulRedisConnection<String, String>
        private lateinit var redisStreamClient: RedisStreamClient
        private const val groupName = "test-group"

        private val streamProcessorConfig = RedisStreamProcessorConfig(
            readInterval = 1.seconds,
            claimMessagesAfter = 3.seconds,
            consumerHeartbeatInterval = 1.seconds,
            maxDeliveryCount = 2,
            deleteOnAck = true
        )

        @BeforeAll
        @JvmStatic
        fun setUp(): Unit = runBlocking {
            redisClient = RedisClient.create("redis://localhost:6379").connect()
            redisStreamClient = RedisStreamClient(redisClient)
        }
    }

    @Test
    fun `message is processed by other consumer after permanent failure`(): Unit = runBlocking {
        // create stream and consumer group and add test message to it
        val brokenConsumerName = "broken-consumer"
        val goodConsumerName = "good-consumer"

        val streamName = "test-stream-${UUID.randomUUID()}"
        redisStreamClient.createConsumerGroup(streamName, groupName)
        redisStreamClient.add(streamName, mapOf("test_key" to "test_value"), ttl = 1.minutes)

        // consumer 1 takes the job, never finishes it and goes offline
        val consumedMessage = Channel<Map<String, String>>(Channel.RENDEZVOUS)
        val brokenConsumer = createConsumer(streamName, brokenConsumerName) {
            consumedMessage.send(it)
            delay(INFINITE) // consumer has stuck
        }

        // assert "broken" consumer received the message we sent
        val message1 = consumedMessage.receive()
        assertEquals("test_value", message1["test_key"])
        brokenConsumer.cancelAndJoin()

        // new consumer has started, it should pick up the same message
        createConsumer(streamName, goodConsumerName) { consumedMessage.send(it) }

        // assert "good" consumer received the same message we sent
        val message2 = consumedMessage.receive()
        assertEquals("test_value", message2["test_key"])
    }

    @Test
    fun `message is processed again by same consumer after restart`(): Unit = runBlocking {
        // create stream and consumer group and add test message to it
        val consumerName = "consumer1"
        val streamName = "test-stream-${UUID.randomUUID()}"
        redisStreamClient.createConsumerGroup(streamName, groupName)
        redisStreamClient.add(streamName, mapOf("test_key" to "test_value"), ttl = 1.minutes)

        // consumer takes the job, never finishes it and goes offline
        val consumedMessage = Channel<Map<String, String>>(Channel.RENDEZVOUS)
        val brokenConsumer = createConsumer(streamName, consumerName) {
            consumedMessage.send(it)
            delay(INFINITE) // consumer has stuck
        }

        // assert consumer received the message we sent
        val message1 = consumedMessage.receive()
        assertEquals("test_value", message1["test_key"])
        brokenConsumer.cancelAndJoin()

        // consumer has restarted, it should pick up the same message again
        createConsumer(streamName, consumerName) { consumedMessage.send(it) }

        // assert consumer received the same message we sent
        val message2 = consumedMessage.receive()
        assertEquals("test_value", message2["test_key"])
    }

    @Test
    fun `parallel processing of 1000 messages by 10 consumers`(): Unit = runBlocking {
        // create stream and consumer group
        val consumerName = "consumer1"
        val streamName = "test-stream-${UUID.randomUUID()}"
        redisStreamClient.createConsumerGroup(streamName, groupName)

        val numberOfMessages = 1000
        val numberOfConsumers = 10

        // start N parallel consumers
        val consumedMessages = Channel<Map<String, String>>(2 * numberOfMessages)

        repeat(numberOfConsumers) {
            createConsumer(streamName, consumerName) { consumedMessages.send(it) }
        }

        // Act: add messages to the stream
        repeat(numberOfMessages) {
            redisStreamClient.add(streamName, mapOf("test_key" to it.toString()), ttl = 1.minutes)
        }

        // Assert
        var receivedMessages = 0
        withTimeout(1.minutes) {
            while (receivedMessages != 1000) {
                consumedMessages.receive()
                receivedMessages++
            }
        }
        assertEquals(numberOfMessages, receivedMessages)
    }

    private suspend fun createConsumer(
        streamName: String,
        consumerName: String,
        messageHandler: suspend (body: Map<String, String>) -> Unit
    ): Job {
        val consumer = RedisStreamProcessor(
            redisClient,
            streamName, groupName, consumerName, streamProcessorConfig
        )
        return consumer.startProcessing(messageHandler)
    }
}