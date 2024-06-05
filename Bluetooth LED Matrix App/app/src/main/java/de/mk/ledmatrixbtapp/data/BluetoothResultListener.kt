package de.mk.ledmatrixbtapp.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * BluetoothResultListener is a class that extends Thread and is responsible for continuously reading data from an InputStream.
 * It accumulates the read data and emits it when no data is read for a set time period.
 *
 * @property inS The InputStream from which data is read.
 * @property timeout The time period in milliseconds after which the accumulated data is emitted if no data is read.
 */
class BluetoothResultListener(
    private val inS: InputStream,
    private val timeout: Long = 50, // timeout in milliseconds
) : Thread() {
    // ByteArrayOutputStream to accumulate the read data.
    private val buffer = ByteArrayOutputStream()

    // MutableSharedFlow to emit the accumulated data.
    private val _data = MutableSharedFlow<ByteArray>()

    // SharedFlow to expose the accumulated data.
    val data = _data.asSharedFlow()

    // The time at which the last data was received.
    private var lastReceive = System.currentTimeMillis()

    // Thread to emit the accumulated data when no data is read for the set time period.
    private val emitter = Thread {
        var emitted = false
        while (true) {
            if (System.currentTimeMillis() - lastReceive > timeout) {
                if (emitted) {
                    continue
                }
                synchronized(buffer) {
                    runBlocking { _data.emit(buffer.toByteArray()) }
                    buffer.reset()
                    emitted = true
                }
            } else {
                emitted = false
            }
        }
    }

    /**
     * The run method of the Thread.
     * It continuously reads data from the InputStream and writes it to the buffer.
     * It also updates the lastReceive time whenever data is read.
     */
    override fun run() {
        emitter.start()
        try {
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val length: Int = inS.read(bytes)
                if (length != -1) {
                    lastReceive = System.currentTimeMillis()
                    synchronized(buffer) { buffer.write(bytes, 0, length) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Method to reset the buffer.
     * It discards all data read up to that point.
     */
    fun reset() = synchronized(buffer, buffer::reset)
}