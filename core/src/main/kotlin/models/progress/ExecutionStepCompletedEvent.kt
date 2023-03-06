package kiso.core.models.progress

import kiso.core.models.ExecutionStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("StepCompleted")
data class ExecutionStepCompletedEvent(
    val step: ExecutionStep,
    val success: Boolean,
    val duration: Long,
    val errorMessage: String?
) : ExecutionProgressEvent