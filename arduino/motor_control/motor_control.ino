/*
 * Arduino DC Motor Control via USB Serial
 * L293D Motor Shield 사용
 *
 * Serial 명령:
 * - '0': 정지 (RELEASE)
 * - '1': 정회전 (FORWARD)
 * - '2': 역회전 (BACKWARD)
 */

#include <AFMotor.h>

// M1 포트에 연결된 DC 모터
AF_DCMotor motor(1);

// 모터 속도 (0-255)
const int MOTOR_SPEED = 255;

void setup() {
  Serial.begin(9600);

  // 모터 초기화
  motor.setSpeed(MOTOR_SPEED);
  motor.run(RELEASE);

  Serial.println("Motor Control Ready");
  Serial.println("Commands: 0=Stop, 1=Forward, 2=Backward");
}

void loop() {
  if (Serial.available() > 0) {
    char command = Serial.read();

    switch (command) {
      case '0':
        motor.run(RELEASE);
        Serial.println("Motor: STOP");
        break;

      case '1':
        motor.run(FORWARD);
        Serial.println("Motor: FORWARD");
        break;

      case '2':
        motor.run(BACKWARD);
        Serial.println("Motor: BACKWARD");
        break;
    }
  }
}
