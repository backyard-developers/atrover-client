package com.example.arduinousbpoc.network

data class BackendConfig(
    val serverIp: String,
    val commandPort: Int = 8080,
    val mediaPort: Int = 8081,
    val roverName: String = "ATRover-Android"
) {
    val commandWsUrl: String get() = "ws://$serverIp:$commandPort/ws"
    val mediaWsUrl: String get() = "ws://$serverIp:$mediaPort/ws"
}
