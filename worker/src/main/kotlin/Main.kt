package kiso.worker

import kiso.worker.config.WorkerConfig
import kiso.worker.di.dockerModule
import kiso.worker.di.mainModule
import kiso.core.di.redisModule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

fun main() = runBlocking {
    val interruptSignal = Channel<Unit>(Channel.RENDEZVOUS)
    val stopSignal = Channel<Unit>(Channel.RENDEZVOUS)

    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            interruptSignal.send(Unit)
            stopSignal.receive()
            Runtime.getRuntime().halt(0)
        }
    })

    val app = startKoin { modules(getConfigModule(), dockerModule, redisModule, mainModule) }
    val worker = app.koin.get<Worker>()

    worker.init()
    worker.start()

    // waiting for SIGINT signal
    interruptSignal.receive()

    worker.stop()

    // graceful shutdown finished, we can exit
    stopSignal.send(Unit)
}

fun getConfigModule(): Module {
    return module {
        single {
            WorkerConfig(
                creationParallelism = System.getenv("CREATION_WORKERS")?.toIntOrNull() ?: 1,
                executionParallelism = System.getenv("EXECUTION_WORKERS")?.toIntOrNull() ?: 1,
                buildLanguageImages = System.getenv("BUILD_LANGUAGE_IMAGES")?.toBooleanStrictOrNull() ?: true
            )
        }
    }
}
