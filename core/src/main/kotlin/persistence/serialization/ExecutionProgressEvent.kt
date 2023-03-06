package kiso.core.persistence.serialization

import kiso.core.models.progress.ExecutionProgressEvent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun serializeEvent(event: ExecutionProgressEvent): Map<String, String> =
    mapOf("body" to Json.encodeToString(event))


fun deserializeEvent(map: Map<String, String>): ExecutionProgressEvent =
    Json.decodeFromString(map.getValue("body"))