package kiso.api.models.request

import kiso.core.models.Package
import kotlinx.serialization.Serializable

@Serializable
data class CreateExecutionJob(
    val language: String,
    val sourceCode: String,
    val packages: List<Package>? = null
)