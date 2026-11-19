package com.jaehoseol.canontyping.audio;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 울리고 있는 음(voice)들을 한 블록씩 합쳐 주는 믹서. 오디오 장치와 무관하므로
 * 실시간 재생(AudioEngine)과 오프라인 WAV 렌더링(DemoWav) 모두에 쓴다.
 */
public final class Mixer {
    private static final int MAX_VOICES = 48;
    private static final int RETRIGGER_FADE = 1500; // 같은 음을 다시 치면 이전 음은 ~34ms에 걸쳐 끈다

    private static final class Voice {
        final int key;
        final float[] data;
        final float gain;
        int pos;
        int fadeLeft = -1; // -1: 페이드 아님

        Voice(int key, float[] data, float gain) {
            this.key = key;
            this.data = data;
            this.gain = gain;
        }
    }

    private final List<Voice> voices = new ArrayList<>();

    public synchronized void noteOn(int key, float[] samples, float gain) {
        for (Voice v : voices) {
            if (v.key == key && v.fadeLeft < 0) v.fadeLeft = RETRIGGER_FADE;
        }
        voices.add(new Voice(key, samples, gain));
        while (voices.size() > MAX_VOICES) voices.remove(0);
    }

    public synchronized boolean isIdle() {
        return voices.isEmpty();
    }

    /** out[0..frames) 에 현재 울리는 음들을 합쳐 쓴다 (-1..1). */
    public synchronized void mix(float[] out, int frames) {
        java.util.Arrays.fill(out, 0, frames, 0f);
        Iterator<Voice> it = voices.iterator();
        while (it.hasNext()) {
            Voice v = it.next();
            int n = Math.min(frames, v.data.length - v.pos);
            for (int i = 0; i < n; i++) {
                float s = v.data[v.pos + i] * v.gain;
                if (v.fadeLeft >= 0) {
                    if (v.fadeLeft == 0) { v.pos = v.data.length; break; }
                    s *= (float) v.fadeLeft / RETRIGGER_FADE;
                    v.fadeLeft--;
                }
                out[i] += s;
            }
            v.pos += n;
            if (v.pos >= v.data.length || v.fadeLeft == 0) it.remove();
        }
        for (int i = 0; i < frames; i++) {
            out[i] = (float) Math.tanh(out[i]); // 화음이 겹쳐도 찢어지지 않게 부드럽게 눌러 준다
        }
    }
}
