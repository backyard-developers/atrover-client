/*
 * Arduino LED Control via USB Serial
 *
 * Android 앱에서 USB 시리얼 통신으로 LED를 제어합니다.
 * - '1' 수신: LED ON
 * - '0' 수신: LED OFF
 */

const int LED_PIN = 13;

void setup() {
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  Serial.begin(9600);
}

void loop() {
  if (Serial.available() > 0) {
    char command = Serial.read();

    if (command == '1') {
      digitalWrite(LED_PIN, HIGH);
    } else if (command == '0') {
      digitalWrite(LED_PIN, LOW);
    }
  }
}
