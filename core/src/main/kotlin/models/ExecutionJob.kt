package kiso.core.models

import kotlinx.serialization.Serializable

@Serializable
data class ExecutionJob(
    val id: String,
    val language: Language,
    val sourceCode: String,
    val packages: List<Package>
)
