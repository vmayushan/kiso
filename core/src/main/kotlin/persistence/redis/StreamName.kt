package kiso.core.persistence.redis

object StreamName {
    const val jobsQueue = "queue"
    const val completedJobs = "completed"
    val jobProgress: (jobId: String) -> String = { "job_$it" }
}