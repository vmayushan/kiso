package kiso.core.di

import io.lettuce.core.RedisClient
import kiso.core.persistence.redis.DefaultRedisConfig
import kiso.core.persistence.redis.RedisConfig
import kiso.core.persistence.redis.RedisStreamClient
import org.koin.dsl.module

val redisModule = module {
    single { DefaultRedisConfig }
    single { RedisClient.create(get<RedisConfig>().uri).connect() }
    single { RedisStreamClient(redisConnection = get()) }
}