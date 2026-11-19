package com.jaehoseol.canontyping.audio;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 샘플 파일 없이 피아노 비슷한 소리를 합성한다 (가산 합성).
 *
 * - 배음 24개까지, 건반이 높을수록 약간씩 높게 벌어지는 비조화성(inharmonicity)
 * - 음마다 두 줄(string): 한 줄은 빨리, 한 줄은 천천히 감쇠 → 피아노 특유의 "퉁-" 하고 길게 남는 소리
 * - 해머가 현을 치는 짧은 노이즈
 * 음 하나를 한 번 만들어 캐시하고 재사용한다.
 */
public final class PianoSynth {
    private final int sampleRate;
    private final ConcurrentHashMap<Integer, float[]> cache = new ConcurrentHashMap<>();

    public PianoSynth(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public float[] get(int midi) {
        return cache.computeIfAbsent(midi, this::render);
    }

    float[] render(int midi) {
        double f0 = 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
        double seconds = clamp(6.5 - (midi - 36) * 0.05, 2.5, 6.0);
        int n = (int) (seconds * sampleRate);
        double[] acc = new double[n];

        double inharm = 0.00006 * Math.exp(0.045 * (midi - 36));
        double tau0 = clamp(3.2 * Math.pow(2.0, -(midi - 48) / 24.0), 0.6, 5.0);

        for (int p = 1; p <= 24; p++) {
            double fp = f0 * p * Math.sqrt(1.0 + inharm * p * p);
            if (fp > 0.42 * sampleRate) break;

            double amp = Math.pow(p, -0.9)
                    * (0.25 + Math.abs(Math.sin(Math.PI * p / 7.5)))   // 해머 위치에 따른 배음 세기
                    * Math.exp(-p * f0 / 6000.0);                       // 높은 배음일수록 약하게
            double tau = tau0 / (1.0 + 0.5 * (p - 1));

            addPartial(acc, fp * 1.00025, amp * 0.65, tau);
            addPartial(acc, fp * 0.99940, amp * 0.35, tau * 2.4);
        }

        // 해머 노이즈 (아주 짧고 약하게)
        Random rnd = new Random(midi * 7919L);
        double lp = 0;
        int noiseLen = (int) (0.03 * sampleRate);
        double peakTone = 0;
        for (int i = 0; i < n; i++) peakTone = Math.max(peakTone, Math.abs(acc[i]));
        for (int i = 0; i < noiseLen && i < n; i++) {
            lp += 0.25 * ((rnd.nextDouble() * 2 - 1) - lp);
            acc[i] += lp * 0.06 * peakTone * Math.exp(-i / (0.006 * sampleRate));
        }

        // 어택(2~3ms)과 끝부분 페이드아웃
        float[] out = new float[n];
        double attack = 0.003 * sampleRate;
        int fade = (int) (0.08 * sampleRate);
        double peak = 0;
        for (int i = 0; i < n; i++) {
            double v = acc[i] * (1.0 - Math.exp(-i / attack));
            if (i >= n - fade) v *= (double) (n - 1 - i) / fade;
            acc[i] = v;
            peak = Math.max(peak, Math.abs(v));
        }
        double norm = peak > 0 ? 0.9 / peak : 1.0;
        for (int i = 0; i < n; i++) out[i] = (float) (acc[i] * norm);
        return out;
    }

    /** sin(wn) · r^n 을 복소수 회전으로 누적 (삼각함수를 샘플마다 호출하지 않아 빠르다) */
    private void addPartial(double[] acc, double freq, double amp, double tau) {
        double w = 2.0 * Math.PI * freq / sampleRate;
        double r = Math.exp(-1.0 / (tau * sampleRate));
        double wr = r * Math.cos(w), wi = r * Math.sin(w);
        double re = 1.0, im = 0.0;
        for (int i = 0; i < acc.length; i++) {
            acc[i] += amp * im;
            double nre = re * wr - im * wi;
            im = re * wi + im * wr;
            re = nre;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
