package kiso.reducer.di

import kiso.reducer.Reducer
import kiso.reducer.queues.CompletedJobProcessor
import org.koin.dsl.module

val mainModule = module {
    single { CompletedJobProcessor(redisStreamClient = get()) }
    single { Reducer(redisStreamClient = get(), mongoDao = get(), completedJobProcessor = get()) }
}