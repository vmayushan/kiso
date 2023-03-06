package unit.docker

import io.mockk.*
import io.mockk.junit5.MockKExtension
import kiso.worker.docker.container.DockerContainer
import kiso.worker.docker.pool.DockerContainerPool
import kiso.worker.docker.image.DockerImageBuilder
import kiso.worker.config.DockerImageConfig
import kiso.worker.docker.pool.DockerContainerPoolConfig
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class DockerContainerPoolTest {
    @BeforeEach
    fun setUp() {
        mockkObject(DockerImageBuilder)
        coEvery { DockerImageBuilder.buildImage(any(), any()) } returns Unit

        mockkConstructor(DockerContainer::class)
        coEvery { anyConstructed<DockerContainer>().startContainer() } returns Unit
    }

    @Test
    fun `container pool starts idle containers`(): Unit = runBlocking {
        // Arrange
        val image1 = createImageConfig("image1", idleContainersCount = 3)
        val image2 = createImageConfig("image2", idleContainersCount = 2)
        val config = DockerContainerPoolConfig(nodeName = "test", images = listOf(image1, image2))

        // Act
        val containerPool = DockerContainerPool(config)
        val job = containerPool.startContainerCreator()

        // Assert
        verifyContainersStarted(count = image1.idleContainersCount + image2.idleContainersCount)
        job.cancelAndJoin()
    }

    @Test
    fun `container pool recreates taken from the pool containers`(): Unit = runBlocking {
        // Arrange
        val image = createImageConfig("image", idleContainersCount = 3)
        val config = DockerContainerPoolConfig(nodeName = "test", images = listOf(image))
        val containerPool = DockerContainerPool(config)
        val job = containerPool.startContainerCreator()

        verifyContainersStarted(count = 3)

        // Act
        repeat(2) { containerPool.takeOrCreate(image) }

        // Assert
        verifyContainersStarted(count = 5)

        job.cancelAndJoin()
    }

    @Test
    fun `container pool starts new containers if no idle available`(): Unit = runBlocking {
        // Arrange
        val image = createImageConfig("image", idleContainersCount = 0)
        val config = DockerContainerPoolConfig(nodeName = "test", images = listOf(image))
        val containerPool = DockerContainerPool(config)

        val job = containerPool.startContainerCreator()
        verifyContainersStarted(count = 0)

        // Act
        repeat(2) { containerPool.takeOrCreate(image) }

        // Assert
        verifyContainersStarted(count = 2)
        job.cancelAndJoin()
    }

    private fun verifyContainersStarted(count: Int) =
        coVerify(timeout = 3000, exactly = count) {
            anyConstructed<DockerContainer>().startContainer()
        }

    private fun createImageConfig(imageName: String, idleContainersCount: Int) =
        DockerImageConfig(imageName, "dockerFile", idleContainersCount, mockk(relaxed = true))
}