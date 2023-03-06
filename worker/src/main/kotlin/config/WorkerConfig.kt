package kiso.worker.config

import java.net.InetAddress

data class WorkerConfig(
    val instanceName: String = InetAddress.getLocalHost().hostName,
    val creationParallelism: Int = 1,
    val executionParallelism: Int = 1
)