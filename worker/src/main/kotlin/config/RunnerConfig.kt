package kiso.worker.config

import kiso.core.models.Package
import kotlin.time.Duration

data class RunnerConfig(
    val sourceFile: String,
    val compileCmd: String?,
    val executeCmd: String,
    val installPackageCmd: (Package) -> String,
    val executionTimeout: Duration
)