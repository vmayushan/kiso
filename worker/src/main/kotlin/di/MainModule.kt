package kiso.worker.di

import kiso.worker.Worker
import kiso.worker.queues.JobProgressHandler
import kiso.worker.runner.Runner
import kiso.worker.queues.JobQueueProcessor

import org.koin.dsl.module

val mainModule = module {
    single { JobProgressHandler(redisStreamClient = get()) }
    single { JobQueueProcessor(redisStreamClient = get()) }
    single { Runner(progressHandler = get()) }
    single {
        Worker(
            config = get(),
            containerPool = get(),
            jobQueueProcessor = get(),
            runner = get()
        )
    }
}