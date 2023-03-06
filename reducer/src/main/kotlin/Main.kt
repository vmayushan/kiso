package kiso.reducer

import kiso.core.di.mongoModule
import kiso.core.di.redisModule
import kiso.reducer.di.mainModule
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

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
    val app = startKoin {
        modules(mongoModule, redisModule, mainModule)
    }
    val reducer = app.koin.get<Reducer>()
    reducer.start()

    // waiting for SIGINT signal
    interruptSignal.receive()

    reducer.stop()

    // graceful shutdown finished, we can exit
    stopSignal.send(Unit)
}
