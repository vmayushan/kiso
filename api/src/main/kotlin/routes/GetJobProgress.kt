package kiso.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kiso.api.services.ExecutionJobProgressService
import kiso.api.services.ExecutionProgressFormatter
import org.koin.ktor.ext.inject

fun Application.routingGetJobProgress() {
    val progressService by inject<ExecutionJobProgressService>()

    routing {
        get("/job/{id}") {
            val id = call.parameters["id"].toString()
            val progress = progressService
                .getByJobId(id)

            if (progress == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(progress.joinToString("") { ExecutionProgressFormatter.format(it) })
            }
        }
    }
}
