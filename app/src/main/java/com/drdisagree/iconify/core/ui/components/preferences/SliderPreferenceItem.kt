package com.drdisagree.iconify.core.ui.components.preferences

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.github.yohannestz.iconsax_compose.iconsax.Iconsax
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.iconify.R
import com.drdisagree.iconify.core.common.LocalNavController
import com.drdisagree.iconify.core.preferences.PrefParam
import com.drdisagree.iconify.core.preferences.PrefValue
import com.drdisagree.iconify.core.preferences.PreferenceController
import com.drdisagree.iconify.core.preferences.PreferenceDefinition
import com.drdisagree.iconify.core.preferences.PreferenceType
import com.drdisagree.iconify.core.preferences.resolveOrNull
import com.drdisagree.iconify.core.preferences.toValueOrNull
import com.drdisagree.iconify.core.ui.components.others.withHaptic
import com.drdisagree.iconify.helpers.replaceAll
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SliderPreferenceItem(
    prefDefinition: PreferenceDefinition,
    prefController: PreferenceController,
    shape: RoundedCornerShape,
    isEnabled: Boolean,
    type: PreferenceType.Slider,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val navController = LocalNavController.current

    val defaultValue = (prefDefinition.defaultValue as PrefValue.FloatValue).v
    val persistedValue by prefController.observe(prefDefinition.key, defaultValue)
    var sliderValue by remember { mutableFloatStateOf(persistedValue) }
    var previousLabel by remember { mutableStateOf<String?>(null) }

    val defaultEpsilon = if (type.steps > 0) {
        (type.max - type.min) / (type.steps + 1) / 2f
    } else {
        0.0001f
    }

    fun labelOf(value: Float) = type.valueLabel?.invoke(value)
        ?: value.roundToInt().toString()

    // Treat a value as default when it's within epsilon OR renders the same label as
    // the default (a continuous slider snaps many raw floats to one rounded "Ndp" label).
    fun isDefault(value: Float) =
        abs(value - defaultValue) <= defaultEpsilon || labelOf(value) == labelOf(defaultValue)

    val isAtDefault = isDefault(sliderValue)

    LaunchedEffect(persistedValue) {
        if (sliderValue != persistedValue) {
            sliderValue = persistedValue
        }
    }

    val originalValueLabel = labelOf(sliderValue)
    val valueLabel = if (type.showDefaultIndicator && isAtDefault) {
        if (type.hideDefaultValue) {
            stringResource(R.string.opt_default).replaceAll("(" to "", ")" to "")
        } else {
            "%s %s".format(originalValueLabel, stringResource(R.string.opt_default))
        }
    } else {
        originalValueLabel
    }

    val param = PrefParam(
        prefDefinition.key,
        prefDefinition.defaultValue.toValueOrNull(),
        sliderValue,
        context,
        activity,
        prefController,
        navController
    )

    val summary = prefDefinition.summary?.invoke(param).resolveOrNull()

    val onValueChangeWithHaptic = withHaptic { /* no-op */ }

    fun updateUiValue(newValue: Float) {
        if (!isEnabled || sliderValue == newValue) return

        val newLabel = labelOf(newValue)

        if (newLabel != previousLabel) {
            onValueChangeWithHaptic()
            previousLabel = newLabel
        }

        sliderValue = newValue
    }

    fun persistValue(value: Float) {
        // Snap to exact default when it reads as default so the stored value stays clean
        // and matches what the "Default" label and reset button report.
        val snapped = if (isDefault(value)) defaultValue else value
        prefController.setFloat(prefDefinition.key, snapped)
    }

    // One discrete step; matches the slider's tick spacing, or 1 for a continuous slider.
    val stepIncrement = if (type.steps > 0) {
        (type.max - type.min) / (type.steps + 1)
    } else {
        1f
    }

    fun stepBy(delta: Float) {
        val newValue = (sliderValue + delta).coerceIn(type.min, type.max)
        updateUiValue(newValue)
        persistValue(newValue)
    }

    PreferenceContainer(
        shape = shape,
        isEnabled = isEnabled,
        modifier = modifier,
        minLine = if (summary.isNullOrEmpty()) 2 else 3,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LeadingIcon(prefDefinition.icon, isEnabled)
                TitleSummaryBlock(prefDefinition.title, summary, isEnabled)
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (type.showStepButtons) {
                    IconButton(
                        enabled = isEnabled && sliderValue > type.min,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        onClick = { stepBy(-stepIncrement) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Iconsax.Outline.Minus,
                            contentDescription = stringResource(R.string.btn_decrease),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Slider(
                    value = sliderValue,
                    onValueChange = { newValue ->
                        updateUiValue(newValue)

                        if (type.applyImmediately) {
                            persistValue(newValue)
                        }
                    },
                    onValueChangeFinished = {
                        if (!type.applyImmediately) {
                            persistValue(sliderValue)
                        }
                    },
                    valueRange = type.min..type.max,
                    steps = type.steps,
                    enabled = isEnabled,
                    modifier = Modifier
                        .wrapContentHeight()
                        .weight(1f)
                )
                if (type.showStepButtons) {
                    IconButton(
                        enabled = isEnabled && sliderValue < type.max,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(),
                        onClick = { stepBy(stepIncrement) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Iconsax.Outline.Add,
                            contentDescription = stringResource(R.string.btn_increase),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (type.showResetButton) {
                    IconButton(
                        enabled = isEnabled && !isAtDefault,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        onClick = {
                            updateUiValue(defaultValue)
                            persistValue(defaultValue)
                        }
                    ) {
                        Icon(
                            imageVector = Iconsax.Outline.RefreshTwo,
                            contentDescription = stringResource(R.string.btn_reset),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}