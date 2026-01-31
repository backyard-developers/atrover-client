# Arduino-Android Communication POC

## 빌드 설정

터미널에서 빌드 시 JAVA_HOME 설정 필요:
```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

## 프로젝트 구조

### Android 앱
- `app/src/main/java/com/example/arduinousbpoc/`
  - `MainActivity.kt` - 탭 네비게이션
  - `screen/LedControlScreen.kt` - USB 시리얼 LED 제어
  - `screen/CameraPreviewScreen.kt` - 카메라 프리뷰
  - `screen/CameraStreamScreen.kt` - WebRTC 스트리밍

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
