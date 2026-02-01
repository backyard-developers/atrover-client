package com.example.arduinousbpoc.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.arduinousbpoc.network.ConnectionState
import com.example.arduinousbpoc.network.StreamingState

enum class TestTab(val title: String) {
    MOTOR_CONTROL("모터"),
    CAMERA_STREAM("스트리밍"),
    REMOTE_CONTROL("원격")
}

@Composable
fun MainScreen(
    connectionStatus: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    motor1Status: String,
    motor2Status: String,
    motor3Status: String,
    motor4Status: String,
    motor1Speed: Int,
    motor2Speed: Int,
    motor3Speed: Int,
    motor4Speed: Int,
    onSpeedChange: (Int, Int) -> Unit,
    lastResponse: String,
    onMotorCommand: (Int, Int) -> Unit,
    commandConnectionState: ConnectionState,
    mediaConnectionState: ConnectionState,
    streamingState: StreamingState,
    roverId: String?,
    lastCommand: String?,
    onBackendConnect: (String, Int, Int, String) -> Unit,
    onBackendDisconnect: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    errorLog: String,
    leftMotor: Int = 3,
    rightMotor: Int = 4,
    onMotorConfigChange: (Int, Int) -> Unit = { _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = TestTab.entries

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    )
                }
            }

            when (tabs[selectedTab]) {
                TestTab.MOTOR_CONTROL -> {
                    MotorControlScreen(
                        connectionStatus = connectionStatus,
                        isConnected = isConnected,
                        motor1Status = motor1Status,
                        motor2Status = motor2Status,
                        motor3Status = motor3Status,
                        motor4Status = motor4Status,
                        motor1Speed = motor1Speed,
                        motor2Speed = motor2Speed,
                        motor3Speed = motor3Speed,
                        motor4Speed = motor4Speed,
                        onSpeedChange = onSpeedChange,
                        lastResponse = lastResponse,
                        onMotorCommand = onMotorCommand,
                        onConnect = onConnect
                    )
                }
                TestTab.CAMERA_STREAM -> {
                    CameraStreamScreen()
                }
                TestTab.REMOTE_CONTROL -> {
                    RemoteControlScreen(
                        commandConnectionState = commandConnectionState,
                        mediaConnectionState = mediaConnectionState,
                        streamingState = streamingState,
                        roverId = roverId,
                        lastCommand = lastCommand,
                        usbConnectionStatus = connectionStatus,
                        isUsbConnected = isConnected,
                        onConnect = onBackendConnect,
                        onDisconnect = onBackendDisconnect,
                        onStartStreaming = onStartStreaming,
                        onStopStreaming = onStopStreaming,
                        errorLog = errorLog,
                        leftMotor = leftMotor,
                        rightMotor = rightMotor,
                        onMotorConfigChange = onMotorConfigChange
                    )
                }
            }
        }
    }
}
