package kiso.api.models.validation

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import kiso.api.models.request.CreateExecutionJob
import kiso.core.models.Language

fun Application.configureExecutionJobValidation() {
    install(RequestValidation) {
        validate<CreateExecutionJob> { request ->
            return@validate validateRequest(request)
        }
    }
}

private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 megabytes
private fun validateRequest(request: CreateExecutionJob): ValidationResult {
    if (Language.valueOfOrNull(request.language) == null) {
        return ValidationResult.Invalid(
            "Invalid language, supported languages: ${Language.values().joinToString()}"
        )
    }

    if (request.sourceCode.length > MAX_FILE_SIZE) {
        return ValidationResult.Invalid("Input file is too big")
    }

    fun isValidNameOrVersion(value: String): Boolean =
        value.all { it.isLetterOrDigit() || arrayOf('.', '-', '_').contains(it) }

    if (request.packages == null) {
        return ValidationResult.Valid
    }
    for (pkg in request.packages) {
        if (!isValidNameOrVersion(pkg.name)) {
            return ValidationResult.Invalid("Invalid package name")
        }

        if (pkg.version != null && !isValidNameOrVersion(pkg.version!!)) {
            return ValidationResult.Invalid("Invalid package version")
        }
    }

    return ValidationResult.Valid
}