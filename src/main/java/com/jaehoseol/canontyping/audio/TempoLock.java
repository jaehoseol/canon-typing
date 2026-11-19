package com.jaehoseol.canontyping.audio;

import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * "오토 템포": 타자가 일정 속도를 넘으면 키 입력과 무관하게 정해진 BPM으로 박자를 이어 간다.
 *
 * <pre>
 *  옵션 꺼짐   : onKey()는 항상 true → 키 한 번에 한 박자 (기존 동작과 완전히 동일)
 *  옵션 켜짐   :
 *    - 최근 {window} 동안의 평균 타수가 threshold(키/초)를 넘으면 "잠금":
 *      그 키는 평소처럼 한 박자를 연주하고, 이후로는 메트로놈이 BPM 간격으로 다음 박자를 연주.
 *      잠긴 동안의 키 입력은 소리를 내지 않는다 (타수와 무관하게 정박).
 *    - 평균 타수가 threshold의 절반 이하로 떨어지면(또는 타이핑을 멈추면) 잠금 해제:
 *      다시 키 한 번에 한 박자로 돌아간다. 곡은 끊기지 않고 이어진다.
 * </pre>
 * 잠글 때와 풀 때 기준을 다르게 둔(히스테리시스) 이유는 기준 근처에서 모드가 깜빡이지 않게 하기 위해서다.
 * IDE에 의존하지 않는 순수 클래스.
 */
public final class TempoLock implements AutoCloseable {
    public static final long DEFAULT_WINDOW_NANOS = 2_000_000_000L; // 최근 2초의 평균 타수

    private final Runnable beat;
    private final LongSupplier clock;
    private final long windowNanos;
    private final ArrayDeque<Long> recent = new ArrayDeque<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private boolean enabled;
    private boolean locked;
    private double threshold = 1.0; // 키/초
    private double bpm = 80.0;

    public TempoLock(Runnable beat) {
        this(beat, System::nanoTime, DEFAULT_WINDOW_NANOS);
    }

    public TempoLock(Runnable beat, LongSupplier nanoClock, long windowNanos) {
        this.beat = beat;
        this.clock = nanoClock;
        this.windowNanos = windowNanos;
    }

    public synchronized void setEnabled(boolean value) {
        enabled = value;
        if (!value) stopLocked();
    }

    public synchronized boolean isEnabled() { return enabled; }

    public synchronized void setThreshold(double keysPerSecond) {
        threshold = Math.max(0.1, keysPerSecond);
    }

    public synchronized double threshold() { return threshold; }

    public synchronized void setBpm(double value) {
        bpm = Math.max(20.0, Math.min(300.0, value));
        if (locked) { // 템포를 바꾸면 새 간격으로 다시 맞춘다
            cancelTask();
            scheduleBeats();
        }
    }

    public synchronized double bpm() { return bpm; }

    public synchronized boolean isLocked() { return locked; }

    /**
     * 키가 눌릴 때마다 호출한다.
     * @return true면 호출한 쪽이 지금 한 박자를 연주해야 한다(키 입력 모드).
     *         false면 메트로놈이 박자를 맡고 있으므로 이 키는 소리를 내지 않는다.
     */
    public synchronized boolean onKey() {
        if (!enabled) return true;
        long now = clock.getAsLong();
        recent.addLast(now);
        prune(now);
        if (locked) return false;
        if (recent.size() > threshold * seconds(windowNanos)) {
            locked = true;
            scheduleBeats();
        }
        return true; // 잠기는 순간의 키도 평소처럼 한 박자를 연주하고, 다음 박자부터 메트로놈이 이어받는다
    }

    private void tick() {
        boolean play;
        synchronized (this) {
            if (!locked) return;
            prune(clock.getAsLong());
            if (!enabled || recent.size() <= threshold * 0.5 * seconds(windowNanos)) {
                stopLocked();
                play = false;
            } else {
                play = true;
            }
        }
        if (play) {
            try {
                beat.run();
            } catch (RuntimeException ignored) {
                // 한 번의 오류로 메트로놈이 죽지 않게
            }
        }
    }

    private void scheduleBeats() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "canon-typing-tempo");
                t.setDaemon(true);
                return t;
            });
        }
        long period = (long) (60_000_000_000.0 / bpm);
        task = scheduler.scheduleAtFixedRate(this::tick, period, period, TimeUnit.NANOSECONDS);
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void stopLocked() {
        locked = false;
        cancelTask();
    }

    private void prune(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst() > windowNanos) recent.pollFirst();
    }

    private static double seconds(long nanos) { return nanos / 1e9; }

    @Override
    public synchronized void close() {
        stopLocked();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
