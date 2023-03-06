package kiso.core.models

enum class Language {
    CSharp,
    Python;

    companion object {
        fun valueOfOrNull(input: String): Language? {
            return values().firstOrNull { it.name.equals(input, true) }
        }
    }
}