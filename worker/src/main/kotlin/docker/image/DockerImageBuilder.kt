package kiso.worker.docker.image

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.BuildResponseItem
import kiso.worker.docker.container.DockerClient
import kiso.worker.docker.container.DockerContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object DockerImageBuilder {
    private val buildArgs = mapOf(
        "DOCKER_SANDBOX_USER" to DockerImageConstants.User,
        "DOCKER_SANDBOX_WORKDIR" to DockerImageConstants.Workdir,
        "DOCKER_SANDBOX_INPUTDIR" to DockerImageConstants.Inputdir
    )

    suspend fun buildImage(tag: String, dockerFileName: String) {
        val dockerFile = getResource(dockerFileName)
            ?: throw Throwable("Failed to find Dockerfile $dockerFileName in resources")
        val buildImageCmd = DockerClient
            .buildImageCmd()
            .withDockerfile(dockerFile)
            .withBaseDirectory(dockerFile.parentFile)
            .withTags(setOf(tag))

        buildArgs.forEach {
            buildImageCmd.withBuildArg(it.key, it.value)
        }

        val imageId = suspendCoroutine { continuation ->
            buildImageCmd
                .exec(object : ResultCallback.Adapter<BuildResponseItem>() {
                    override fun onNext(value: BuildResponseItem) {
                        DockerContainer.logger.info { value.rawValues }
                        if (value.isBuildSuccessIndicated)
                            continuation.resume(value.imageId)
                        else if (value.isErrorIndicated)
                            continuation.resumeWithException(Throwable("Failed to build image $tag"))
                    }

                    override fun onError(error: Throwable) {
                        DockerContainer.logger.error { error }
                        continuation.resumeWithException(error)
                    }
                })
        }

        return withContext(Dispatchers.IO) {
            DockerClient.inspectImageCmd(imageId)
                .exec()
        }
    }

    private fun getResource(file: String) = {}::class.java.getResource(file)
        ?.let { Paths.get(it.toURI()) }
        ?.toFile()

}