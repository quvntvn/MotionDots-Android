package com.quvntvn.motiondots.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quvntvn.motiondots.R
import androidx.compose.ui.res.stringResource

@Composable
fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    var sliderValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (value != sliderValue) {
            sliderValue = value
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.slider_value_format, title, valueLabel))
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}
