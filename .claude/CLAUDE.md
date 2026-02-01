# Arduino-Android Communication POC

## 작업 규칙

- **git push 금지**: 사용자의 명시적 허락 없이 절대 push하지 말 것. 커밋 후 "푸시할까요?" 확인 필수.

## 빌드 설정

터미널에서 빌드 시 JAVA_HOME 설정 필요:
```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

## 프로젝트 구조

### Android 앱
- `app/src/main/java/com/example/arduinousbpoc/`
  - `MainActivity.kt` - wiring only (네트워크+USB 연결)
  - `usb/UsbMotorController.kt` - USB 시리얼 + 모터 제어
  - `screen/MainScreen.kt` - 탭 네비게이션 (모터, 스트리밍, 원격)
  - `screen/MotorControlScreen.kt` - 모터 제어 UI
  - `screen/CameraStreamScreen.kt` - WebRTC 스트리밍
  - `screen/RemoteControlScreen.kt` - 원격 제어 (Backend 연동)

### Node.js 서버
- `server/` - WebRTC 시그널링 서버
  ```bash
  cd server && npm install && npm start
  ```

## 주요 라이브러리
- USB Serial: `com.github.mik3y:usb-serial-for-android`
- CameraX: `androidx.camera:camera-*`
- WebRTC: `io.getstream:stream-webrtc-android`
- WebSocket: `com.squareup.okhttp3:okhttp`
