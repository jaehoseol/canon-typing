package com.jaehoseol.canontyping.ide;

import com.jaehoseol.canontyping.audio.AudioEngine;
import com.jaehoseol.canontyping.audio.CanonPlayer;
import com.jaehoseol.canontyping.audio.TempoLock;
import com.jaehoseol.canontyping.score.CanonInD;
import com.jaehoseol.canontyping.score.MidiScoreLoader;
import com.jaehoseol.canontyping.score.Score;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * 플러그인의 중심. 에디터 키 입력(TypingEditorListener)을 받아 악보를 한 박자씩 연주한다.
 * 설정(켜짐/꺼짐, 사용자 MIDI 경로)은 PropertiesComponent에 저장되어 IDE를 다시 켜도 유지된다.
 */
@Service(Service.Level.APP)
public final class CanonTypingService implements Disposable {
    private static final String KEY_ENABLED = "canontyping.enabled";
    private static final String KEY_MIDI = "canontyping.midiPath";
    private static final String KEY_EVERYWHERE = "canontyping.everywhere";
    private static final String KEY_TEMPO_ON = "canontyping.autoTempo.enabled";
    private static final String KEY_TEMPO_THRESHOLD = "canontyping.autoTempo.keysPerSecond";
    private static final String KEY_TEMPO_BPM = "canontyping.autoTempo.bpm";
    private static final long IME_DEDUP_NANOS = 40_000_000L;

    private static final Logger LOG = Logger.getInstance(CanonTypingService.class);
    private static final long DEDUP_NANOS = 20_000_000L; // 같은 키가 두 경로로 들어와도 한 번만 (20 ms)

    private final AudioEngine engine = new AudioEngine();
    private final CanonPlayer player;
    private final TempoLock tempo;
    private volatile boolean enabled;
    private volatile boolean everywhere;
    private volatile long lastAwtKeyNanos = Long.MIN_VALUE / 2;
    private long lastKeyNanos = Long.MIN_VALUE / 2;
    private int loggedKeys;

    /**
     * IDE 전체의 키 입력을 보는 전역 리스너. 터미널(Editor가 아님), Copilot 같은 다른 플러그인의 채팅 창,
     * 검색 창, 대화상자 등 에디터 리스너가 닿지 않는 곳을 담당한다.
     *  - everywhere=true : 어떤 컴포넌트든 글자 키면 센다
     *  - everywhere=false: 터미널에서 온 키만 센다 (에디터는 TypingEditorListener가 담당)
     * 같은 키를 여러 경로가 세도 CanonTypingService.onKey()의 20ms 중복 제거가 한 번으로 만든다.
     */
    private final AWTEventListener globalKeys = ev -> {
        if (!enabled) return;
        if (ev instanceof KeyEvent ke) {
            if (ke.getID() != KeyEvent.KEY_PRESSED) return;
            if ((everywhere || isTerminalComponent(ke.getSource())) && TypingEditorListener.isTypingKey(ke)) {
                lastAwtKeyNanos = System.nanoTime();
                onKey(everywhere ? "AWT" : "Terminal");
            }
        } else if (everywhere && ev instanceof InputMethodEvent ime
                && ime.getID() == InputMethodEvent.INPUT_METHOD_TEXT_CHANGED) {
            // 한글 IME 조합: KeyEvent 없이 InputMethodEvent만 오는 경우
            long now = System.nanoTime();
            if (now - lastAwtKeyNanos >= IME_DEDUP_NANOS) {
                lastAwtKeyNanos = now;
                onKey("AWT/IME");
            }
        }
    };

