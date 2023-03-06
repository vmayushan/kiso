package kiso.worker.config

import kiso.worker.docker.container.DockerContainerRuntimeLimits

data class DockerImageConfig(
    val imageName: String,
    val dockerFile: String,
    val idleContainersCount: Int,
    val runtimeLimits: DockerContainerRuntimeLimits
)