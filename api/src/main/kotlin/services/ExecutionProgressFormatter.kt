package kiso.api.services

import kiso.core.models.progress.*

object ExecutionProgressFormatter {
    fun format(event: ExecutionProgressEvent): String {
        fun writeln(message: String): String = "$message\n"

        val formatted = when (event) {
            ExecutionStartedEvent -> writeln("Execution started")
            ExecutionCompletedEvent -> writeln("Execution completed")
            is ExecutionStepStartedEvent -> writeln("Step[name=${event.step}] started")
            is ExecutionStepCompletedEvent -> return if (event.success)
                writeln("Step[name=${event.step}] completed in ${event.duration} ms")
            else {
                if(event.errorMessage != null) {
                    writeln("Step[name=${event.step}] failed with '${event.errorMessage}' error message in ${event.duration} ms")
                }
                else {
                    writeln("Step[name=${event.step}] failed in ${event.duration} ms")
                }
            }

            is ExecutionLogEvent -> {
                val isError = if (event.type == LogType.Stderr) "STDERR:" else ""
                return "${isError}${event.log}"
            }
        }

        return formatted
    }
}