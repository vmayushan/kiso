package kiso.api.models.validation

import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import kiso.api.models.request.CreateExecutionJob
import kiso.core.models.Language

fun Application.configureExecutionJobValidation() {
    install(RequestValidation) {
        validate<CreateExecutionJob> { request ->
            if (Language.valueOfOrNull(request.language) == null) {
                return@validate ValidationResult.Invalid(
                    "Invalid language, supported languages: ${Language.values().joinToString()}"
                )
            }

            fun isValidNameOrVersion(value: String): Boolean =
                value.all { it.isLetterOrDigit() || arrayOf('.', '-', '_').contains(it) }

            if(request.packages == null) {
                return@validate ValidationResult.Valid
            }
            for (pkg in request.packages) {
                if (!isValidNameOrVersion(pkg.name)) {
                    return@validate ValidationResult.Invalid("Invalid package name")
                }

                if (pkg.version != null && !isValidNameOrVersion(pkg.version!!)) {
                    return@validate ValidationResult.Invalid("Invalid package version")
                }
            }

            return@validate ValidationResult.Valid
        }
    }
}