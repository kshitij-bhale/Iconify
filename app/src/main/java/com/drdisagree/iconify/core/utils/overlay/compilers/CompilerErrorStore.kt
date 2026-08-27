package com.drdisagree.iconify.core.utils.overlay.compilers

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide sink for the most recent [CompilerFailure].
 *
 * The overlay compilers still return a coarse `Boolean` up their call chains
 * (changing every signature would be a large, risky churn on root-flashing
 * code). Instead, the failure *details* are stashed here at the point they are
 * produced, and the UI layer [consume]s them when an error event arrives — so a
 * generic `ToastUiEvent.Error` can be upgraded into a detailed error dialog
 * without touching every ViewModel.
 */
object CompilerErrorStore {

    private val last = AtomicReference<CompilerFailure?>(null)

    /** Record a failure, overwriting any previous unconsumed one. */
    fun record(failure: CompilerFailure) {
        last.set(failure)
    }

    /** Return and clear the last recorded failure, if any. */
    fun consume(): CompilerFailure? = last.getAndSet(null)

    /** Drop any pending failure (e.g. after a successful apply). */
    fun clear() {
        last.set(null)
    }
}
