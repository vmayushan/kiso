package kiso.worker.docker.pool

import kiso.worker.config.DockerImageConfig

data class DockerContainerPoolConfig(
    val nodeName: String,
    val images: List<DockerImageConfig>
)

