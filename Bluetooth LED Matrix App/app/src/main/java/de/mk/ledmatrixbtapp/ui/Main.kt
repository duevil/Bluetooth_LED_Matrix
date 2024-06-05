package de.mk.ledmatrixbtapp.ui

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mk.ledmatrixbtapp.data.ColorPart
import de.mk.ledmatrixbtapp.data.ViewModel
import de.mk.ledmatrixbtapp.data.hexString


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Main(vm: ViewModel = viewModel()) {

    val error by vm.lastError.collectAsState()
    val leds by vm.leds.collectAsState()
    var selectedLeds by remember { mutableStateOf(emptySet<Int>()) }
    val color = leds.find { it.id in selectedLeds }?.color ?: Color.Black
    val snackbarHost = remember { SnackbarHostState() }


    LaunchedEffect(error) {
        if (error != null) {
            snackbarHost.showSnackbar(
                "Error: $error"
            )
        }
    }


    SelectDeviceDialog.compose(vm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LED Matrix Control") },
                actions = {
                    IconButton(
                        onClick = vm::refresh,
                        content = { Icon(Icons.Rounded.Refresh, null) }
                    )
                    IconButton(
                        onClick = SelectDeviceDialog::show,
                        content = { Icon(Icons.Rounded.Settings, null) }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                userScrollEnabled = false,
                modifier = Modifier.padding(6.dp)
            ) {
                items(leds) { led ->
                    val cPrimary = MaterialTheme.colorScheme.primary
                    val v = getSystemService(LocalContext.current, Vibrator::class.java)
                    Box(Modifier
                        .padding(2.dp)
                        .background(led.color, CircleShape)
                        .wrapContentSize()
                        .aspectRatio(1f)
                        .let {
                            if (led.id in selectedLeds) it.border(2.dp, cPrimary, CircleShape)
                            else it
                        }
                        .combinedClickable(
                            onClick = { selectedLeds = setOf(led.id) },
                            onLongClick = {
                                selectedLeds = when (led.id) {
                                    in selectedLeds -> selectedLeds - led.id
                                    else -> selectedLeds + led.id
                                }
                                v!!.vibrate(
                                    VibrationEffect.createOneShot(
                                        50,
                                        VibrationEffect.DEFAULT_AMPLITUDE
                                    )
                                )
                            }
                        ))
                }
            }
            Spacer(Modifier.weight(2f, true))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f, true))
                TextButton(
                    onClick = { /* TODO: open input dialog */ },
                    content = { Text("#" + color.hexString) }
                )
                Spacer(Modifier.weight(2f, true))
                Text("Select:")
                TextButton(
                    onClick = { selectedLeds = leds.map { l -> l.id }.toSet() },
                    content = { Text("All") }
                )
                TextButton(
                    onClick = { selectedLeds = emptySet() },
                    content = { Text("None") }
                )
                Spacer(Modifier.weight(1f, true))
            }
            Spacer(Modifier.weight(2f, true))
            ColorPart.values().forEach {
                ColorSlider(vm, selectedLeds, color, it)
                Spacer(Modifier.weight(1f, true))
            }
        }
    }
}

