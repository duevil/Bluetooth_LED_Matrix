package de.mk.ledmatrixbtapp.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import de.mk.ledmatrixbtapp.data.ViewModel
import de.mk.ledmatrixbtapp.data.hexString
import de.mk.ledmatrixbtapp.data.rgb

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ColorInputDialog(
    color: Color,
    textInput: String,
    vm: ViewModel,
    selectedLeds: Set<Int>,
) {
    var textInput1 = textInput
    TextField(
        value = color.hexString,
        onValueChange = { s ->
            textInput1 = s
            s.toIntOrNull(radix = 16)?.let { vm.setLedColor(selectedLeds, it.rgb) }
        },
        isError = textInput1.toIntOrNull(radix = 16) == null,
        visualTransformation = {
            TransformedText(
                text = AnnotatedString("#" + it.text),
                offsetMapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int) = offset + 1
                    override fun transformedToOriginal(offset: Int) = offset - 1
                })
        },
        keyboardActions = KeyboardActions(
            onSend = { vm.sendColorValues(selectedLeds) },
            onDone = { vm.sendColorValues(selectedLeds) },
            onGo = { vm.sendColorValues(selectedLeds) }
        )
    )
}