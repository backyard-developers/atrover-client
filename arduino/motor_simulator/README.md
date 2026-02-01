# Motor Simulator (모터 시뮬레이터)

Adafruit 2.8" TFT LCD 쉴드를 사용하여 실제 모터 없이 모터 상태를 시각적으로 시뮬레이션합니다.

## 개요

Motor Simulator는 dual_motor_control과 100% 동일한 시리얼 프로토콜을 사용하여 모터 제어를 테스트할 수 있는 시뮬레이션 환경입니다.

- TFT LCD 화면에서 M1~M4 각 모터의 상태를 실시간으로 표시
- 실제 모터 드라이버 없이도 프로토콜 검증 및 테스트 가능
- dual_motor_control과 동일한 프로토콜 지원으로 호환성 확인

## 하드웨어

| 구성품 | 사양 |
|--------|------|
| 마이크로컨트롤러 | Arduino Uno 또는 Mega |
| LCD 디스플레이 | Adafruit 2.8" TFT LCD Shield |
| 통신 | USB 시리얼 케이블 (프로그래밍 및 통신용) |

## 핀 설정

Adafruit 2.8" TFT LCD Shield 기본 핀 설정:

| 신호 | 핀 |
|------|-----|
| LCD_CS | A3 |
| LCD_CD | A2 |
| LCD_WR | A1 |
| LCD_RD | A0 |
| LCD_RESET | A4 |

D4~D7, D8~D11: LCD 데이터 라인

## 라이브러리

다음 라이브러리가 필요합니다:

```
- Adafruit_GFX
- Adafruit_TFTLCD
```

Arduino IDE 라이브러리 매니저에서 설치하거나, [Adafruit GitHub](https://github.com/adafruit)에서 다운로드합니다.

## 프로토콜

Motor Simulator는 dual_motor_control과 **100% 동일한 프로토콜**을 지원합니다.

### 지원 프로토콜
- **단일 모터 명령**: `<[ID][CMD][SPEED][CHK]>`
- **멀티 모터 명령**: `<M[COUNT][CMD1]...[CMDN][SPEED][CHK]>`
- **Ready 신호**: `<O00>` (부팅 완료)

상세한 프로토콜 정의는 [../dual_motor_control/README.md](../dual_motor_control/README.md)를 참고하세요.

## 화면 표시

### 레이아웃

```
┌─────────────────────────────┐
│    MOTOR SIMULATOR          │
├─────────────────────────────┤
│ M1: [FWD] 255              │
│ M2: [STOP] 0               │
│ M3: [BWD] 128              │
│ M4: [FWD] 200              │
├─────────────────────────────┤
│ Last Command: <O11>         │
└─────────────────────────────┘
```

### 상태 표시

| 모터 | 표시 항목 |
|------|----------|
| M1~M4 | 방향(STOP/FWD/BWD), 속도(0-255) |
| 색상 | STOP(회색), FWD(초록색), BWD(빨간색) |
| 상태줄 | 마지막 수신한 명령 및 응답 상태 |

## 사용법

### 1. 하드웨어 조립

1. Adafruit 2.8" TFT LCD Shield를 Arduino 위에 장착
2. USB 케이블로 컴퓨터와 연결

### 2. 소프트웨어 설치

1. Arduino IDE 열기
2. 스케치 → 라이브러리 포함하기 → 라이브러리 관리
3. "Adafruit GFX" 및 "Adafruit TFTLCD" 설치
4. `motor_simulator.ino` 파일 열기
5. 보드 선택: Tools → Board → Arduino Uno (또는 Mega)
6. 포트 선택: Tools → Port → COM (또는 /dev/ttyUSB)
7. 업로드 클릭

### 3. 명령 전송

#### Serial Monitor 사용

1. Tools → Serial Monitor 열기
2. 보드레이트: **9600** 선택
3. 명령 입력 후 Send 클릭

예시:
```
<11 255 CF>    // M1 정회전 속도255
<22 128 B0>    // M2 역회전 속도128
<M2 1 1 200 XX> // 2모터 명령
```

#### Android 앱 사용

1. atrover-client 앱 실행
2. Remote Control 탭에서 모터 제어
3. LCD 화면에서 실시간으로 상태 확인

### 4. 상태 확인

LCD 화면에서:
- M1~M4 각 모터의 현재 상태 확인
- 마지막 수신 명령 및 응답 코드 확인
- 방향 색상으로 모터 상태 즉시 파악

## 응용 분야

- **프로토콜 검증**: 실제 모터 연결 전 시리얼 프로토콜 테스트
- **개발 단계**: PC 시뮬레이터 없이 임베디드 프로토콜 확인
- **교육**: Arduino 및 LCD 라이브러리 학습
- **호환성 확인**: dual_motor_control과의 동일성 검증

## 트러블슈팅

### LCD가 표시되지 않음

1. Shield의 핀 설정 확인 (A0~A4)
2. Arduino IDE의 Serial Monitor에서 디버그 메시지 확인
3. 라이브러리 재설치

### 명령을 받지 않음

1. 보드레이트 9600 확인
2. USB 케이블 재연결
3. Serial Monitor에서 Ready 신호(`<O00>`) 확인
4. 명령 형식 (STX, ETX, 체크섬) 재확인

### 응답이 없음

1. Arduino 리셋 버튼 누르기
2. IDE에서 "Verify" 로 코드 문법 확인
3. 포트 설정 재확인

## 참고

- [Adafruit Motor Shield V2](https://learn.adafruit.com/adafruit-motor-shield-v2-for-arduino)
- [Adafruit TFT LCD Shield](https://learn.adafruit.com/adafruit-2-8-tft-touch-shield)
- [dual_motor_control 프로토콜](../dual_motor_control/README.md)
