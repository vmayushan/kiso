package kiso.core.models.progress

import kiso.core.models.ExecutionStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("StepStarted")
data class ExecutionStepStartedEvent(
    val step: ExecutionStep
) : ExecutionProgressEvent