package de.mk.ledmatrixbtapp.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mk.ledmatrixbtapp.data.ViewModel.Command.Companion.cmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.Blocking
import java.io.IOException
import java.util.UUID

/**
 * ViewModel class for managing Bluetooth devices and LED colors.
 *
 * This class extends AndroidViewModel and takes an Application as a parameter.
 * It manages the state of Bluetooth devices and LED colors in the application.
 * It also handles Bluetooth permissions, Bluetooth device connection, and data transmission.
 *
 * @property app The application context.
 */
class ViewModel(private val app: Application) : AndroidViewModel(app) {
    private val btManager: BluetoothManager = app.getSystemService(BluetoothManager::class.java)
    private val btAdapter: BluetoothAdapter = btManager.adapter
    private val btDeviceAddressKey = stringPreferencesKey("bluetooth_device_address")

    private val _devices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val devices: StateFlow<Set<BluetoothDevice>> = _devices.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == intent.action) {
                _devices.value = btAdapter.bondedDevices
            }
        }
    }

    private val _btDevice: MutableStateFlow<BluetoothDevice?> = MutableStateFlow(null)
    val btDevice: StateFlow<BluetoothDevice?> = _btDevice.asStateFlow()

    private val _leds = MutableStateFlow(Array(64) { LED(it, Color.Green) })
    val leds: StateFlow<Array<LED>> = _leds.asStateFlow()

    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private val outS get() = socket?.outputStream
    private val inS get() = socket?.inputStream
    private var resultListener: BluetoothResultListener? = null

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()


    init {
        if (ActivityCompat.checkSelfPermission(
                app,
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH
                else Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Bluetooth permission not granted")
        }

        app.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        _devices.value = btAdapter.bondedDevices

        runBlocking {
            app.dataStore.data.map { it[btDeviceAddressKey] }.first()?.let { address ->
                _btDevice.value = _devices.value.find { it.address == address }
            }
        }

        viewModelScope.launch {
            _btDevice.filterNotNull().collectLatest { device ->
                println("Connecting to device: ${device.name}")
                try {
                    socket?.close()
                    socket = device.createRfcommSocketToServiceRecord(uuid).also { it.connect() }
                    resultListener = inS?.let { BluetoothResultListener(it) }
                    resultListener?.start()
                    withContext(Dispatchers.IO) { readColors() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        app.unregisterReceiver(receiver)
        socket?.close()
    }


    fun setBtDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            app.dataStore.edit { it[btDeviceAddressKey] = device.address }
            _btDevice.value = device
        }
    }

    fun setLedColor(leds: Set<Int>, color: Color) {
        _leds.value = _leds.value.map {
            if (it.id in leds) it.copy(color = color) else it
        }.toTypedArray()
    }

    fun sendColorValues(leds: Set<Int>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (leds.size == _leds.value.size) {
                        _leds.value.first().color.let(::writeColorAll)
                    } else {
                        // the bluetooth module can only handle 16 leds at most
                        // so we need to split the leds into chunks
                        _leds.value
                            .filter { it.id in leds }
                            .chunked(16)
                            .map(List<LED>::toSet)
                            .forEach(::writeColors)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _lastError.value = e.message
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        viewModelScope.launch {
            try {
                println("Refreshing devices")
                if (socket?.isConnected == false) {
                    socket?.close()
                    socket = btDevice.value?.createRfcommSocketToServiceRecord(uuid)
                        ?.also { it.connect() }
                    resultListener = inS?.let { BluetoothResultListener(it) }
                    resultListener?.start()
                }
                withContext(Dispatchers.IO) { readColors() }
            } catch (e: Exception) {
                e.printStackTrace()
                _lastError.value = e.message
            }
        }
    }


    /*
     * Bluetooth command structure:
     *
     * 0x01
     *      get the color of all leds
     *      1 byte: cmd
     *      respond: cmd, status, ([number, r, g, b] * count)
     * 0x02
     *      set some specific leds to a specific color
     *      at most count * 4 + 1 bytes: cmd, [number, r, g, b] * count
     *      respond: cmd, status
     * 0x03
     *      set all leds to a specific color
     *      4 bytes: cmd, r, g, b
     *      respond: cmd, status
     *
     * respond codes:
     *      0x00: success
     *      0x01: invalid data length
     *      0x02: led number out of range
     *      0xFE: invalid state
     *      0xFF: invalid command
     */

    private val Byte.ok get() = toInt() == 0x00

    private enum class Command(private val code: Byte) {
        READ(0x01),
        WRITE(0x02),
        WRITE_ALL(0x03),
        ;

        operator fun invoke(vararg data: Byte) = byteArrayOf(code, *data)

        companion object {
            val Byte.cmd get() = values().find { it.code == this }
        }
    }

    @Throws(IOException::class)
    @Blocking
    private fun readColors() = Command.READ().write()

    @Throws(IOException::class)
    @Blocking
    private fun writeColors(leds: Set<LED>) = Command.WRITE(*leds.flatMap { (id, color) ->
        listOf(
            id.toByte(),
            color.red.times(255).toInt().toByte(),
            color.green.times(255).toInt().toByte(),
            color.blue.times(255).toInt().toByte()
        )
    }.toByteArray()).write()

    @Throws(IOException::class)
    @Blocking
    private fun writeColorAll(color: Color) = Command.WRITE_ALL(
        color.red.times(255).toInt().toByte(),
        color.green.times(255).toInt().toByte(),
        color.blue.times(255).toInt().toByte()
    ).write()

    @Throws(IOException::class)
    @Blocking
    private fun ByteArray.write() {
        resultListener?.reset()
        outS?.write(this)
        outS?.flush()
        runBlocking {
            withTimeoutOrNull(500) {
                // wait for the response
                resultListener?.data?.filter { it.isNotEmpty() }?.first()?.parseResult()
                Unit
            } ?: _lastError.also { it.value = "Timeout" }
        }
    }

    private fun ByteArray.parseResult() {
        println("RECEIVED: ${joinToString { "%02X".format(it) }}")
        val status = this[1]
        if (!status.ok) {
            _lastError.value = when (status) {
                0x01.toByte() -> "Invalid data length"
                0x02.toByte() -> "LED number out of range"
                0xFE.toByte() -> "Invalid state"
                0xFF.toByte() -> "Invalid command"
                else -> "Unknown error"
            }
            return
        }
        when (this[0].cmd) {
            Command.READ -> this
                .drop(2)
                .map(Byte::toInt)
                .chunked(4)
                .map { LED(it[0], Color(it[1], it[2], it[3])) }
                .toTypedArray()
                .also { _leds.value = it }

            Command.WRITE, Command.WRITE_ALL -> Unit
            else -> _lastError.value = "Invalid command"
        }
    }
}