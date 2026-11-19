# Canon Typing — 타이핑할 때마다 파헬벨 캐논이 한 박자씩

PyCharm(및 다른 JetBrains IDE)에서 **키를 누를 때마다 파헬벨 '캐논 D장조'가 한 박자씩** 흘러나오는 플러그인.
단음이면 단음, 화음이면 화음 전체가 한 번에 울립니다. 타이핑 리듬이 곧 연주 템포가 됩니다.

- 스페이스·엔터·백스페이스·탭 포함, 글자 키를 누를 때마다 한 박자 (방향키·Shift·Esc·단축키는 제외)
- 한글 IME로 조합 중인 입력도 감지 (※ 아래 "확인 필요" 참고)
- 샘플 파일 없이 피아노 음색을 코드로 합성 (외부 파일·라이선스 문제 없음)
- 내장 편곡 대신 원하는 `.mid` 파일을 불러와 그 곡으로 연주 가능
- 내장 곡 65박 → 끝나면 처음부터 반복

## 실행해 보기

JDK 21이 필요합니다.

```bash
./gradlew runIde
```

처음 한 번은 PyCharm을 내려받습니다(약 1GB). 이미 설치된 PyCharm을 쓰려면 `gradle.properties`의
`pycharmPath=` 주석을 풀고 경로를 지정하세요 (다운로드 생략).

샌드박스 PyCharm이 열리면 아무 파일에서 타이핑해 보세요.

## 설치용 파일 만들기

```bash
./gradlew buildPlugin
```

`build/distributions/canon-typing-0.2.0.zip` 이 생깁니다.
PyCharm → Settings → Plugins → ⚙ → **Install Plugin from Disk...** 로 설치하세요.

## 메뉴 (Tools > Canon Typing)

| 메뉴 | 동작 |
|---|---|
| Enable Canon Typing | 켜기/끄기 (체크 메뉴, 재시작해도 유지) |
| Restart From Beginning | 곡을 첫 박자로 되돌림 |
| Load MIDI Score... | `.mid` 파일을 악보로 사용 |
| Use Built-in Canon in D | 내장 곡으로 되돌림 |

### Auto-Tempo (타자가 빠르면 정박으로)

기본은 **꺼짐**이고, 꺼져 있으면 키 한 번에 한 박자입니다. 켜면(Tools > Canon Typing > Auto-Tempo):

- 최근 2초의 평균 타수가 기준(기본 **1키/초**)을 넘으면, 키와 무관하게 정해진 BPM(기본 80)으로 박자를 이어 갑니다.
  잠기는 순간의 키는 평소처럼 한 박자를 연주하고, 그다음 박자부터 메트로놈이 이어받습니다. 잠긴 동안의 키는 소리를 내지 않습니다.
- 평균 타수가 기준의 절반 이하로 떨어지거나 타이핑을 멈추면 다시 키 한 번에 한 박자로 돌아갑니다. 곡은 끊기지 않고 이어집니다.
  (기준 근처에서 모드가 깜빡이지 않도록, 들어갈 때와 나올 때 기준을 다르게 둡니다.)
- 기준 타수(0.2~10키/초)와 BPM(30~240)은 Tools > Canon Typing > Auto-Tempo Settings... 에서 바꿉니다.

### MIDI 파일을 쓰면

거의 동시에 시작하는 음들(박자의 1/12 이내)은 한 박자(화음)로 묶이고, 시간순으로 한 박자씩 키 한 번에 대응됩니다.
드럼 채널(10번)은 무시합니다. **정확한 캐논 악보를 쓰고 싶다면 이 방법이 가장 좋습니다.**
(내장 곡은 코드 진행과 선율 윤곽을 바탕으로 만든 *간략 편곡*이지, 정식 악보를 옮긴 것이 아닙니다.)

## Marketplace에 올리기

