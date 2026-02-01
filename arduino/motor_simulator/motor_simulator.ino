/**
 * Motor Simulator with TFT LCD Display
 *
 * dual_motor_control.ino와 동일한 시리얼 프로토콜을 사용하지만,
 * 실제 모터 대신 TFT LCD에 모터 상태를 시뮬레이션 표시
 *
 * 하드웨어:
 * - Arduino Uno/Mega
 * - 2.4" TFT LCD Shield (ILI9341 controller)
 *   LCD_CS   A3
 *   LCD_CD   A2
 *   LCD_WR   A1
 *   LCD_RD   A0
 *   LCD_RESET A4
 */

#include <Adafruit_GFX.h>
#include <Adafruit_TFTLCD.h>

// TFT 핀 정의
#define LCD_CS A3
#define LCD_CD A2
#define LCD_WR A1
#define LCD_RD A0
#define LCD_RESET A4

// 색상 정의
#define BLACK   0x0000
#define WHITE   0xFFFF
#define RED     0xF800
#define GREEN   0x07E0
#define YELLOW  0xFFE0
#define GRAY    0x7BEF
#define CYAN    0x07FF

// TFT 객체 생성
Adafruit_TFTLCD tft(LCD_CS, LCD_CD, LCD_WR, LCD_RD, LCD_RESET);

// 시리얼 프로토콜 정의
const char STX = '<';
const char ETX = '>';
const int BUFFER_SIZE = 8;

char buffer[BUFFER_SIZE];
int bufferIndex = 0;
bool receiving = false;

// 모터 상태 구조체
struct MotorState {
  uint8_t command;  // 0=STOP, 1=FORWARD, 2=BACKWARD
  uint8_t speed;    // 0-255
};

MotorState motors[4] = {{0, 0}, {0, 0}, {0, 0}, {0, 0}};

// 레이아웃 상수 (320x240, landscape)
#define TITLE_Y 10
#define MOTOR_START_Y 50
#define MOTOR_ROW_HEIGHT 35
#define STATUS_Y 200

// 마지막 명령 상태
String lastStatus = "READY";

/**
 * 응답 전송 함수 (단일 모터)
 */
void sendResponse(char status, char motorID, char command) {
  Serial.print(STX);
  Serial.print(status);
  Serial.print(motorID);
  Serial.print(command);
  Serial.println(ETX);
}

/**
 * 응답 전송 함수 (멀티 모터)
 */
void sendMultiResponse(char status, char count) {
  Serial.print(STX);
  Serial.print(status);
  Serial.print(count);
  Serial.print('M');
  Serial.println(ETX);
}

/**
 * 특정 모터 상태 업데이트 및 화면 갱신
 */
void updateMotor(int motorIndex, uint8_t cmd, uint8_t speed) {
  motors[motorIndex].command = cmd;
  motors[motorIndex].speed = speed;
  drawMotor(motorIndex);
}

/**
 * 특정 모터 행 화면 그리기
 */
void drawMotor(int index) {
  int y = MOTOR_START_Y + (index * MOTOR_ROW_HEIGHT);

  // 모터 번호 (M1-M4) - 변경 안 함
  // tft.setCursor(10, y);
  // tft.setTextColor(WHITE);
  // tft.setTextSize(2);
  // tft.print("M");
  // tft.print(index + 1);
  // tft.print(":");

  // 방향 영역 (배경색 지정으로 자동 덮어쓰기)
  tft.setCursor(60, y);
  tft.setTextSize(2);

  const char* dirText;
  uint16_t dirColor;

  switch(motors[index].command) {
    case 0:  // STOP
      dirText = "STOP";
      dirColor = GRAY;
      break;
    case 1:  // FORWARD
      dirText = "FWD ";
      dirColor = GREEN;
      break;
    case 2:  // BACKWARD
      dirText = "BWD ";
      dirColor = RED;
      break;
    default:
      dirText = "??? ";
      dirColor = WHITE;
  }

  tft.setTextColor(dirColor, BLACK);
  tft.print(dirText);

  // 속도 영역 (배경색 지정으로 자동 덮어쓰기)
  tft.setCursor(200, y);
  tft.setTextColor(YELLOW, BLACK);
  tft.print("SPD:");

  // 3자리 숫자로 포맷 (예: 005, 128, 255)
  if (motors[index].speed < 100) tft.print('0');
  if (motors[index].speed < 10) tft.print('0');
  tft.print(motors[index].speed);
}

/**
 * 모든 모터 초기 화면 그리기
 */
void drawAllMotors() {
  for (int i = 0; i < 4; i++) {
    int y = MOTOR_START_Y + (i * MOTOR_ROW_HEIGHT);

    // 모터 번호
    tft.setCursor(10, y);
    tft.setTextColor(WHITE);
    tft.setTextSize(2);
    tft.print("M");
    tft.print(i + 1);
    tft.print(":");

    drawMotor(i);
  }
}

/**
 * 상태 메시지 표시
 */
void drawStatus(String status) {
  lastStatus = status;
  tft.fillRect(0, STATUS_Y, 320, 20, BLACK);
  tft.setCursor(10, STATUS_Y);
  tft.setTextColor(CYAN, BLACK);
  tft.setTextSize(2);
  tft.print("Last: ");
  tft.print(status);
}

/**
 * 단일 모터 명령 처리
 */
