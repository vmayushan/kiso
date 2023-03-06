package kiso.core.persistence.redis

import io.lettuce.core.*
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration

data class RedisStreamProcessorConfig(
    val readInterval: Duration,
    val consumerHeartbeatInterval: Duration,
    val claimMessagesAfter: Duration,
    val maxDeliveryCount: Int,
    val deleteOnAck: Boolean
)

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisStreamProcessor(
    connection: StatefulRedisConnection<String, String>,
    private val streamName: String,
    private val groupName: String,
    private val consumerName: String,
    private val config: RedisStreamProcessorConfig,
) {
    private val redis = connection.coroutines()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    suspend fun startProcessing(messageHandler: suspend (body: Map<String, String>) -> Unit) = scope.launch {
        val messagesFlow = readNextMessage()
        messagesFlow.collect { message ->
            val deliveryCount = extractMessageDeliveryCount(message.id)
            if (deliveryCount > config.maxDeliveryCount) {
                completeMessage(message.id)
                return@collect
            }

            val heartbeatJob = resetMessageIdleTime(message.id).launchIn(scope)
            try {
                messageHandler(message.body)
                completeMessage(message.id)
            } finally {
                heartbeatJob.cancelAndJoin()
            }
        }
    }

    private suspend fun resetMessageIdleTime(messageId: String): Flow<Unit> = flow {
        while (true) {
            emit(
                redis.xclaim(
                    streamName,
                    Consumer.from(groupName, consumerName),
                    XClaimArgs().idle(0).justid(),
                    messageId
                ).collect()
            )
            delay(config.consumerHeartbeatInterval)
        }
    }

    private suspend fun completeMessage(messageId: String) {
        redis.xack(streamName, groupName, messageId)
        if (config.deleteOnAck) {
            redis.xdel(streamName, messageId)
        }
    }

    private suspend fun readNextMessage(): Flow<StreamMessage<String, String>> {
        val messagesFlow = flow {
            while (true) {
                emit(claimMessageOfOtherConsumer() ?: readMessageFromLastConsumed())
            }
        }
        return messagesFlow
            .transform {
                if (it != null) {
                    return@transform emit(it)
                } else {
                    delay(config.readInterval)
                }
            }
    }

    private suspend fun readMessageFromLastConsumed(): StreamMessage<String, String>? {
        return redis.xreadgroup(
            Consumer.from(groupName, consumerName),
            XReadArgs().count(1),
            XReadArgs.StreamOffset.lastConsumed(streamName)
        ).firstOrNull()
    }

    private suspend fun claimMessageOfOtherConsumer(): StreamMessage<String, String>? {
        val claimAfterMs = config.claimMessagesAfter.inWholeMilliseconds
        val pending = redis
            .xpending(
                streamName, XPendingArgs<String>()
                    .group(groupName)
                    .range(Range.unbounded())
                    .limit(Limit.from(1))
                    .idle(claimAfterMs)
            )
            .firstOrNull() ?: return null
        return redis.xclaim(
            streamName,
            Consumer.from(groupName, consumerName),
            XClaimArgs().minIdleTime(claimAfterMs),
            pending.id
        ).firstOrNull()
    }

    private fun extractMessageDeliveryCount(messageId: String): Int {
        if (messageId.contains('-')) {
            return messageId.split('-')[1].toInt()
        }
        return -1
    }
}