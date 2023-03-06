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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

class DockerContainer(
    val containerId: String,
    private val imageName: String,
    private val labels: Map<String, String>,
    private val limits: DockerContainerRuntimeLimits
) {
    private lateinit var inputDirectory: Path
    suspend fun startContainer() {
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

    private fun buildHostConfig(limitsConfig: DockerContainerRuntimeLimits): HostConfig {
        inputDirectory = Files.createTempDirectory("temp")
        inputDirectory.toFile().deleteOnExit()
        return HostConfig
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
                        .withTmpfsOptions(TmpfsOptions().withSizeBytes(limitsConfig.tmpSizeLimitBytes)),
                    Mount()
                        .withTarget("/${DockerImageConstants.Inputdir}")
                        .withReadOnly(true)
                        .withType(MountType.BIND)
                        .withSource(inputDirectory.pathString)
                )
            )
    }


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
        var stdoutSizeBytes = 0
        return callbackFlow {
            DockerClient
                .execStartCmd(execCreate.id)
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(value: Frame) {
                        logger.debug { value }

                        stdoutSizeBytes += value.payload.size
                        if(stdoutSizeBytes > limits.stdoutLimitBytes) {
                            cancel(CancellationException("Too much output"))
                        } else {
                            trySendBlocking(value.asLog())
                        }
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

        val filePath = Paths.get(inputDirectory.toString(), fileName)
        withContext(Dispatchers.IO) { Files.write(filePath, content.toByteArray()) }

        val source = "/${DockerImageConstants.Inputdir}/${fileName}"
        val destination = "/${DockerImageConstants.Workdir}/${fileName}"

        val cmd = arrayOf("cp", source, destination)
        val exit = execCommand(cmd)
            .filterIsInstance<DockerExecCmdOut.Exit>()
            .firstOrNull()
        return exit?.exitCode
    }

    companion object : KLogging() {
        suspend fun removeAllByLabel(labels: Map<String, String>) {
            val containers = withContext(Dispatchers.IO) {
                DockerClient
                    .listContainersCmd()
                    .withLabelFilter(labels)
                    .exec()
            }

            containers.forEach {
                withContext(Dispatchers.IO) {
                    DockerClient
                        .removeContainerCmd(it.id)
                        .withRemoveVolumes(true)
                        .withForce(true)
                        .exec()
                }
            }
        }
    }
}