package de.mk.ledmatrixbtapp.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.mk.ledmatrixbtapp.data.ViewModel

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
object SelectDeviceDialog {
    private var show by mutableStateOf(false)

    fun show() {
        show = true
    }

    @Composable
    fun compose(vm: ViewModel) {

        val deviceSet by vm.devices.collectAsState()
        val current by vm.btDevice.collectAsState()
        var selection by remember { mutableStateOf(current) }

        LaunchedEffect(current) { if (current == null) show() }
        LaunchedEffect(show) { selection = current }

        if (!show) return

        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("Select LED Matrix Device") },
            text = {
                LazyColumn {
                    if (deviceSet.isEmpty()) item {
                        ListItem(
                            leadingContent = { Icon(Icons.Rounded.Warning, null) },
                            headlineText = { Text("No devices found") },
                            supportingText = { Text("Please pair your device") }
                        )
                    }
                    else items(deviceSet.toList()) {
                        val selected by remember { derivedStateOf { selection == it } }
                        ListItem(
                            headlineText = { Text(it.name) },
                            supportingText = { Text(it.address) },
                            trailingContent = {
                                RadioButton(
                                    selected = selected,
                                    onClick = { selection = it }
                                )
                            },
                            modifier = Modifier.clickable { selection = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setBtDevice(selection!!)
                        show = false
                    },
                    content = { Text("Select") },
                    enabled = selection != null
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { show = false },
                    content = { Text("Cancel") }
                )
            },
        )
    }
}