package com.example.arduinousbpoc.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val backendJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// --- Command Server Messages ---

@Serializable
data class RegisterMessage(
    val type: String = "register",
    val name: String
)

@Serializable
data class RegisteredMessage(
    val type: String = "registered",
    @SerialName("roverId") val roverId: String
)

@Serializable
data class HeartbeatMessage(
    val type: String = "heartbeat"
)

@Serializable
data class HeartbeatAckMessage(
    val type: String = "heartbeat_ack"
)

@Serializable
data class RoverCommand(
    val type: String = "command", // server sends type:"command" in the command object itself
    val action: String, // move, stop, rotate, calibrate
    val direction: String? = null, // forward, backward, left, right
    val speed: Int? = null, // 0-100
    val degrees: Int? = null
)

@Serializable
data class CommandMessage(
    val type: String = "command",
    val roverId: String? = null,
    val command: RoverCommand
)

@Serializable
data class TelemetryData(
    val speed: Int = 0,
    val heading: Int = 0,
    val status: String = "idle"
)

@Serializable
data class TelemetryMessage(
    val type: String = "telemetry",
    val roverId: String,
    val data: TelemetryData
)

// --- Media Server Messages ---

@Serializable
data class MediaRegisterMessage(
    val type: String = "register",
    val roverId: String
)

@Serializable
data class StartStreamMessage(
    val type: String = "start_stream"
)

@Serializable
data class StopStreamMessage(
    val type: String = "stop_stream"
)

// --- Motor Config Messages ---

@Serializable
data class MotorMapping(
    val left: Int = 3,
    val right: Int = 4,
    val leftReversed: Boolean = false,
    val rightReversed: Boolean = false
)

@Serializable
data class MotorConfigMessage(
    val type: String = "motor_config",
    val mapping: MotorMapping
)

@Serializable
data class MotorConfigUpdateMessage(
    val type: String = "motor_config_update",
    val roverId: String,
    val mapping: MotorMapping
)

// --- Generic envelope for parsing incoming type field ---

@Serializable
data class MessageEnvelope(
    val type: String
)