    static boolean isTerminalComponent(Object source) {
        if (!(source instanceof Component)) return false;
        for (Class<?> c = source.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().toLowerCase(Locale.ROOT).contains("terminal")) return true;
        }
        return false;
    }

    public static CanonTypingService getInstance() {
        return ApplicationManager.getApplication().getService(CanonTypingService.class);
    }

    public CanonTypingService() {
        PropertiesComponent props = PropertiesComponent.getInstance();
        enabled = props.getBoolean(KEY_ENABLED, true);
        everywhere = props.getBoolean(KEY_EVERYWHERE, true);
        player = new CanonPlayer(initialScore(props.getValue(KEY_MIDI)),
                (midi, gain) -> engine.noteOn(midi, gain));
        tempo = new TempoLock(player::next);
        tempo.setEnabled(props.getBoolean(KEY_TEMPO_ON, false)); // 기본은 꺼짐 = 키 한 번에 한 박자
        tempo.setThreshold(parseOr(props.getValue(KEY_TEMPO_THRESHOLD), 1.0));
        tempo.setBpm(parseOr(props.getValue(KEY_TEMPO_BPM), 80.0));
        engine.setLogger(msg -> LOG.info("Canon Typing: " + msg));
        engine.prewarm(player.score().allNotes());
        Toolkit.getDefaultToolkit().addAWTEventListener(globalKeys,
                AWTEvent.KEY_EVENT_MASK | AWTEvent.INPUT_METHOD_EVENT_MASK);
        LOG.info("Canon Typing: started, enabled=" + enabled + ", everywhere=" + everywhere + ", autoTempo=" + tempo.isEnabled() + ", score=\"" + player.score().name() + "\"");
    }

    private static double parseOr(String text, double fallback) {
        try {
            return text == null ? fallback : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Score initialScore(String savedMidiPath) {
        if (savedMidiPath != null && !savedMidiPath.isEmpty()) {
            try {
                return MidiScoreLoader.load(new File(savedMidiPath));
            } catch (IOException | RuntimeException ignored) {
                // 파일이 사라졌거나 망가졌으면 기본 곡으로
            }
        }
        return CanonInD.create();
    }

    /** 키가 눌릴 때마다 호출 (UI 스레드). 큐에 넣기만 하므로 빠르게 끝난다. */
    public void onKey(String source) {
        if (!enabled) return;
        long now = System.nanoTime();
        synchronized (this) {
            if (now - lastKeyNanos < DEDUP_NANOS) return;
            lastKeyNanos = now;
            if (loggedKeys < 5) { // 키 감지가 되는지 확인할 수 있도록 처음 몇 번만 기록
                loggedKeys++;
                LOG.info("Canon Typing: key detected via " + source + " (#" + loggedKeys + ")");
            }
        }
        // 오토 템포가 꺼져 있으면 항상 true → 키 한 번에 한 박자. 켜져 있고 타자가 빠르면 false → 메트로놈이 박자를 맡는다.
        if (tempo.onKey()) player.next();
    }

    public boolean isAutoTempo() { return tempo.isEnabled(); }

    public void setAutoTempo(boolean value) {
        tempo.setEnabled(value);
        PropertiesComponent.getInstance().setValue(KEY_TEMPO_ON, value, false);
    }

    public double getTempoThreshold() { return tempo.threshold(); }

    public double getTempoBpm() { return tempo.bpm(); }

    public void setTempoParams(double keysPerSecond, double bpm) {
        tempo.setThreshold(keysPerSecond);
        tempo.setBpm(bpm);
        PropertiesComponent props = PropertiesComponent.getInstance();
        props.setValue(KEY_TEMPO_THRESHOLD, String.valueOf(tempo.threshold()));
        props.setValue(KEY_TEMPO_BPM, String.valueOf(tempo.bpm()));
    }

    /** Tools > Canon Typing > Play Test Sound : 키 감지와 무관하게 소리 출력만 확인한다. */
    public void playTestChord() {
        LOG.info("Canon Typing: test chord requested");
        for (int note : new int[]{62, 66, 69, 74}) engine.noteOn(note, 0.35f);
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) {
        enabled = value;
        PropertiesComponent.getInstance().setValue(KEY_ENABLED, value, true);
    }

    public boolean isEverywhere() { return everywhere; }

    public void setEverywhere(boolean value) {
        everywhere = value;
        PropertiesComponent.getInstance().setValue(KEY_EVERYWHERE, value, true);
    }

    public void restart() { player.restart(); }

    public String currentScoreName() { return player.score().name(); }

    /** MIDI 파일을 악보로 불러온다. 실패하면 IOException (기존 악보는 그대로). */
    public Score loadMidi(File file) throws IOException {
        Score s = MidiScoreLoader.load(file);
        player.setScore(s);
        engine.prewarm(s.allNotes());
        PropertiesComponent.getInstance().setValue(KEY_MIDI, file.getAbsolutePath());
        return s;
    }

    public void useBuiltIn() {
        Score s = CanonInD.create();
        player.setScore(s);
        engine.prewarm(s.allNotes());
        PropertiesComponent.getInstance().unsetValue(KEY_MIDI);
    }

    @Override
    public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(globalKeys);
        tempo.close();
        engine.close();
    }
}
