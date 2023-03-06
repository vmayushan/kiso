package kiso.worker

import kiso.core.models.Language
import kiso.worker.config.RunnerConfig
import kiso.worker.docker.container.DockerContainerRuntimeLimits
import kiso.worker.config.DockerImageConfig
import kotlin.time.Duration.Companion.hours

private const val MB = 1024 * 1024L
private val defaultRuntimeLimits = DockerContainerRuntimeLimits(
    cpuCount = 1,
    memoryLimitBytes = 100 * MB,
    workdirSizeLimitBytes = 50 * MB,
    tmpSizeLimitBytes = 50 * MB,
    stdoutLimitBytes = 2 * MB
)

val Language.imageConfig
    get() = when (this) {
        Language.CSharp -> DockerImageConfig(
            imageName = "dotnet-sandbox",
            dockerFile = "/docker/dotnet6.Dockerfile",
            idleContainersCount = 1,
            runtimeLimits = defaultRuntimeLimits
        )

        Language.Python -> DockerImageConfig(
            imageName = "python-sandbox",
            dockerFile = "/docker/python3.Dockerfile",
            idleContainersCount = 1,
            runtimeLimits = defaultRuntimeLimits
        )
    }

val Language.runnerConfig
    get() = when (this) {
        Language.CSharp -> RunnerConfig(
            sourceFile = "Program.cs",
            compileCmd = "dotnet build --no-restore -v=q --nologo",
            executeCmd = "dotnet ./bin/Debug/net6.0/app.dll",
            installPackageCmd = { "dotnet add package ${it.name}" },
            executionTimeout = 1.hours
        )

        Language.Python -> RunnerConfig(
            sourceFile = "program.py",
            compileCmd = null,
            executeCmd = "pypy ./program.py",
            installPackageCmd = { "pip install ${it.name}" },
            executionTimeout = 1.hours
        )
    }