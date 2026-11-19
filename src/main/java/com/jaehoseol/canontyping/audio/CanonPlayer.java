package com.jaehoseol.canontyping.audio;

import com.jaehoseol.canontyping.score.Score;

import java.util.function.BiConsumer;

/**
 * 악보와 "지금 몇 번째 박자인가"를 들고 있다가, next()가 불릴 때마다 다음 스텝을 연주한다.
 * IDE에 의존하지 않는 순수 클래스.
 */
public final class CanonPlayer {
    private final BiConsumer<Integer, Float> sink; // (midi, gain)
    private Score score;
    private int index;

    public CanonPlayer(Score score, BiConsumer<Integer, Float> sink) {
        this.score = score;
        this.sink = sink;
    }

    public synchronized void setScore(Score s) {
        this.score = s;
        this.index = 0;
    }

    public synchronized Score score() { return score; }

    public synchronized void restart() { index = 0; }

    public synchronized int index() { return index; }

    /** 현재 박자를 연주하고 다음 박자로 넘어간다. 끝에 다다르면 처음으로 돌아간다. */
    public synchronized void next() {
        int[] notes = score.step(index);
        index = (index + 1) % score.size();
        play(notes);
    }

    /**
     * 화음의 모든 음을 낸다. 가장 높은 음(선율)은 또렷하게, 가장 낮은 음(베이스)은 든든하게,
     * 가운데 음들은 약간 작게. 음이 많을수록 전체 음량이 터지지 않도록 줄인다.
     */
    public static float[] gains(int[] notes) {
        int n = notes.length;
        float[] g = new float[n];
        if (n == 0) return g;
        int lo = 0, hi = 0;
        for (int i = 1; i < n; i++) {
            if (notes[i] < notes[lo]) lo = i;
            if (notes[i] > notes[hi]) hi = i;
        }
        float base = 0.42f / (float) Math.sqrt(n);
        for (int i = 0; i < n; i++) {
            float w = (i == hi) ? 1.0f : (i == lo) ? 0.85f : 0.6f;
            g[i] = base * w * 1.6f;
        }
        return g;
    }

    private void play(int[] notes) {
        float[] g = gains(notes);
        for (int i = 0; i < notes.length; i++) sink.accept(notes[i], g[i]);
    }
}
