package unit.runner

import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kiso.core.models.ExecutionJob
import kiso.core.models.ExecutionStep
import kiso.core.models.Language
import kiso.core.models.Package
import kiso.core.models.progress.*
import kiso.worker.docker.container.DockerContainer
import kiso.worker.docker.container.DockerExecCmdOut
import kiso.worker.queues.JobProgressHandler
import kiso.worker.runner.Runner
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class RunnerTest {
    @RelaxedMockK
    lateinit var progressHandler: JobProgressHandler

    @RelaxedMockK
    lateinit var container: DockerContainer

    private var jobId = "test_job"

    @BeforeEach
    fun setUp() {
        coEvery { container.execCommand(any()) } returns flowOf(DockerExecCmdOut.Exit(0))
    }

    @Test
    fun `runner executes and reports all steps`() = runBlocking {
        // Arrange
        val runner = Runner(progressHandler)

        // Act
        val job = createExecutionJob()
        runner.run(job, container)

        // Assert
        verifyExecutionStarted()
        verifyStepStarted(ExecutionStep.Setup)
        verifyStepStarted(ExecutionStep.Compile)
        verifyStepStarted(ExecutionStep.Execute)
        verifyExecutionCompleted()
    }

    @Test
    fun `runner reported build failure and did not start execution`(): Unit = runBlocking {
        // Arrange
        coEvery { container.execCommand(match { it.contains("build") }) } returns flowOf(
            DockerExecCmdOut.Log("build log", LogType.Stdout),
            DockerExecCmdOut.Log("build error", LogType.Stderr),
            DockerExecCmdOut.Exit(-1)
        )

        // Act
        val runner = Runner(progressHandler)
        val job = createExecutionJob()
        runner.run(job, container)

        // Assert
        verifyExecutionStarted()
        verifyStepStarted(ExecutionStep.Setup)
        verifyStepStarted(ExecutionStep.Compile)
        verifyLog(ExecutionStep.Compile, LogType.Stdout, "build log")
        verifyLog(ExecutionStep.Compile, LogType.Stderr, "build error")
        verifyStepNotStarted(ExecutionStep.Execute)
        verifyExecutionCompleted()
    }

    @Test
    fun `network is disabled after package installation`(): Unit = runBlocking {
        // Arrange
        val runner = Runner(progressHandler)

        // Act
        val job = createExecutionJob(packages = listOf(Package("Newtonsoft.Json", "10")))
        runner.run(job, container)

        // Assert
        coVerify(exactly = 1) {
            container.execCommand(match { it.contains("Newtonsoft.Json") })
            container.disableNetwork()
        }
    }

    private fun verifyExecutionStarted() =
        coVerify(exactly = 1) { progressHandler.report(jobId, ExecutionStartedEvent) }

    private fun verifyExecutionCompleted() =
        coVerify(exactly = 1) { progressHandler.report(jobId, ExecutionCompletedEvent) }

    private fun verifyStepStarted(step: ExecutionStep) =
        coVerify(exactly = 1) { progressHandler.report(jobId, ExecutionStepStartedEvent(step)) }

    @Suppress("SameParameterValue")
    private fun verifyStepNotStarted(step: ExecutionStep) =
        coVerify(exactly = 0) { progressHandler.report(jobId, ExecutionStepStartedEvent(step)) }

    @Suppress("SameParameterValue")
    private fun verifyLog(step: ExecutionStep, logType: LogType, message: String) =
        coVerify(exactly = 1) { progressHandler.report(jobId, ExecutionLogEvent(step, logType, message)) }

    private fun createExecutionJob(packages: List<Package> = emptyList()): ExecutionJob = ExecutionJob(
        jobId, Language.CSharp, "source code", packages
    )
}