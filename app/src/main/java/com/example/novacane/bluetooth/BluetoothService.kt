package com.example.novacane.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED
}

class BluetoothService(
    private val deviceName: String,
    private val uuid: UUID
) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var socket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null

    fun init(adapter: BluetoothAdapter?) {
        bluetoothAdapter = adapter
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(onDataReceived: (String) -> Unit) {

        Log.d("BT_DEBUG", "connect() called")

        val device = bluetoothAdapter?.bondedDevices
            ?.firstOrNull { it.name == deviceName }

        if (device == null) {
            Log.e("BT_DEBUG", "❌ Device not found: $deviceName")
            _connectionState.value = ConnectionState.FAILED
            return
        }

        connectedDevice = device
        _connectionState.value = ConnectionState.CONNECTING

        thread {
            try {
                Log.d("BT_DEBUG", "Thread started")

                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()

                if (socket?.isConnected != true) {
                    Log.e("BT_DEBUG", "❌ Socket not connected")
                    _connectionState.value = ConnectionState.FAILED
                    startReconnection(onDataReceived)
                    return@thread
                }

                Log.d("BT_DEBUG", "✅ Bluetooth Connected")
                _connectionState.value = ConnectionState.CONNECTED

                val inputStream = socket?.inputStream

                if (inputStream == null) {
                    Log.e("BT_DEBUG", "❌ InputStream is NULL")
                    _connectionState.value = ConnectionState.FAILED
                    startReconnection(onDataReceived)
                    return@thread
                }

                readData(inputStream, onDataReceived)

            } catch (e: Exception) {
                Log.e("BT_DEBUG", "❌ Bluetooth Error", e)
                _connectionState.value = ConnectionState.FAILED
                startReconnection(onDataReceived)
            }
        }
    }

    private fun readData(
        inputStream: InputStream,
        onDataReceived: (String) -> Unit
    ) {
        val buffer = ByteArray(1024)
        var accumulatedData = ""

        try {
            while (true) {
                val bytes = inputStream.read(buffer)

                if (bytes == -1) {
                    throw Exception("Stream closed")
                }

                val chunk = String(buffer, 0, bytes)
                Log.d("BT_DEBUG", "RAW CHUNK: $chunk")

                accumulatedData += chunk

                val messages = accumulatedData.split("\n")
                accumulatedData = messages.last()

                for (i in 0 until messages.size - 1) {
                    val clean = messages[i].trim()

                    if (clean.isNotEmpty()) {
                        Log.d("BT_DEBUG", "CLEAN: $clean")
                        onDataReceived(clean)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BT_DEBUG", "❌ Read Error", e)

            _connectionState.value = ConnectionState.DISCONNECTED

            try {
                socket?.close()
            } catch (_: Exception) {}

            // 🔁 Trigger reconnection
            startReconnection(onDataReceived)
        }
    }

    private fun startReconnection(onDataReceived: (String) -> Unit) {

        val device = connectedDevice ?: return

        _connectionState.value = ConnectionState.RECONNECTING

        CoroutineScope(Dispatchers.IO).launch {

            while (_connectionState.value != ConnectionState.CONNECTED) {

                try {
                    delay(3000)

                    Log.d("BT_DEBUG", "🔁 Attempting reconnection...")

                    socket?.close()

                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket?.connect()

                    if (socket?.isConnected == true) {

                        Log.d("BT_DEBUG", "✅ Reconnected successfully")

                        _connectionState.value = ConnectionState.CONNECTED

                        val inputStream = socket?.inputStream
                        if (inputStream != null) {
                            readData(inputStream, onDataReceived)
                        }

                        break
                    }

                } catch (e: Exception) {
                    Log.e("BT_DEBUG", "❌ Reconnect failed", e)
                    _connectionState.value = ConnectionState.RECONNECTING
                }
            }
        }
    }

    fun disconnect() {
        try {
            socket?.close()
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.d("BT_DEBUG", "Disconnected")
        } catch (e: Exception) {
            Log.e("BT_DEBUG", "Disconnect error", e)
        }
    }
}