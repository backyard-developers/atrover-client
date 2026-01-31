package com.example.arduinousbpoc.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.arduinousbpoc.network.ConnectionState
import com.example.arduinousbpoc.network.StreamingState

@Composable
fun RemoteControlScreen(
    commandConnectionState: ConnectionState,
    mediaConnectionState: ConnectionState,
    streamingState: StreamingState,
    roverId: String?,
    lastCommand: String?,
    usbConnectionStatus: String,
    isUsbConnected: Boolean,
    onConnect: (serverIp: String, commandPort: Int, mediaPort: Int, roverName: String) -> Unit,
    onDisconnect: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    errorLog: String = "",
    modifier: Modifier = Modifier
) {
    var serverIp by remember { mutableStateOf("192.168.0.2") }
    var commandPort by remember { mutableStateOf("8080") }
    var mediaPort by remember { mutableStateOf("8081") }
    var roverName by remember { mutableStateOf("ATRover-Android") }

    val isCommandConnected = commandConnectionState == ConnectionState.Connected
    val isMediaConnected = mediaConnectionState == ConnectionState.Connected
    val isStreaming = streamingState == StreamingState.Streaming

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "원격 제어 (Backend 연동)",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Server settings ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = serverIp,
                onValueChange = { serverIp = it },
                label = { Text("서버 IP") },
                modifier = Modifier.weight(2f),
                singleLine = true,
                enabled = !isCommandConnected
            )
            OutlinedTextField(
                value = commandPort,
                onValueChange = { commandPort = it },
                label = { Text("CMD") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isCommandConnected
            )
            OutlinedTextField(
                value = mediaPort,
                onValueChange = { mediaPort = it },
                label = { Text("Media") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isCommandConnected
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = roverName,
            onValueChange = { roverName = it },
            label = { Text("로버 이름") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isCommandConnected
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Connect / Disconnect ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onConnect(
                        serverIp,
                        commandPort.toIntOrNull() ?: 8080,
                        mediaPort.toIntOrNull() ?: 8081,
                        roverName
                    )
                },
                enabled = !isCommandConnected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.weight(1f)
            ) {
                Text("연결")
            }
            Button(
                onClick = onDisconnect,
                enabled = isCommandConnected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.weight(1f)
            ) {
                Text("연결 해제")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Status display ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("상태", style = MaterialTheme.typography.titleSmall)

            StatusRow("Command Server", commandConnectionState.name, isCommandConnected)
            StatusRow("Media Server", mediaConnectionState.name, isMediaConnected)
            StatusRow("USB 연결", usbConnectionStatus, isUsbConnected)

            if (roverId != null) {
                Text(
                    text = "Rover ID: $roverId",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
            }

            if (lastCommand != null) {
                Text(
                    text = "마지막 명령: $lastCommand",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "스트리밍: ${streamingState.name}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // --- Error log ---
        if (errorLog.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorLog,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF44336),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF44336).copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Camera streaming toggle ---
        Text("카메라 스트리밍", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        onStartStreaming()
                    }
                },
                enabled = isMediaConnected && !isStreaming,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                modifier = Modifier.weight(1f)
            ) {
                Text("스트리밍 시작")
            }
            Button(
                onClick = onStopStreaming,
                enabled = isStreaming,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.weight(1f)
            ) {
                Text("스트리밍 중지")
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, status: String, isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                    RoundedCornerShape(4.dp)
                )
        )
        Text(
            text = "$label: $status",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
