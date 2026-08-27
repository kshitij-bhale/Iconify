package com.drdisagree.iconify.core.ui.components.others

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.drdisagree.iconify.R
import com.drdisagree.iconify.core.ui.components.dialogs.ErrorDialog
import com.drdisagree.iconify.core.utils.overlay.compilers.CompilerErrorStore
import com.drdisagree.iconify.core.utils.overlay.compilers.CompilerFailure
import com.drdisagree.iconify.data.events.ToastUiEvent
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun ToastAppliedEvent(uiEvent: SharedFlow<ToastUiEvent>) {
    val context = LocalContext.current

    // Holds a compiler failure captured during the last operation so it can be
    // shown in a detailed dialog instead of the generic "error" toast.
    var failure by remember { mutableStateOf<CompilerFailure?>(null) }

    failure?.let { compilerFailure ->
        ErrorDialog(
            title = stringResource(R.string.toast_error),
            description = compilerFailure.detailText(),
            onDismiss = { failure = null }
        )
    }

    LaunchedEffect(Unit) {
        uiEvent.collect { event ->
            when (event) {
                ToastUiEvent.Applied -> {
                    CompilerErrorStore.clear()
                    Toast.makeText(
                        context,
                        R.string.toast_applied,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                ToastUiEvent.Disabled -> {
                    CompilerErrorStore.clear()
                    Toast.makeText(
                        context,
                        R.string.toast_disabled,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                ToastUiEvent.Error -> {
                    // Upgrade to a detailed dialog when the compilers recorded
                    // what actually went wrong; otherwise fall back to a toast.
                    val recorded = CompilerErrorStore.consume()
                    if (recorded != null) {
                        failure = recorded
                    } else {
                        Toast.makeText(
                            context,
                            R.string.toast_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
