package kiso.worker.docker.container

data class DockerContainerRuntimeLimits(
    val cpuCount: Long,
    val memoryLimitBytes: Long,
    val workdirSizeLimitBytes: Long,
    val tmpSizeLimitBytes: Long
)

