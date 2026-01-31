/*
 * Quad DC Motor Control via USB Serial
 * L293D Motor Shield 사용
 *
 * 프로토콜: <[모터ID][명령][속도][체크섬]>
 * 응답: <[상태][모터ID][명령]>
 */

#include <AFMotor.h>

// M1~M4 포트에 연결된 DC 모터
AF_DCMotor motor1(1);
AF_DCMotor motor2(2);
AF_DCMotor motor3(3);
AF_DCMotor motor4(4);

// 프로토콜 상수
const char STX = '<';
const char ETX = '>';

// 버퍼
char buffer[6];
int bufferIndex = 0;
bool receiving = false;

void setup() {
  Serial.begin(9600);

  // 모터 초기화 (속도는 명령 시 설정)
  motor1.run(RELEASE);
  motor2.run(RELEASE);
  motor3.run(RELEASE);
  motor4.run(RELEASE);

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
    } else if (receiving && bufferIndex < 5) {
      buffer[bufferIndex++] = c;
    }
  }
}

void processCommand() {
  if (bufferIndex != 4) {
    sendResponse('E', '0', '0');
    return;
  }

  char motorId = buffer[0];
  char command = buffer[1];
  uint8_t speed = (uint8_t)buffer[2];
  char checksum = buffer[3];

  // 체크섬 검증
  char expectedChecksum = motorId ^ command ^ speed;
  if (checksum != expectedChecksum) {
    sendResponse('E', motorId, command);
    return;
  }

  // 모터ID 검증
  if (motorId < '1' || motorId > '4') {
    sendResponse('M', motorId, command);
    return;
  }

  // 명령 검증
  if (command != '0' && command != '1' && command != '2') {
    sendResponse('X', motorId, command);
    return;
  }

  // 모터 선택
  AF_DCMotor* motor;
  switch (motorId) {
    case '1': motor = &motor1; break;
    case '2': motor = &motor2; break;
    case '3': motor = &motor3; break;
    case '4': motor = &motor4; break;
  }

  // 속도 설정
  motor->setSpeed(speed);

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
