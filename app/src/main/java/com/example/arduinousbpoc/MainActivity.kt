package com.example.arduinousbpoc

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.arduinousbpoc.network.BackendConfig
import com.example.arduinousbpoc.network.CommandSocketManager
import com.example.arduinousbpoc.network.ConnectionState
import com.example.arduinousbpoc.network.MediaSocketManager
import com.example.arduinousbpoc.network.MotorMapping
import com.example.arduinousbpoc.screen.MainScreen
import com.example.arduinousbpoc.ui.theme.ArduinoUsbPocTheme
import com.example.arduinousbpoc.usb.UsbMotorController

class MainActivity : ComponentActivity() {

    private lateinit var motorController: UsbMotorController
    private lateinit var motorConfigPrefs: MotorConfigPreferences
    private lateinit var commandSocketManager: CommandSocketManager
    private lateinit var mediaSocketManager: MediaSocketManager
    private var backendConfig: BackendConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // USB + Motor
        motorController = UsbMotorController(this)

        // Motor config
        motorConfigPrefs = MotorConfigPreferences(this)
        motorController.leftMotor = motorConfigPrefs.getLeftMotor()
        motorController.rightMotor = motorConfigPrefs.getRightMotor()

        // Backend managers
        commandSocketManager = CommandSocketManager(
            onCommandReceived = { command -> motorController.handleRoverCommand(command) },
            onMotorConfigReceived = { mapping ->
                motorController.leftMotor = mapping.left
                motorController.rightMotor = mapping.right
                motorConfigPrefs.save(mapping.left, mapping.right)
            }
        )
        mediaSocketManager = MediaSocketManager()

        // USB receiver
        val filter = motorController.createIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(motorController.usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(motorController.usbReceiver, filter)
        }

        motorController.findAndConnectDevice()

        setContent {
            ArduinoUsbPocTheme {
                val cmdState by commandSocketManager.connectionState.collectAsState()
                val mediaState by mediaSocketManager.connectionState.collectAsState()
                val streamState by mediaSocketManager.streamingState.collectAsState()
                val roverId by commandSocketManager.roverId.collectAsState()
                val lastCmd by commandSocketManager.lastCommand.collectAsState()
                val cmdError by commandSocketManager.errorLog.collectAsState()
                val mediaError by mediaSocketManager.errorLog.collectAsState()

                // Auto-connect media server when command server registers
                androidx.compose.runtime.LaunchedEffect(roverId) {
                    val id = roverId
                    val cfg = backendConfig
                    if (id != null && cfg != null && mediaState == ConnectionState.Disconnected) {
                        mediaSocketManager.connect(cfg, id)
                    }
                }

                MainScreen(
                    connectionStatus = motorController.connectionStatus,
                    isConnected = motorController.isConnected,
                    onConnect = { motorController.findAndConnectDevice() },
                    motor1Status = motorController.motor1Status,
                    motor2Status = motorController.motor2Status,
                    motor3Status = motorController.motor3Status,
                    motor4Status = motorController.motor4Status,
                    motor1Speed = motorController.motor1Speed,
                    motor2Speed = motorController.motor2Speed,
                    motor3Speed = motorController.motor3Speed,
                    motor4Speed = motorController.motor4Speed,
                    onSpeedChange = { motorId, speed ->
                        motorController.setSpeed(motorId, speed)
                        val cmd = when (motorId) {
                            1 -> motorController.motor1Command
                            2 -> motorController.motor2Command
                            3 -> motorController.motor3Command
                            4 -> motorController.motor4Command
                            else -> 0
                        }
                        motorController.sendMotorCommand(motorId, cmd)
                    },
                    lastResponse = motorController.lastResponse,
                    onMotorCommand = { motorId, command ->
                        motorController.sendMotorCommand(motorId, command)
                    },
                    commandConnectionState = cmdState,
                    mediaConnectionState = mediaState,
                    streamingState = streamState,
                    roverId = roverId,
                    lastCommand = lastCmd,
                    onBackendConnect = { ip, cmdPort, mdPort, name ->
                        val config = BackendConfig(ip, cmdPort, mdPort, name)
                        backendConfig = config
                        commandSocketManager.connect(config)
                    },
                    onBackendDisconnect = {
                        mediaSocketManager.disconnect()
                        commandSocketManager.disconnect()
                        backendConfig = null
                    },
                    onStartStreaming = {
                        mediaSocketManager.startStreaming(this@MainActivity, this@MainActivity)
                    },
                    onStopStreaming = {
                        mediaSocketManager.stopStreaming()
                    },
                    errorLog = listOfNotNull(cmdError, mediaError).joinToString("\n"),
                    leftMotor = motorController.leftMotor,
                    rightMotor = motorController.rightMotor,
                    onMotorConfigChange = { left, right ->
                        motorController.leftMotor = left
                        motorController.rightMotor = right
                        motorConfigPrefs.save(left, right)
                        commandSocketManager.sendMotorConfig(MotorMapping(left, right))
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commandSocketManager.destroy()
        mediaSocketManager.destroy()
        motorController.disconnect()
        unregisterReceiver(motorController.usbReceiver)
    }
}
