package kiso.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kiso.api.models.request.CreateExecutionJob
import kiso.api.models.response.ExecutionJobCreated
import kiso.core.models.ExecutionJob
import kiso.core.models.Language
import kiso.core.persistence.mongo.ExecutionJobDao
import kiso.core.persistence.redis.RedisStreamClient
import kiso.core.persistence.redis.StreamName
import kiso.core.persistence.serialization.serializeJob
import org.koin.ktor.ext.inject
import java.util.*

fun Application.routingPostExecutionJob() {
    val redisStreamClient by inject<RedisStreamClient>()
    val executionJobDao by inject<ExecutionJobDao>()

    routing {
        post("/job") {
            val req = call.receive<CreateExecutionJob>()
            val jobId = UUID.randomUUID().toString()
            val executionJob = ExecutionJob(
                jobId,
                Language.valueOfOrNull(req.language)!!,
                req.sourceCode,
                req.packages ?: emptyList()
            )
            executionJobDao.insertJob(jobId = jobId)
            redisStreamClient.add(StreamName.jobsQueue, serializeJob(executionJob))
            val response = ExecutionJobCreated(executionJob.id)
            call.respond(HttpStatusCode.Created, response)
        }
    }
}