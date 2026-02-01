# Arduino Sketches

## led_control
USB 시리얼로 LED ON/OFF 제어. `'1'` → ON, `'0'` → OFF.

## dual_motor_control
L293D Motor Shield 기반 4채널 DC 모터 제어. 세 가지 프로토콜 지원:

| 프로토콜 | 형식 | 응답 | 설명 |
|----------|------|------|------|
| 단일 | `<[id][cmd][spd][chk]>` | `<O[id][cmd]>` | 모터 1개 제어 |
| 멀티(M) | `<M[n][cmd1]..[cmdn][spd][chk]>` | `<O[n]M>` | n개 동일속도 (n=2: M3,M4 고정) |
| 다이렉트(D) | `<D[n][id1][cmd1][spd1]..[idn][cmdn][spdn][chk]>` | `<O[n]D>` | n개 개별ID+개별속도 |

- `cmd`: `'0'`=STOP, `'1'`=FWD, `'2'`=BWD
- `spd`: 0~255
- `chk`: XOR checksum

## motor_simulator
`dual_motor_control`과 동일한 프로토콜을 지원하되, 실제 모터 대신 2.4" TFT LCD (ILI9341)에 모터 상태를 시각적으로 표시. 하드웨어 없이 프로토콜 테스트 가능.
