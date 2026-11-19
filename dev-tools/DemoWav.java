import com.jaehoseol.canontyping.audio.AudioEngine;
import com.jaehoseol.canontyping.audio.CanonPlayer;
import com.jaehoseol.canontyping.audio.Mixer;
import com.jaehoseol.canontyping.audio.PianoSynth;
import com.jaehoseol.canontyping.score.CanonInD;
import com.jaehoseol.canontyping.score.MidiScoreLoader;
import com.jaehoseol.canontyping.score.Score;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * IDE 없이 소리와 악보를 확인하는 개발용 도구. 키를 한 번 누를 때마다 한 스텝씩,
 * 일정한 간격으로 연주한 결과를 WAV 파일로 저장한다.
 *
 *   java -cp out DemoWav <출력.wav> [BPM=100] [악보.mid]
 */
public class DemoWav {
    public static void main(String[] args) throws Exception {
        File outFile = new File(args.length > 0 ? args[0] : "canon-demo.wav");
        double bpm = args.length > 1 ? Double.parseDouble(args[1]) : 100;
        Score score = args.length > 2 ? MidiScoreLoader.load(new File(args[2])) : CanonInD.create();

        int sr = AudioEngine.SAMPLE_RATE;
        int beat = (int) (sr * 60.0 / bpm);
        int total = beat * score.size() + sr * 5; // 마지막 음의 여운까지

        PianoSynth synth = new PianoSynth(sr);
        Mixer mixer = new Mixer();
        CanonPlayer player = new CanonPlayer(score, (midi, gain) -> mixer.noteOn(midi, synth.get(midi), gain));

        short[] pcm = new short[total];
        float[] block = new float[512];
        int pos = 0, nextBeat = 0, played = 0;
        while (pos < total) {
            if (pos >= nextBeat && played < score.size()) {
                player.next(); // ← 실제 플러그인에서는 키 입력 한 번이 이 호출 한 번
                played++;
                nextBeat += beat;
            }
            int frames = Math.min(block.length, total - pos);
            if (played < score.size()) frames = Math.min(frames, nextBeat - pos);
            mixer.mix(block, frames);
            for (int i = 0; i < frames; i++) pcm[pos + i] = (short) Math.round(block[i] * 32767f * 0.9f);
            pos += frames;
        }

        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[2 * i] = (byte) pcm[i];
            bytes[2 * i + 1] = (byte) (pcm[i] >> 8);
        }
        AudioFormat fmt = new AudioFormat(sr, 16, 1, true, false);
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(bytes), fmt, pcm.length),
                AudioFileFormat.Type.WAVE, outFile);

        int peak = 0;
        double sum = 0;
        for (short s : pcm) { peak = Math.max(peak, Math.abs(s)); sum += (double) s * s; }
        System.out.printf("score=\"%s\" steps=%d  bpm=%.0f  length=%.1fs  peak=%d/32767  rms=%.0f%n",
                score.name(), score.size(), bpm, pcm.length / (double) sr, peak, Math.sqrt(sum / pcm.length));
        System.out.println("wrote " + outFile.getAbsolutePath());
    }
}
