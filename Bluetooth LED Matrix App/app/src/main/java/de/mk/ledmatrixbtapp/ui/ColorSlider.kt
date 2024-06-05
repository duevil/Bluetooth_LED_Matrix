package de.mk.ledmatrixbtapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import de.mk.ledmatrixbtapp.data.ColorPart
import de.mk.ledmatrixbtapp.data.ViewModel
import de.mk.ledmatrixbtapp.data.color
import de.mk.ledmatrixbtapp.data.value

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ColorSlider(
    vm: ViewModel,
    selectedLeds: Set<Int>,
    color: Color,
    part: ColorPart,
) {
    ListItem(
        leadingContent = { Text(part.name) },
        headlineText = {
            Slider(
                enabled = selectedLeds.isNotEmpty(),
                value = color.value(part),
                onValueChange = {
                    vm.setLedColor(
                        selectedLeds, when (part) {
                            ColorPart.R -> color.copy(red = it)
                            ColorPart.G -> color.copy(green = it)
                            ColorPart.B -> color.copy(blue = it)
                        }
                    )
                },
                onValueChangeFinished = { vm.sendColorValues(selectedLeds) },
                colors = SliderDefaults.colors(
                    activeTrackColor = color.color(part),
                    inactiveTrackColor = color.color(part),
                )
            )
        },
        trailingContent = {
            Text(
                fontFamily = FontFamily.Monospace,
                text = color.value(part).let { "%4d".format((it * 255).toInt()) }
            )
        }
    )
}