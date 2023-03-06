package kiso.api.di

import kiso.api.services.ExecutionJobProgressService
import kiso.core.persistence.redis.RedisStreamClient
import org.koin.dsl.module

val mainModule = module {RedisStreamClient
    single { ExecutionJobProgressService(executionJobDao = get(), redisStreamClient = get()) }
}