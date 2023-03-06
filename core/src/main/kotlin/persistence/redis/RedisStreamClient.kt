package kiso.core.persistence.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.StreamMessage
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.Flow
import mu.KLogging
import kotlin.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisStreamClient(
    private val redisConnection: StatefulRedisConnection<String, String>
) {
    private val redis = redisConnection.coroutines()

    companion object : KLogging()

    suspend fun add(key: String, body: Map<String, String>, ttl: Duration? = null) {
        redis.xadd(key, body)
        ttl?.let { redis.expire(key, it.inWholeSeconds) }
    }

    fun readMessages(
        streamName: String,
        offset: String = "0"
    ): Flow<StreamMessage<String, String>> {
        val readArgs = XReadArgs()
        val streamOffset = XReadArgs.StreamOffset.from(streamName, offset)
        return redis.xread(readArgs, streamOffset)
    }

    suspend fun deleteStream(key: String) {
        redis.unlink(key)
    }

    suspend fun createConsumerGroup(streamName: String, groupName: String, offset: String = "0") {
        val groupExists = kotlin.runCatching {
            val groups = redis.xinfoGroups(streamName).map {
                (it as List<*>).map { field ->
                    if (field is ByteArray) {
                        field.toString(Charsets.UTF_8)
                    } else {
                        field
                    }
                }
            }

            groups.any { groupInfo ->
                val nameFieldIndex = groupInfo.indexOf("name")
                groupInfo[nameFieldIndex + 1] == groupName
            }
        }.getOrDefault(false)

        if (!groupExists) {
            logger.info { "The group $groupName already does not exist yet and has to be created" }
            redis.xgroupCreate(
                XReadArgs.StreamOffset.from(streamName, offset),
                groupName,
                XGroupCreateArgs.Builder.mkstream(true)
            )
        } else {
            logger.info { "The group $groupName already exists" }
        }
    }

    fun createProcessor(
        streamName: String,
        groupName: String,
        consumerName: String,
        config: RedisStreamProcessorConfig
    ): RedisStreamProcessor {
        return RedisStreamProcessor(
            redisConnection, streamName, groupName, consumerName, config
        )
    }
}