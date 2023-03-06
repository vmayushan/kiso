package kiso.worker.docker.container

import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType

import kiso.core.models.progress.LogType

sealed class DockerExecCmdOut {
    data class Exit(val exitCode: Long) : DockerExecCmdOut()
    data class Log(val message: String, val type: LogType): DockerExecCmdOut()
}

private fun StreamType.asLogType() = when (this) {
    StreamType.STDOUT -> LogType.Stdout
    StreamType.STDERR -> LogType.Stderr
    else -> LogType.None
}

internal fun Frame.asLog() = DockerExecCmdOut.Log(payload.toString(Charsets.UTF_8), streamType.asLogType())
