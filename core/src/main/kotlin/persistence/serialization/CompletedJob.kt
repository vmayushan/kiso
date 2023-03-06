package kiso.core.persistence.serialization
fun serializeCompletedJob(jobId: String): Map<String, String> =
    mapOf("job_id" to jobId)

fun deserializeCompletedJob(map: Map<String, String>): String =
    map.getValue("job_id")