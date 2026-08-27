package com.drdisagree.iconify.core.utils.overlay.compilers

/**
 * Structured description of a failed overlay-compilation step.
 *
 * Replaces the old "true == error" boolean convention with something that
 * actually carries the captured shell output, so failures can be surfaced to
 * the user instead of being swallowed into a generic toast.
 */
data class CompilerFailure(
    val stage: String,            // "AAPT", "ZipAlign", "Manifest", "Sign", "Flash"
    val target: String,           // overlay / apk name that failed
    val message: String,          // short human-readable summary
    val command: String? = null,  // the shell command that was run, if any
    val output: List<String> = emptyList()  // captured stdout+stderr
) {

    /** Multi-line detail suitable for an error dialog body. */
    fun detailText(): String = buildString {
        append(message)

        val cleanOutput = output.filter { it.isNotBlank() }
        if (cleanOutput.isNotEmpty()) {
            append("\n\n")

            // Errors are at the tail of the output; keep it bounded so the
            // dialog stays readable (the full log is still written to file).
            val shown = cleanOutput.takeLast(MAX_LINES)
            if (cleanOutput.size > MAX_LINES) {
                append("… (${cleanOutput.size - MAX_LINES} earlier lines omitted)\n")
            }
            append(shown.joinToString("\n"))
        }
    }

    private companion object {
        const val MAX_LINES = 40
    }
}
