package com.app.basic_app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothService {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val ESP32_NAME = "ESP32_BT"
    private val UUID_STRING = "00001101-0000-1000-8000-00805F9B34FB" // Standard SerialPort service UUID

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var listenThread: Thread? = null
    private val _receivedMessage = MutableStateFlow<String?>(null)
    val receivedMessage: StateFlow<String?> = _receivedMessage

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    suspend fun connectToESP32(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First disconnect if already connected
            disconnect()
            
            // Find the ESP32 device
            val device = findESP32Device()
            if (device == null) {
                _connectionState.value = ConnectionState.Error("ESP32 device not found. Please make sure it's paired and turned on.")
                return@withContext false
            }

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(UUID_STRING))
                bluetoothSocket?.let { socket ->
                    // Cancel discovery before connecting
                    bluetoothAdapter?.cancelDiscovery()
                    
                    try {
                        socket.connect()
                        if (socket.isConnected) {
                            _connectionState.value = ConnectionState.Connected
                            startListening()
                            return@withContext true
                        }
                    } catch (e: IOException) {
                        try {
                            socket.close()
                        } catch (e2: IOException) {
                            _connectionState.value = ConnectionState.Error("Failed to close socket: ${e2.message}")
                        }
                        _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
                        return@withContext false
                    }
                }
            } catch (e: SecurityException) {
                _connectionState.value = ConnectionState.Error("Bluetooth permission denied")
                return@withContext false
            }
            
            _connectionState.value = ConnectionState.Error("Failed to create socket")
            return@withContext false
            
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Connection error: ${e.message}")
            return@withContext false
        }
    }

    private fun findESP32Device(): BluetoothDevice? {
        try {
            return bluetoothAdapter?.bondedDevices?.find { it.name == ESP32_NAME }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied while searching for ESP32")
            return null
        }
    }

    suspend fun sendSpeedLimit(speedLimit: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            if (bluetoothSocket?.isConnected != true) {
                _connectionState.value = ConnectionState.Error("Not connected to ESP32")
                return@withContext false
            }

            val outputStream = bluetoothSocket?.outputStream
            outputStream?.write(speedLimit.toString().toByteArray())
            outputStream?.flush()
            true
        } catch (e: IOException) {
            _connectionState.value = ConnectionState.Error("Failed to send data: ${e.message}")
            false
        }
    }

    suspend fun sendCustomMessage(message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (bluetoothSocket?.isConnected != true) {
                _connectionState.value = ConnectionState.Error("Not connected to ESP32")
                return@withContext false
            }
            val outputStream = bluetoothSocket?.outputStream
            outputStream?.write(message.toByteArray())
            outputStream?.flush()
            true
        } catch (e: IOException) {
            _connectionState.value = ConnectionState.Error("Failed to send data: "+e.message)
            false
        }
    }

    private fun startListening() {
        listenThread?.interrupt()
        listenThread = Thread {
            val buffer = ByteArray(1024)
            var messageBuffer = StringBuilder()
            
            while (!Thread.currentThread().isInterrupted && bluetoothSocket?.isConnected == true) {
                try {
                    val bytes = bluetoothSocket?.inputStream?.read(buffer)
                    if (bytes != null && bytes > 0) {
                        val receivedData = String(buffer, 0, bytes)
                        messageBuffer.append(receivedData)
                        
                        // Process complete messages (split by newlines)
                        val messages = messageBuffer.toString().split('\n', '\r')
                        
                        // Keep the last incomplete message in buffer
                        messageBuffer = StringBuilder()
                        if (messages.isNotEmpty() && !receivedData.endsWith('\n') && !receivedData.endsWith('\r')) {
                            messageBuffer.append(messages.last())
                        }
                        
                        // Process complete messages
                        for (i in 0 until messages.size - 1) {
                            val trimmedMessage = messages[i].trim()
                            if (trimmedMessage.isNotEmpty()) {
                                // Debug logging for received messages
                                android.util.Log.d("BluetoothReceive", "Received: '$trimmedMessage'")
                                _receivedMessage.value = trimmedMessage
                                
                                // Small delay to ensure message processing
                                Thread.sleep(50)
                            }
                        }
                        
                        // If message ends with newline, process the last message too
                        if ((receivedData.endsWith('\n') || receivedData.endsWith('\r')) && messages.isNotEmpty()) {
                            val lastMessage = messages.last().trim()
                            if (lastMessage.isNotEmpty()) {
                                android.util.Log.d("BluetoothReceive", "Received: '$lastMessage'")
                                _receivedMessage.value = lastMessage
                            }
                        }
                    }
                } catch (e: IOException) {
                    if (!Thread.currentThread().isInterrupted) {
                        _connectionState.value = ConnectionState.Error("Connection lost: ${e.message}")
                        break
                    }
                }
            }
        }.apply {
            start()
        }
    }

    fun disconnect() {
        try {
            listenThread?.interrupt()
            listenThread = null
            bluetoothSocket?.close()
            bluetoothSocket = null
            _connectionState.value = ConnectionState.Disconnected
        } catch (e: IOException) {
            _connectionState.value = ConnectionState.Error("Error disconnecting: ${e.message}")
        }
    }

    fun clearReceivedMessage() {
        _receivedMessage.value = null
    }
} 