void processCommand() {
  if (bufferIndex != 4) {
    sendResponse('E', '0', '0'); // 길이 에러
    drawStatus("ERR:LEN");
    return;
  }

  char motorID = buffer[0];
  char command = buffer[1];
  uint8_t speed = (uint8_t)buffer[2];
  uint8_t checksum = (uint8_t)buffer[3];

  // 체크섬 검증
  uint8_t calculatedChecksum = motorID ^ command ^ speed;
  if (checksum != calculatedChecksum) {
    sendResponse('E', motorID, command);
    drawStatus("ERR:CHK");
    return;
  }

  // 모터 ID 검증 (1-4)
  if (motorID < '1' || motorID > '4') {
    sendResponse('M', motorID, command);
    drawStatus("ERR:ID");
    return;
  }

  // 명령 검증 (0=STOP, 1=FORWARD, 2=BACKWARD)
  if (command < '0' || command > '2') {
    sendResponse('X', motorID, command);
    drawStatus("ERR:CMD");
    return;
  }

  // 모터 상태 업데이트 (motorID는 '1'-'4'이므로 인덱스는 0-3)
  int motorIndex = motorID - '1';
  uint8_t cmd = command - '0';

  updateMotor(motorIndex, cmd, speed);

  // 성공 응답
  sendResponse('O', motorID, command);
  String statusMsg = "OK:M";
  statusMsg += motorID;
  drawStatus(statusMsg);
}

/**
 * 멀티 모터 명령 처리
 */
void processMultiMotorCommand() {
  if (bufferIndex < 5) { // 최소: M + n + cmd1 + speed + checksum
    sendMultiResponse('E', '0');
    drawStatus("ERR:MLEN");
    return;
  }

  char nChar = buffer[1];
  if (nChar != '2' && nChar != '4') {
    sendMultiResponse('E', nChar);
    drawStatus("ERR:MCNT");
    return;
  }

  int n = nChar - '0';

  // 예상 길이 검증: 'M' + n + (n개 commands) + speed + checksum = 2 + n + 2
  if (bufferIndex != (2 + n + 2)) {
    sendMultiResponse('E', nChar);
    drawStatus("ERR:MLEN");
    return;
  }

  // 명령들 추출
  char commands[4];
  for (int i = 0; i < n; i++) {
    commands[i] = buffer[2 + i];
  }

  uint8_t speed = (uint8_t)buffer[2 + n];
  uint8_t checksum = (uint8_t)buffer[3 + n];

  // 체크섬 계산
  uint8_t calculatedChecksum = 'M' ^ nChar;
  for (int i = 0; i < n; i++) {
    calculatedChecksum ^= commands[i];
  }
  calculatedChecksum ^= speed;

  if (checksum != calculatedChecksum) {
    sendMultiResponse('E', nChar);
    drawStatus("ERR:MCHK");
    return;
  }

  // 명령 검증
  for (int i = 0; i < n; i++) {
    if (commands[i] < '0' || commands[i] > '2') {
      sendMultiResponse('E', nChar);
      drawStatus("ERR:MCMD");
      return;
    }
  }

  // n=2: M3(왼쪽), M4(오른쪽)
  // n=4: M1, M2, M3, M4
  if (n == 2) {
    updateMotor(2, commands[0] - '0', speed); // M3
    updateMotor(3, commands[1] - '0', speed); // M4
  } else {
    for (int i = 0; i < 4; i++) {
      updateMotor(i, commands[i] - '0', speed);
    }
  }

  // 성공 응답
  sendMultiResponse('O', nChar);
  String statusMsg = "OK:M";
  statusMsg += nChar;
  drawStatus(statusMsg);
}

/**
 * 초기 화면 그리기
 */
void drawInitialScreen() {
  tft.fillScreen(BLACK);

  // 타이틀
  tft.setCursor(10, TITLE_Y);
  tft.setTextColor(WHITE);
  tft.setTextSize(2);
  tft.print("MOTOR SIMULATOR");

  // 구분선
  tft.drawLine(0, 35, 320, 35, WHITE);
  tft.drawLine(0, 185, 320, 185, WHITE);

  // 모든 모터 초기 상태 그리기
  drawAllMotors();

  // 초기 상태
  drawStatus("READY");
}

void setup() {
  // 시리얼 초기화
  Serial.begin(9600);

  // TFT 초기화
  uint16_t identifier = tft.readID();
  if (identifier == 0 || identifier == 0xFFFF) {
    identifier = 0x9341; // ILI9341 기본값
  }

  tft.begin(identifier);
  tft.setRotation(1); // 가로 모드 (320x240)

  // 초기 화면 그리기
  drawInitialScreen();

  // Ready 신호 전송
  Serial.println("<O00>");
}

void loop() {
  while (Serial.available() > 0) {
    char inChar = Serial.read();

    if (inChar == STX) {
      receiving = true;
      bufferIndex = 0;
      memset(buffer, 0, BUFFER_SIZE);
    }
    else if (inChar == ETX) {
      if (receiving) {
        receiving = false;

        if (bufferIndex > 0) {
          if (buffer[0] == 'M') {
            // 멀티 모터 명령
            processMultiMotorCommand();
          } else {
            // 단일 모터 명령
            processCommand();
          }
        }
      }
    }
    else if (receiving) {
      if (bufferIndex < BUFFER_SIZE) {
        buffer[bufferIndex++] = inChar;
      } else {
        // 버퍼 오버플로우
        receiving = false;
        sendResponse('E', '0', '0');
        drawStatus("ERR:OVFL");
      }
    }
  }
}
