package kiso.core.models.progress

enum class LogType(private val raw: String) {
    Stdout("Stdout"),
    Stderr("Stderr"),
    None("None");

    override fun toString() = raw
}