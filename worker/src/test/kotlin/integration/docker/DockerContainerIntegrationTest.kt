package integration.docker

import kiso.worker.docker.container.DockerContainer
import kiso.worker.docker.container.DockerContainerRuntimeLimits
import kiso.worker.docker.container.DockerExecCmdOut
import kiso.worker.docker.image.DockerImageBuilder
import kiso.worker.docker.image.DockerImageConstants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlin.test.*

class DockerContainerIntegrationTest {
    companion object {
        private const val imageName = "test-image"
        private const val MB = 1024 * 1024L
        private val runtimeLimits = DockerContainerRuntimeLimits(
            cpuCount = 1,
            memoryLimitBytes = 50 * MB,
            workdirSizeLimitBytes = 30 * MB,
            tmpSizeLimitBytes = 30 * MB,
            stdoutLimitBytes = 2 * MB
        )

        @BeforeAll
        @JvmStatic
        fun buildImage() = runBlocking {
            DockerImageBuilder.buildImage(imageName, "/integration/test-image.Dockerfile")
        }
    }

    private val container = DockerContainer(
        UUID.randomUUID().toString(),
        imageName,
        emptyMap(),
        runtimeLimits
    )

    @BeforeEach
    fun setUp() = runBlocking {
        container.startContainer()
    }

    @AfterEach
    fun tearDown() = runBlocking {
        container.removeContainer()
    }

    @Test
    fun `create file in container then read it`(): Unit = runBlocking {
        val content = """println("Hello World!")""""
        container.createFileInContainer(content, fileName = "mouse")

        val readFileCmd = container
            .execCommand(arrayOf("cat", "mouse"))
            .filter { it is DockerExecCmdOut.Log }
            .firstOrNull() as DockerExecCmdOut.Log

        assertNotNull(readFileCmd)
        assertContains(readFileCmd.message, content)
    }

    @Test
    fun `network is unreachable after disabling`(): Unit = runBlocking {
        val fileName = "connectivity.sh"
        val script = """
            if nc -zw1 google.com 443; then
              echo "connectivity"
            fi
        """.trimIndent()

        container.createFileInContainer(script, fileName)

        suspend fun hasNetwork(): Boolean {
            return container
                .execCommand(arrayOf("sh", fileName))
                .firstOrNull { it is DockerExecCmdOut.Log && it.message.contains("connectivity") } != null
        }

        assertTrue { hasNetwork() }
        container.disableNetwork()
        assertFalse { hasNetwork() }
    }

    @Test
    fun `big file creation is rejected`(): Unit = runBlocking {
        assertTrue {
            val diskStressCmd = "stress --hdd 1 --hdd-bytes 1G"
            container
                .execCommand(diskStressCmd.split(' ').toTypedArray())
                .filterIsInstance<DockerExecCmdOut.Log>()
                .firstOrNull { it.message.contains("No space left on device") } != null
        }
    }

    @Test
    fun `exceeding memory limit is rejected`(): Unit = runBlocking {
        val memoryStressCmd = "stress --vm 1 --vm-bytes 1G --vm-keep -t 1"
        val execCmdOut = container
            .execCommand(memoryStressCmd.split(' ').toTypedArray())
            .filterIsInstance<DockerExecCmdOut.Exit>()
            .firstOrNull()

        assertNotEquals(0, execCmdOut?.exitCode)
    }

    @Test
    fun `containers runs as non root user`(): Unit = runBlocking {
        val execCmdOut = container
            .execCommand(arrayOf("whoami"))
            .filterIsInstance<DockerExecCmdOut.Log>()
            .firstOrNull()

        assertNotNull(execCmdOut?.message)
        assertContains(execCmdOut!!.message, DockerImageConstants.User)
    }

    @Test
    fun `exceeding stdout limit is rejected`(): Unit = runBlocking {
        val fileName = "stdout-test.sh"
        val script = """while true; do echo "Infinitely repeated log line"; done"""
        container.createFileInContainer(script, fileName)

        assertThrows<CancellationException> {
            container
                .execCommand(arrayOf("sh", fileName))
                .collect()
        }
    }
}