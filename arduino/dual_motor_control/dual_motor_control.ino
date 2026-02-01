/*
 * Quad DC Motor Control via USB Serial
 * L293D Motor Shield 사용
 *
 * 단일 모터 프로토콜: <[모터ID][명령][속도][체크섬]>
 * 단일 응답: <[상태][모터ID][명령]>
 *
 * 멀티모터 프로토콜: <M[n][cmd1]...[cmdn][속도][체크섬]>
 * 멀티 응답: <O[n]M> (성공) 또는 <E[n]M> (실패)
 *
 * 다이렉트 프로토콜: <D[n][id1][cmd1][spd1]...[idn][cmdn][spdn][체크섬]>
 * 다이렉트 응답: <O[n]D> (성공) 또는 <E[n]D> (실패)
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

// 버퍼 (다이렉트 최대: D + n + 4*(id+cmd+spd) + checksum = 15)
char buffer[16];
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
    } else if (receiving && bufferIndex < 15) {
      buffer[bufferIndex++] = c;
    }
  }
}

void processCommand() {
  // 다이렉트 모터 명령 분기
  if (bufferIndex >= 6 && buffer[0] == 'D') {
    processDirectMotorCommand();
    return;
  }

  // 멀티모터 명령 분기
  if (bufferIndex >= 5 && buffer[0] == 'M') {
    processMultiMotorCommand();
    return;
  }

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

void processMultiMotorCommand() {
  // 버퍼: M(0) n(1) cmd1(2) ... cmdn(2+n-1) speed(2+n) checksum(3+n)
  char n = buffer[1];
  int motorCount = n - '0';

  if (motorCount < 2 || motorCount > 4) {
    sendMultiResponse('E', n);
    return;
  }

  // 예상 버퍼 길이: 1(M) + 1(n) + motorCount(cmds) + 1(speed) + 1(checksum)
  int expectedLen = 2 + motorCount + 2;
  if (bufferIndex != expectedLen) {
    sendMultiResponse('E', n);
    return;
  }

  // 체크섬 검증: M ^ n ^ cmd1 ^ ... ^ cmdn ^ speed
  uint8_t speed = (uint8_t)buffer[2 + motorCount];
  char checksum = buffer[3 + motorCount];

  char expected = 'M' ^ n;
  for (int i = 0; i < motorCount; i++) {
    expected ^= buffer[2 + i];
  }
  expected ^= speed;

  if (checksum != expected) {
    sendMultiResponse('E', n);
    return;
  }

  // 명령 검증
  for (int i = 0; i < motorCount; i++) {
    char cmd = buffer[2 + i];
    if (cmd != '0' && cmd != '1' && cmd != '2') {
      sendMultiResponse('E', n);
      return;
    }
  }

  // 모터 매핑 및 실행
  // n=2: cmd1→모터3(좌), cmd2→모터4(우)
  // n=4: cmd1→모터1, cmd2→모터2, cmd3→모터3, cmd4→모터4
  AF_DCMotor* motors2[] = { &motor3, &motor4 };
  AF_DCMotor* motors4[] = { &motor1, &motor2, &motor3, &motor4 };
  AF_DCMotor** motorList = (motorCount == 2) ? motors2 : motors4;

  for (int i = 0; i < motorCount; i++) {
    motorList[i]->setSpeed(speed);
    char cmd = buffer[2 + i];
    switch (cmd) {
      case '0': motorList[i]->run(RELEASE);  break;
      case '1': motorList[i]->run(FORWARD);  break;
      case '2': motorList[i]->run(BACKWARD); break;
    }
  }

  sendMultiResponse('O', n);
}

void processDirectMotorCommand() {
  // 버퍼: D(0) n(1) [id1(2) cmd1(3) spd1(4)] ... [idn cmdn spdn] checksum
  char n = buffer[1];
  int motorCount = n - '0';

  if (motorCount < 2 || motorCount > 4) {
    sendDirectResponse('E', n);
    return;
  }

  // 예상 버퍼 길이: 1(D) + 1(n) + motorCount*3(id+cmd+spd) + 1(checksum)
  int expectedLen = 2 + motorCount * 3 + 1;
  if (bufferIndex != expectedLen) {
    sendDirectResponse('E', n);
    return;
  }

  // 체크섬 검증: D ^ n ^ id1 ^ cmd1 ^ spd1 ^ ... ^ idn ^ cmdn ^ spdn
  char checksum = buffer[2 + motorCount * 3];

  char expected = 'D' ^ n;
  for (int i = 0; i < motorCount * 3; i++) {
    expected ^= buffer[2 + i];
  }

  if (checksum != expected) {
    sendDirectResponse('E', n);
    return;
  }

  // ID와 명령 검증
  for (int i = 0; i < motorCount; i++) {
    char id = buffer[2 + i * 3];
    char cmd = buffer[3 + i * 3];

    if (id < '1' || id > '4') {
      sendDirectResponse('E', n);
      return;
    }
    if (cmd != '0' && cmd != '1' && cmd != '2') {
      sendDirectResponse('E', n);
      return;
    }
  }

  // 모터 실행
  AF_DCMotor* motorPtrs[] = { &motor1, &motor2, &motor3, &motor4 };

  for (int i = 0; i < motorCount; i++) {
    char id = buffer[2 + i * 3];
    char cmd = buffer[3 + i * 3];
    uint8_t spd = (uint8_t)buffer[4 + i * 3];
    int idx = id - '1';

    motorPtrs[idx]->setSpeed(spd);
    switch (cmd) {
      case '0': motorPtrs[idx]->run(RELEASE);  break;
      case '1': motorPtrs[idx]->run(FORWARD);  break;
      case '2': motorPtrs[idx]->run(BACKWARD); break;
    }
  }

  sendDirectResponse('O', n);
}

void sendDirectResponse(char status, char n) {
  Serial.print(STX);
  Serial.print(status);
  Serial.print(n);
  Serial.print('D');
  Serial.println(ETX);
}

void sendMultiResponse(char status, char n) {
  Serial.print(STX);
  Serial.print(status);
  Serial.print(n);
  Serial.print('M');
  Serial.println(ETX);
}

void sendResponse(char status, char motorId, char command) {
  Serial.print(STX);
  Serial.print(status);
  Serial.print(motorId);
  Serial.print(command);
  Serial.println(ETX);
}
