package kiso.worker.docker.pool

import kiso.worker.config.DockerImageConfig
import kiso.worker.docker.container.DockerContainer
import kiso.worker.docker.image.DockerImageBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KLogging
import java.util.*

class DockerContainerPool(private val config: DockerContainerPoolConfig) {
    private val idleContainerPool: MutableMap<DockerImageConfig, Channel<DockerContainer>> = HashMap()
    private val containerCreationQueue: Channel<DockerImageConfig> = Channel(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    companion object : KLogging()

    init {
        logger.info { "Initializing container pool" }

        runBlocking {
            for (image in config.images) {
                idleContainerPool[image] = Channel(image.idleContainersCount)
                repeat(image.idleContainersCount) { containerCreationQueue.send(image) }
            }
        }
    }

    suspend fun buildImages() {
        for (image in config.images) {
            logger.info { "Building image for ${image.imageName}" }
            DockerImageBuilder.buildImage(image.imageName, image.dockerFile)
        }
    }

    suspend fun takeOrCreate(image: DockerImageConfig): DockerContainer {
        val receiveContainerResult = idleContainerPool[image]?.tryReceive()

        if (receiveContainerResult?.isSuccess == true) {
            // start creation of just taken container
            containerCreationQueue.send(image)
            logger.info { "Container ${image.imageName} was taken from the pool" }
            return receiveContainerResult.getOrThrow()
        }

        logger.info { "Container ${image.imageName} was not found in the pool, creating new one" }
        return createContainer(image)
    }

    private suspend fun createContainer(image: DockerImageConfig): DockerContainer {
        val containerId = "${image.imageName}-${UUID.randomUUID()}"
        val container = DockerContainer(containerId)
        container.startContainer(image.imageName, getContainerLabel(), image.runtimeLimits)
        return container
    }

    suspend fun startContainerCreator() = scope.launch {
        for (imageName in containerCreationQueue) {
            val container = createContainer(imageName)
            idleContainerPool.getValue(imageName).send(container)

            logger.info { "Container ${container.containerId} has started and was added to the pool" }
        }
    }

    suspend fun removeStaledContainers() {
        logger.info { "Removing staled containers" }

        DockerContainer
            .listAllByLabel(getContainerLabel())
            .forEach { it.removeContainer() }
    }

    private fun getContainerLabel(): Map<String, String> = mapOf("nodeName" to config.nodeName)
}