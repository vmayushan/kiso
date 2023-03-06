package kiso.api

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kiso.api.models.validation.*
import kiso.api.plugins.*
import kiso.api.routes.routingGetJobProgress
import kiso.api.routes.routingPostExecutionJob
import kiso.api.routes.routingWebSocketJobProgress

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::main)
        .start(wait = true)
}

fun Application.main() {
    configureCors()
    configureKoin()
    configureSerialization()
    configureStatusPages()
    configureWebSockets()

    configureExecutionJobValidation()

    routingPostExecutionJob()
    routingGetJobProgress()
    routingWebSocketJobProgress()
}
