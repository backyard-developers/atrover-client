package com.example.arduinousbpoc.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MotorControlScreen(
    connectionStatus: String,
    isConnected: Boolean,
    motor1Status: String,
    motor2Status: String,
    motor3Status: String,
    motor4Status: String,
    motor1Speed: Int,
    motor2Speed: Int,
    motor3Speed: Int,
    motor4Speed: Int,
    onSpeedChange: (motorId: Int, speed: Int) -> Unit,
    lastResponse: String,
    onMotorCommand: (motorId: Int, command: Int) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MOTOR CONTROL",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Motor 1 Card
        MotorCard(
            motorName = "MOTOR 1",
            status = motor1Status,
            speed = motor1Speed,
            isConnected = isConnected,
            onSpeedChange = { onSpeedChange(1, it) },
            onReverse = { onMotorCommand(1, 2) },
            onStop = { onMotorCommand(1, 0) },
            onForward = { onMotorCommand(1, 1) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Motor 2 Card
        MotorCard(
            motorName = "MOTOR 2",
            status = motor2Status,
            speed = motor2Speed,
            isConnected = isConnected,
            onSpeedChange = { onSpeedChange(2, it) },
            onReverse = { onMotorCommand(2, 2) },
            onStop = { onMotorCommand(2, 0) },
            onForward = { onMotorCommand(2, 1) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Motor 3 Card
        MotorCard(
            motorName = "MOTOR 3",
            status = motor3Status,
            speed = motor3Speed,
            isConnected = isConnected,
            onSpeedChange = { onSpeedChange(3, it) },
            onReverse = { onMotorCommand(3, 2) },
            onStop = { onMotorCommand(3, 0) },
            onForward = { onMotorCommand(3, 1) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Motor 4 Card
        MotorCard(
            motorName = "MOTOR 4",
            status = motor4Status,
            speed = motor4Speed,
            isConnected = isConnected,
            onSpeedChange = { onSpeedChange(4, it) },
            onReverse = { onMotorCommand(4, 2) },
            onStop = { onMotorCommand(4, 0) },
            onForward = { onMotorCommand(4, 1) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                StatusRow(
                    label = "Connection",
                    value = connectionStatus,
                    valueColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722)
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusRow(
                    label = "Last Response",
                    value = lastResponse,
                    valueColor = if (lastResponse.contains("O")) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isConnected) {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Arduino 연결")
            }
        }
    }
}

@Composable
private fun MotorCard(
    motorName: String,
    status: String,
    speed: Int,
    isConnected: Boolean,
    onSpeedChange: (Int) -> Unit,
    onReverse: () -> Unit,
    onStop: () -> Unit,
    onForward: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = motorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed:",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = speed.toFloat(),
                    onValueChange = { onSpeedChange(it.toInt()) },
                    valueRange = 0f..255f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = speed.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onReverse,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("◀ REV")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onStop,
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("STOP")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onForward,
                    enabled = isConnected,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("FWD ▶")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Medium
        )
    }
}
