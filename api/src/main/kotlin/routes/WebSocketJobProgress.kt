package kiso.api.routes

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.cio.*
import io.ktor.websocket.*
import kiso.api.services.ExecutionJobProgressService
import kiso.api.services.ExecutionProgressFormatter
import kotlinx.coroutines.cancel
import org.koin.ktor.ext.inject

fun Application.routingWebSocketJobProgress() {
    val progressService by inject<ExecutionJobProgressService>()

    routing {
        webSocket("/ws/job/{id}") {
            val id = call.parameters["id"].toString()

            val progressFlow = progressService.listenByJobId(id)
            if(progressFlow == null) {
                cancel("Job is not found")
                return@webSocket
            }

            progressFlow.collect {
                try {
                    send(ExecutionProgressFormatter.format(it))
                    flush()
                } catch (_: ChannelWriteException) {
                    cancel()
                }
            }
            close()
        }
    }
}
