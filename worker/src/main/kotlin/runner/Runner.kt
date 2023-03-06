package kiso.worker.runner

import kiso.core.models.ExecutionJob
import kiso.core.models.ExecutionStep
import kiso.core.models.progress.*
import kiso.worker.docker.container.DockerContainer
import kiso.worker.docker.container.DockerExecCmdOut
import kiso.worker.queues.JobProgressHandler
import kotlinx.coroutines.withTimeout
import mu.KLogging

import kotlin.system.measureTimeMillis

class Runner(
    private val progressHandler: JobProgressHandler
) {
    private val executionSteps = mapOf(
        ExecutionStep.Setup to ::setup,
        ExecutionStep.Compile to ::compile,
        ExecutionStep.Execute to ::execute
    )

    companion object : KLogging()

    suspend fun run(job: ExecutionJob, container: DockerContainer) {
        val ctx = RunnerContext(
            job,
            ExecutionStep.Setup,
            container
        )

        logger.info { "Execution of job ${job.id} started in container ${container.containerId}" }

        report(ctx, ExecutionStartedEvent)
        try {
            for ((step, method) in executionSteps) {
                ctx.step = step
                if (!runStep(ctx, method)) {
                    break
                }
            }
        } finally {
            logger.info { "Execution of job ${job.id} finished" }

            report(ctx, ExecutionCompletedEvent)
        }
    }

    private suspend fun setup(ctx: RunnerContext): Boolean {
        for (pkg in ctx.job.packages) {
            val exitCode = execCommand(
                ctx,
                ctx.config.installPackageCmd(pkg),
            )
            if (exitCode != 0L) {
                report(
                    ctx,
                    ExecutionLogEvent(
                        ctx.step, LogType.Stderr,
                        "Package ${pkg.name} installation did exit with non zero code: $exitCode"
                    )
                )
                return false
            }
        }

        ctx.container.disableNetwork()

        val exitCode = ctx.container
            .createFileInContainer(ctx.job.sourceCode, ctx.config.sourceFile)
        if (exitCode != 0L) {
            report(
                ctx,
                ExecutionLogEvent(
                    ctx.step, LogType.Stderr,
                    "Failed to copy source code to container, exit with non zero code: $exitCode"
                )
            )
            return false
        }

        return true
    }

    private suspend fun compile(ctx: RunnerContext): Boolean {
        if (ctx.config.compileCmd == null) {
            // interpreter, compilation is not needed
            return true
        }

        val exitCode = execCommand(
            ctx,
            ctx.config.compileCmd!!
        )
        if (exitCode != 0L) {
            report(
                ctx,
                ExecutionLogEvent(
                    ctx.step, LogType.Stderr,
                    "Build command did exit with non zero code: $exitCode"
                )
            )
            return false
        }
        return true
    }

    private suspend fun execute(ctx: RunnerContext): Boolean {
        val exitCode = execCommand(
            ctx,
            ctx.config.executeCmd
        )
        if (exitCode != 0L) {
            report(
                ctx,
                ExecutionLogEvent(
                    ctx.step, LogType.Stderr,
                    "Execution did exit with non zero code: $exitCode"
                )
            )
            return false
        }
        return true
    }

    private suspend fun runStep(
        ctx: RunnerContext,
        block: suspend (context: RunnerContext) -> Boolean
    ): Boolean {
        report(ctx, ExecutionStepStartedEvent(ctx.step))
        var success = false
        var duration = 0L
        var errorMessage: String? = null
        try {
            duration = measureTimeMillis {
                try {
                    withTimeout(ctx.config.executionTimeout) {
                        success = block(ctx)
                    }
                } catch (throwable: Throwable) {
                    errorMessage = throwable.localizedMessage
                }
            }
        } finally {
            report(
                ctx, ExecutionStepCompletedEvent(ctx.step, success, duration, errorMessage)
            )
        }

        return success
    }

    private suspend fun execCommand(ctx: RunnerContext, cmd: String): Long? {
        val execCmdFlow = ctx.container.execCommand(
            cmd.split(' ').toTypedArray(),
        )
        var exitCode: Long? = null
        execCmdFlow.collect {
            when (it) {
                is DockerExecCmdOut.Exit -> exitCode = it.exitCode
                is DockerExecCmdOut.Log -> report(ctx, ExecutionLogEvent(ctx.step, it.type, it.message))
            }
        }
        return exitCode
    }

    private suspend fun report(ctx: RunnerContext, event: ExecutionProgressEvent) {
        progressHandler.report(ctx.job.id, event)
    }
}