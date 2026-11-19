package com.jaehoseol.canontyping.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 실시간 재생. noteOn()은 큐에 넣기만 하고 바로 리턴하므로 UI 스레드를 막지 않는다.
 * 실제 믹싱과 장치 출력은 전용 데몬 스레드가 한다. 소리가 없을 때 스레드는 쉬고,
 * 20초 동안 아무 입력이 없으면 오디오 장치도 닫는다.
 */
public final class AudioEngine implements AutoCloseable {
    public static final int SAMPLE_RATE = 44100;
    private static final int BLOCK = 512;                 // ≈ 11.6 ms
    private static final int LINE_BUFFER_BYTES = 4096;    // ≈ 46 ms (지연 vs. 끊김의 타협점)
    private static final long IDLE_CLOSE_MS = 20_000;

    private record NoteEvent(int midi, float gain) {}

    private final Mixer mixer = new Mixer();
    private final PianoSynth synth = new PianoSynth(SAMPLE_RATE);
    private final BlockingQueue<NoteEvent> pending = new LinkedBlockingQueue<>();
    private Thread thread;
    private volatile boolean closed;
    private volatile boolean unavailable;
    private volatile Consumer<String> log = msg -> System.err.println("[Canon Typing] " + msg);

    /** 상태·오류 메시지를 받을 곳 (IDE에서는 idea.log로 연결). */
    public void setLogger(Consumer<String> logger) { this.log = logger; }

    /** 첫 입력에 렌더링 때문에 버벅이지 않도록 음들을 미리 만들어 둔다 (백그라운드). */
    public void prewarm(Collection<Integer> midiNotes) {
        Thread t = new Thread(() -> {
            for (int n : midiNotes) synth.get(n);
        }, "canon-typing-prewarm");
        t.setDaemon(true);
        t.start();
    }

    public void noteOn(int midi, float gain) {
        if (closed || unavailable) return;
        pending.add(new NoteEvent(midi, gain));
        ensureThread();
    }

    private synchronized void ensureThread() {
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this::run, "canon-typing-audio");
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY - 1);
            thread.start();
        }
    }

    private void run() {
        SourceDataLine line = null;
        float[] block = new float[BLOCK];
        byte[] bytes = new byte[BLOCK * 2];
        try {
            while (!closed) {
                if (mixer.isIdle()) {
                    NoteEvent e = pending.poll(IDLE_CLOSE_MS, TimeUnit.MILLISECONDS);
                    if (e == null) {
                        // 한참 조용했다 → 장치를 놓아 주고 스레드를 끝낸다
                        synchronized (this) {
                            if (pending.isEmpty()) { thread = null; return; }
                        }
                        continue;
                    }
                    handle(e);
                }
                NoteEvent e;
                while ((e = pending.poll()) != null) handle(e);

                if (line == null) line = openLine();
                if (line == null) return;

                mixer.mix(block, BLOCK);
                for (int i = 0; i < BLOCK; i++) {
                    int s = Math.round(block[i] * 32767f * 0.9f);
                    bytes[2 * i] = (byte) s;
                    bytes[2 * i + 1] = (byte) (s >> 8);
                }
                line.write(bytes, 0, bytes.length); // 장치 버퍼가 차면 여기서 기다린다 = 박자 맞춘 페이스 조절
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ex) {
            log.accept("audio thread failed: " + ex);
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
            synchronized (this) {
                if (thread == Thread.currentThread()) thread = null;
            }
        }
    }

    private void handle(NoteEvent e) {
        mixer.noteOn(e.midi(), synth.get(e.midi()), e.gain());
    }

    private SourceDataLine openLine() {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            SourceDataLine l = (SourceDataLine) AudioSystem.getLine(info);
            l.open(fmt, LINE_BUFFER_BYTES);
            l.start();
            log.accept("audio output opened: " + l.getFormat() + ", buffer " + l.getBufferSize() + " bytes");
            return l;
        } catch (Exception ex) { // 오디오 장치 없음, 다른 앱이 독점 등
            unavailable = true;
            log.accept("cannot open audio output: " + ex);
            return null;
        }
    }

    @Override
    public void close() {
        closed = true;
        Thread t;
        synchronized (this) { t = thread; }
        if (t != null) t.interrupt();
    }
}