1. **JDK 21로 빌드합니다.** (자바 25 등으로 빌드한 파일은 자바 21 기반 IDE에서 로드되지 않아서, 빌드가 막아 줍니다.)
   ```bash
   ./gradlew buildPlugin          # -PjavaVersion 없이, JDK 21로
   ```
2. 올릴 파일은 소스가 아니라 **`build/distributions/canon-typing-0.2.0.zip`** 입니다.
3. (선택) 올리기 전 호환성 검사: `./gradlew verifyPlugin` — 여러 IDE 버전을 내려받아 검사하므로 시간이 걸립니다.
4. plugins.jetbrains.com 에 로그인 → 플러그인 추가 → zip 업로드 → 판매자 프로필, 라이선스(MIT), 태그 등을 입력 → 검수 대기.
5. 두 번째 업로드부터는 버전을 올려서(`build.gradle.kts`의 `version`) 같은 방식으로 올리거나,
   Marketplace에서 발급한 토큰으로 `PUBLISH_TOKEN=... ./gradlew publishPlugin` 을 쓸 수 있습니다.

내 PC에 설치해 쓰기만 할 때는 JBR 25로 `./gradlew buildPlugin -PjavaVersion=25 -PallowNewBytecode` 처럼 빌드해도 됩니다.

## 내장 곡 고치기

`src/main/java/com/jaehoseol/canontyping/score/CanonInD.java` 의 표를 수정하면 됩니다.
음은 `"F#5"`, `"D4 F#4 A4"` 처럼 이름으로 적습니다 (C4 = 가운데 도).

## IDE 없이 소리만 확인하기

```bash
mkdir -p out
javac -d out $(find src/main/java/com/jaehoseol/canontyping/score src/main/java/com/jaehoseol/canontyping/audio -name '*.java') dev-tools/DemoWav.java
java -cp out DemoWav canon-demo.wav 100        # 100 BPM으로 65박 전체를 WAV로 저장
java -cp out DemoWav out.wav 90 my-score.mid   # MIDI 파일도 가능
```

## 구조

```
score/   NoteName, Score, CanonInD(내장 곡), MidiScoreLoader      ← IDE 비의존
audio/   PianoSynth(음색 합성), Mixer, AudioEngine(실시간 출력), CanonPlayer(박자 진행)  ← IDE 비의존
ide/     TypingEditorListener(키 감지), CanonTypingService, 메뉴 액션들           ← IntelliJ 플랫폼 API
```

키 입력 경로: `에디터 키 → TypingEditorListener → CanonTypingService.onKey() → CanonPlayer.next()
→ AudioEngine(큐) → 전용 오디오 스레드가 믹싱해 출력`. UI 스레드에서는 큐에 넣기만 하므로 타이핑이 느려지지 않습니다.

## 확인 필요 / 알려진 한계

- **IDE 연동 코드는 실제 IntelliJ 플랫폼에 대해 컴파일·실행해 보지 못했습니다.** 개발 환경에서 JetBrains 서버에
  접근할 수 없어서, 소리·악보·MIDI·키 필터 로직만 따로 검증했습니다. 처음 `./gradlew runIde` 할 때 컴파일 오류나
  `since-build` 관련 경고가 나면 알려 주세요.
- 한글 IME 조합 중 입력은 `InputMethodListener`로 잡습니다. IDE 버전·OS에 따라 이벤트 방식이 달라서
  한글 입력 시 소리가 안 나거나 두 번 나면 `ide/TypingEditorListener.java`를 손봐야 할 수 있습니다.
- 피아노 음은 합성음이라 실제 그랜드 피아노 샘플보다 가볍게 들립니다. 음정·감쇠는 수치로 검증했지만 귀로 들은 평가는 아닙니다.
  (다음 단계로 SoundFont(.sf2) 지원을 붙이면 음질이 크게 좋아집니다.)
- 지연 시간은 오디오 버퍼(약 46 ms) 정도입니다.
- 플러그인 ID는 `com.jaehoseol.canontyping` 입니다. Marketplace에 한 번 올리면 ID는 바꿀 수 없습니다.
