package kiso.worker.runner

import kiso.core.models.ExecutionJob
import kiso.core.models.ExecutionStep
import kiso.worker.runnerConfig
import kiso.worker.config.RunnerConfig
import kiso.worker.docker.container.DockerContainer

data class RunnerContext(
    val job: ExecutionJob,
    var step: ExecutionStep,
    val container: DockerContainer,
) {
    val config: RunnerConfig
        get() = job.language.runnerConfig
}
