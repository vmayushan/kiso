package kiso.core.models.progress

import kiso.core.models.ExecutionStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Log")
data class ExecutionLogEvent(
    val step: ExecutionStep,
    val type: LogType,
    val log: String
) : ExecutionProgressEvent