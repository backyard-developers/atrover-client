/*
 * Dual DC Motor Control via USB Serial
 * L293D Motor Shield 사용
 *
 * 프로토콜: <[모터ID][명령][체크섬]>
 * 응답: <[상태][모터ID][명령]>
 */

#include <AFMotor.h>

// M1, M2 포트에 연결된 DC 모터
AF_DCMotor motor1(1);
AF_DCMotor motor2(2);

// 모터 속도 (0-255)
const int MOTOR_SPEED = 255;

// 프로토콜 상수
const char STX = '<';
const char ETX = '>';

// 버퍼
char buffer[5];
int bufferIndex = 0;
bool receiving = false;

void setup() {
  Serial.begin(9600);

  // 모터 초기화
  motor1.setSpeed(MOTOR_SPEED);
  motor2.setSpeed(MOTOR_SPEED);
  motor1.run(RELEASE);
  motor2.run(RELEASE);

  Serial.println("<O00>");  // Ready
}

void loop() {
  while (Serial.available() > 0) {
    char c = Serial.read();

    if (c == STX) {
      // 새 메시지 시작
      bufferIndex = 0;
      receiving = true;
    } else if (c == ETX && receiving) {
      // 메시지 끝
      receiving = false;
      processCommand();
    } else if (receiving && bufferIndex < 4) {
      buffer[bufferIndex++] = c;
    }
  }
}

void processCommand() {
  if (bufferIndex != 3) {
    sendResponse('E', '0', '0');
    return;
  }

  char motorId = buffer[0];
  char command = buffer[1];
  char checksum = buffer[2];

  // 체크섬 검증
  char expectedChecksum = motorId ^ command;
  if (checksum != expectedChecksum) {
    sendResponse('E', motorId, command);
    return;
  }

  // 모터ID 검증
  if (motorId != '1' && motorId != '2') {
    sendResponse('M', motorId, command);
    return;
  }

  // 명령 검증
  if (command != '0' && command != '1' && command != '2') {
    sendResponse('X', motorId, command);
    return;
  }

  // 모터 선택
  AF_DCMotor* motor = (motorId == '1') ? &motor1 : &motor2;

  // 명령 실행
  switch (command) {
    case '0':
      motor->run(RELEASE);
      break;
    case '1':
      motor->run(FORWARD);
      break;
    case '2':
      motor->run(BACKWARD);
      break;
  }

  sendResponse('O', motorId, command);
}

void sendResponse(char status, char motorId, char command) {
  Serial.print(STX);
  Serial.print(status);
  Serial.print(motorId);
  Serial.print(command);
  Serial.println(ETX);
}
