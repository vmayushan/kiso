package kiso.core.models.progress

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Completed")
object ExecutionCompletedEvent : ExecutionProgressEvent