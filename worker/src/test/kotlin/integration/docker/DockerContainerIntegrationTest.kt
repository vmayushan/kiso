package integration.docker

import kiso.worker.docker.container.DockerContainer
import kiso.worker.docker.container.DockerContainerRuntimeLimits
import kiso.worker.docker.container.DockerExecCmdOut
import kiso.worker.docker.image.DockerImageBuilder
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*

class DockerContainerIntegrationTest {
    companion object {
        private const val imageName = "test-image"
        private val runtimeLimits = DockerContainerRuntimeLimits(
            cpuCount = 1,
            memoryLimitBytes = 50 * 1024 * 1024,
            workdirSizeLimitBytes = 30 * 1024 * 1024,
            tmpSizeLimitBytes = 30 * 1024 * 1024
        )

        @BeforeAll
        @JvmStatic
        fun buildImage() = runBlocking {
            DockerImageBuilder.buildImage(imageName, "/integration/test-image.Dockerfile")
        }
    }

    private val container = DockerContainer(UUID.randomUUID().toString())

    @BeforeEach
    fun setUp() = runBlocking {
        container.startContainer(imageName, emptyMap(), runtimeLimits)
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
                .firstOrNull { it is DockerExecCmdOut.Log && it.message.contains("No space left on device") } != null
        }
    }

    @Test
    fun `exceeding memory limit is rejected`(): Unit = runBlocking {
        val memoryStressCmd = "stress --vm 1 --vm-bytes 1G --vm-keep -t 1"
        val execCmdOut = container
            .execCommand(memoryStressCmd.split(' ').toTypedArray())
            .firstOrNull { it is DockerExecCmdOut.Exit } as DockerExecCmdOut.Exit

        assertNotEquals(0, execCmdOut.exitCode)
    }

    @Test
    fun `containers runs as non root user`(): Unit = runBlocking {
        val execCmdOut = container
            .execCommand(arrayOf("whoami"))
            .firstOrNull { it is DockerExecCmdOut.Log } as DockerExecCmdOut.Log

        assertNotEquals("root", execCmdOut.message)
    }
}