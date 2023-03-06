package kiso.core.persistence.serialization

import kiso.core.models.ExecutionJob
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

fun serializeJob(job: ExecutionJob): Map<String, String> =
    mapOf("job" to Json.encodeToString(job))


fun deserializeJob(map: Map<String, String>): ExecutionJob =
    Json.decodeFromString(map.getValue("job"))