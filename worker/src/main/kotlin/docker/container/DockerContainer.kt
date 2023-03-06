package kiso.worker.docker.container

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.*
import kiso.worker.docker.image.DockerImageConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import mu.KLogging
import java.util.*

class DockerContainer(
    val containerId: String
) {
    suspend fun startContainer(
        imageName: String,
        labels: Map<String, String>,
        limits: DockerContainerRuntimeLimits
    ) {
        logger.info { "Starting container $containerId" }

        withContext(Dispatchers.IO) {
            DockerClient
                .createContainerCmd(imageName)
                .withName(containerId)
                .withLabels(labels)
                .withHostConfig(buildHostConfig(limits))
                .exec()
        }

        withContext(Dispatchers.IO) {
            DockerClient
                .startContainerCmd(containerId)
                .exec()
        }
    }

    private fun buildHostConfig(limitsConfig: DockerContainerRuntimeLimits): HostConfig =
        HostConfig
            .newHostConfig()
            .withCpuCount(limitsConfig.cpuCount)
            .withReadonlyRootfs(true)
            .withAutoRemove(true)
            .withSecurityOpts(listOf("no-new-privileges"))
            .withMemory(limitsConfig.memoryLimitBytes)
            .withMounts(
                listOf(
                    Mount()
                        .withTarget("/${DockerImageConstants.Workdir}")
                        .withTmpfsOptions(TmpfsOptions().withSizeBytes(limitsConfig.workdirSizeLimitBytes)),
                    Mount()
                        .withTarget("/tmp")
                        .withTmpfsOptions(TmpfsOptions().withSizeBytes(limitsConfig.tmpSizeLimitBytes))
                )
            )


    suspend fun removeContainer() {
        logger.info { "Removing container $containerId" }

        withContext(Dispatchers.IO) {
            DockerClient
                .removeContainerCmd(containerId)
                .withRemoveVolumes(true)
                .withForce(true)
                .exec()
        }
    }

    suspend fun disableNetwork() {
        logger.info { "Disabling network of container $containerId" }

        val info = withContext(Dispatchers.IO) {
            DockerClient
                .inspectContainerCmd(containerId)
                .exec()
        }

        for (network in info.networkSettings.networks) {
            withContext(Dispatchers.IO) {
                DockerClient
                    .disconnectFromNetworkCmd()
                    .withContainerId(containerId)
                    .withNetworkId(network.key)
                    .exec()
            }
        }
    }

    suspend fun execCommand(cmd: Array<String>): Flow<DockerExecCmdOut> {
        logger.info { "Executing ${cmd[0]} command in $containerId" }

        val execCreate = withContext(Dispatchers.IO) {
            DockerClient
                .execCreateCmd(containerId)
                .withCmd(*cmd)
                .withUser(DockerImageConstants.User)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec()
        }

        return callbackFlow {
            DockerClient
                .execStartCmd(execCreate.id)
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(value: Frame) {
                        logger.debug { value }
                        trySendBlocking(value.asLog())
                    }

                    override fun onError(error: Throwable) {
                        logger.error { error }
                        cancel(CancellationException("Docker Exec Error", error))
                    }

                    override fun onComplete() {
                        val exitCode = DockerClient
                            .inspectExecCmd(execCreate.id)
                            .exec()
                            .exitCodeLong
                        trySendBlocking(DockerExecCmdOut.Exit(exitCode))
                        channel.close()
                    }
                })

            awaitClose()
        }
    }

    suspend fun createFileInContainer(content: String, fileName: String): Long? {
        logger.info { "Creating file $fileName in container $containerId workdir" }

        val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray())
        val path = "/${DockerImageConstants.Workdir}/${fileName}"

        val cmd = arrayOf("sh", "-c", "echo '${encodedContent}' | base64 -d> $path")
        val exit = execCommand(cmd)
            .filter { it is DockerExecCmdOut.Exit }
            .map { it as DockerExecCmdOut.Exit }
            .firstOrNull()
        return exit?.exitCode
    }

    companion object : KLogging() {
        suspend fun listAllByLabel(labels: Map<String, String>): List<DockerContainer> =
            withContext(Dispatchers.IO) {
                DockerClient
                    .listContainersCmd()
                    .withLabelFilter(labels)
                    .exec()
                    .map { DockerContainer(containerId = it.id) }
            }
    }
}