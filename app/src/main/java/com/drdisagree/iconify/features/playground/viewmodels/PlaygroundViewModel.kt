package com.drdisagree.iconify.features.playground.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.iconify.core.preferences.PreferenceController
import com.drdisagree.iconify.core.utils.overlay.resource.ResourceEntry
import com.drdisagree.iconify.core.utils.overlay.resource.ResourceManager
import com.drdisagree.iconify.data.common.Const.SYSTEMUI_PACKAGE
import com.drdisagree.iconify.data.events.ToastUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaygroundViewModel @Inject constructor(
    private val prefController: PreferenceController
) : ViewModel() {

    private val keyguardPinAccentBgId = "keyguard_pin_accent_bg"

    private val keyguardPinAccentBgResourceEntries = listOf(
        ResourceEntry(
            SYSTEMUI_PACKAGE,
            "color",
            "pin_bouncer_action_button_bg",
            "@*android:color/system_accent1_100"
        ),
        ResourceEntry(
            SYSTEMUI_PACKAGE,
            "color",
            "pin_bouncer_action_button_bg",
            "@*android:color/system_accent1_600"
        ).apply {
            isNightMode = true
        }
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ToastUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun toggleKeyguardPinAccentBg(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isLoading.value) return@launch

            _isLoading.value = true

            val entries = keyguardPinAccentBgResourceEntries

            val error = if (enable) {
                ResourceManager.buildOverlayWithResource(
                    keyguardPinAccentBgId,
                    *entries.toTypedArray()
                )
            } else {
                ResourceManager.removeResourceFromOverlay(
                    overlayIds = listOf(keyguardPinAccentBgId),
                    packagesToUpdate = entries.map { it.packageName }.distinct()
                )
            }

            _isLoading.value = false

            if (!error) {
                _uiEvent.emit(ToastUiEvent.Applied)
            } else {
                _uiEvent.emit(ToastUiEvent.Error)
            }
        }
    }
}
