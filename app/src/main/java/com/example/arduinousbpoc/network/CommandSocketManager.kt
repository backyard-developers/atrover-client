package com.example.arduinousbpoc.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

private const val TAG = "CommandSocketManager"

enum class ConnectionState {
    Disconnected, Connecting, Connected, Error
}

class CommandSocketManager(
    private val onCommandReceived: (RoverCommand) -> Unit,
    private val onMotorConfigReceived: ((MotorMapping) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _roverId = MutableStateFlow<String?>(null)
    val roverId: StateFlow<String?> = _roverId

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand

    private val _errorLog = MutableStateFlow<String?>(null)
    val errorLog: StateFlow<String?> = _errorLog

    private val _motorMapping = MutableStateFlow(MotorMapping())
    val motorMapping: StateFlow<MotorMapping> = _motorMapping

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var config: BackendConfig? = null
    private var shouldReconnect = false
    private var reconnectAttempt = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(config: BackendConfig) {
        this.config = config
        shouldReconnect = true
        reconnectAttempt = 0
        doConnect(config)
    }

    private fun doConnect(config: BackendConfig) {
        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(config.commandWsUrl)
            .build()

        Log.d(TAG, "Connecting to ${config.commandWsUrl}")

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Command Server")
                mainHandler.post { _connectionState.value = ConnectionState.Connected }
                reconnectAttempt = 0

                // Send register message
                val registerMsg = backendJson.encodeToString(
                    RegisterMessage.serializer(),
                    RegisterMessage(name = config.roverName)
                )
                webSocket.send(registerMsg)
                Log.d(TAG, "Sent register: $registerMsg")

                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                mainHandler.post { handleMessage(text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code $reason")
                mainHandler.post { _connectionState.value = ConnectionState.Disconnected }
                stopHeartbeat()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed", t)
                mainHandler.post {
                    _connectionState.value = ConnectionState.Error
                    _errorLog.value = "CMD 연결 실패: ${t.message}"
                }
                stopHeartbeat()
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val envelope = backendJson.decodeFromString(MessageEnvelope.serializer(), text)
            when (envelope.type) {
                "registered" -> {
                    val msg = backendJson.decodeFromString(RegisteredMessage.serializer(), text)
                    _roverId.value = msg.roverId
                    Log.d(TAG, "Registered with roverId: ${msg.roverId}")
                }
                "heartbeat_ack" -> {
                    Log.d(TAG, "Heartbeat ack received")
                }
                "command_ack" -> {
                    Log.d(TAG, "Command ack received")
                }
                "error" -> {
                    Log.e(TAG, "Server error: $text")
                    _errorLog.value = "CMD error: $text"
                }
                "command" -> {
                    // Server sends RoverCommand directly (not wrapped in CommandMessage)
                    val cmd = backendJson.decodeFromString(RoverCommand.serializer(), text)
                    _lastCommand.value = "${cmd.action} ${cmd.direction ?: ""}"
                    onCommandReceived(cmd)
                }
                "motor_config" -> {
                    val msg = backendJson.decodeFromString(MotorConfigMessage.serializer(), text)
                    _motorMapping.value = msg.mapping
                    onMotorConfigReceived?.invoke(msg.mapping)
                    Log.d(TAG, "Motor config received: left=${msg.mapping.left}, right=${msg.mapping.right}")
                }
                "motor_config_ack" -> {
                    Log.d(TAG, "Motor config ack received")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: $text", e)
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(30_000)
                val msg = backendJson.encodeToString(
                    HeartbeatMessage.serializer(),
                    HeartbeatMessage()
                )
                ws.send(msg)
                Log.d(TAG, "Sent heartbeat")
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val cfg = config ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = minOf(1000L * (1 shl minOf(reconnectAttempt, 4)), 30_000L)
            reconnectAttempt++
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
            delay(delayMs)
            doConnect(cfg)
        }
    }

    fun sendTelemetry(data: TelemetryData) {
        val id = _roverId.value ?: return
        val msg = backendJson.encodeToString(
            TelemetryMessage.serializer(),
            TelemetryMessage(roverId = id, data = data)
        )
        webSocket?.send(msg)
    }

    fun sendMotorConfig(mapping: MotorMapping) {
        val id = _roverId.value ?: return
        val msg = backendJson.encodeToString(
            MotorConfigUpdateMessage.serializer(),
            MotorConfigUpdateMessage(roverId = id, mapping = mapping)
        )
        webSocket?.send(msg)
        _motorMapping.value = mapping
        Log.d(TAG, "Sent motor config: left=${mapping.left}, right=${mapping.right}")
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        stopHeartbeat()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        _roverId.value = null
        _lastCommand.value = null
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
