package kiso.core.models

import kotlinx.serialization.Serializable

@Serializable
data class Package(val name: String, val version: String? = null)
