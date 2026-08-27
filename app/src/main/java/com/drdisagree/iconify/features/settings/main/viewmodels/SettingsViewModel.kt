package com.drdisagree.iconify.features.settings.main.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.iconify.BuildConfig
import com.drdisagree.iconify.core.preferences.PreferenceController
import com.drdisagree.iconify.core.utils.SystemUtils
import com.drdisagree.iconify.core.utils.SystemUtils.disableBlur
import com.drdisagree.iconify.core.utils.weather.WeatherConfig
import com.drdisagree.iconify.data.common.Resources.MODULE_DIR
import com.drdisagree.iconify.data.keys.SettingsKey
import com.drdisagree.iconify.data.repository.DynamicResourceRepository
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefController: PreferenceController,
    private val dynamicResourceRepository: DynamicResourceRepository
) : ViewModel() {

    private val tag = "SettingsViewModel"

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Emitted once the teardown finishes so the screen can restart the app.
    private val _restartApp = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val restartApp = _restartApp.asSharedFlow()

    /**
     * Resets every customization and restarts SystemUI.
     *
     * Runs on [viewModelScope] (survives recomposition / screen disposal) so it
     * cannot be orphaned the way the old `rememberCoroutineScope` version was.
     * The final restart + state cleanup run under [NonCancellable] so they
     * execute even if the scope is cancelled mid-flight.
     */
    fun disableEverything() {
        if (_isLoading.value) return
        _isLoading.value = true

        // viewModelScope runs on Dispatchers.Main.immediate. PreferenceController
        // is backed by Compose snapshot state (mutableStateMapOf), so its
        // mutations MUST happen on the main thread — doing them off-main caused
        // "Unsupported concurrent change during composition". Only the blocking
        // work (shell / db / file IO) is moved to Dispatchers.IO.
        viewModelScope.launch {
            try {
                // Clear weather configs
                withContext(Dispatchers.IO) { WeatherConfig.clear(context) }

                // Clear shared preferences (main thread — snapshot state)
                prefController.reset()

                withContext(Dispatchers.IO) {
                    // Clear dynamic resource database (awaited, unlike before)
                    dynamicResourceRepository.clearAllResources()

                    disableBlur(false)
                }

                prefController.setInt(
                    SettingsKey.OVERLAY_VERSION_CODE,
                    BuildConfig.OVERLAY_VERSION_CODE
                )
                prefController.setBoolean(SettingsKey.ON_HOME_PAGE, true)
                prefController.setBoolean(SettingsKey.FIRST_INSTALL, false)

                // Disable every Iconify overlay (blocking, so the SystemUI
                // restart below happens only after they are actually disabled)
                withContext(Dispatchers.IO) {
                    Shell.cmd(
                        $$"> $$MODULE_DIR/system.prop; > $$MODULE_DIR/post-exec.sh; for ol in $(cmd overlay list | grep -E '.x.*IconifyComponent' | sed -E 's/^.x..//'); do cmd overlay disable $ol; done"
                    ).exec()
                }
            } catch (t: Throwable) {
                Log.e(tag, "disableEverything failed", t)
            } finally {
                // Guaranteed even if the scope is cancelled: drop the loading
                // state and trigger the restarts.
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { SystemUtils.restartSystemUI() }
                    _isLoading.value = false
                    _restartApp.emit(Unit)
                }
            }
        }
    }
}
