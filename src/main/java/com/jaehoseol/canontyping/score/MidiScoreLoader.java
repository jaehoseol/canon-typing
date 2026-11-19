package com.jaehoseol.canontyping.score;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * 표준 MIDI 파일(.mid)을 스텝 목록으로 바꾼다.
 * 거의 같은 시각(박자의 1/12 이내)에 시작하는 음들은 한 스텝(화음)으로 묶는다.
 * 드럼 채널(10번)은 무시한다.
 */
public final class MidiScoreLoader {
    private MidiScoreLoader() {}

    private record NoteOn(long tick, int note) {}

    public static Score load(File file) throws IOException {
        Sequence seq;
        try {
            seq = javax.sound.midi.MidiSystem.getSequence(file);
        } catch (InvalidMidiDataException | IOException e) {
            throw new IOException("Not a valid MIDI file: " + file.getName(), e);
        }

        List<NoteOn> ons = new ArrayList<>();
        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent ev = track.get(i);
                if (ev.getMessage() instanceof ShortMessage sm
                        && sm.getCommand() == ShortMessage.NOTE_ON
                        && sm.getData2() > 0
                        && sm.getChannel() != 9) {
                    ons.add(new NoteOn(ev.getTick(), sm.getData1()));
                }
            }
        }
        if (ons.isEmpty()) throw new IOException("No notes found in " + file.getName());
        ons.sort(Comparator.comparingLong(NoteOn::tick).thenComparingInt(NoteOn::note));

        long tolerance = seq.getDivisionType() == Sequence.PPQ
                ? Math.max(1, seq.getResolution() / 12)
                : 10;

        List<int[]> steps = new ArrayList<>();
        TreeSet<Integer> cur = new TreeSet<>();
        long groupStart = ons.get(0).tick();
        for (NoteOn on : ons) {
            if (on.tick() - groupStart > tolerance) {
                steps.add(toArray(cur));
                cur.clear();
                groupStart = on.tick();
            }
            cur.add(on.note());
        }
        steps.add(toArray(cur));

        String name = file.getName().replaceFirst("\\.[^.]+$", "");
        return new Score(name, steps);
    }

    private static int[] toArray(TreeSet<Integer> s) {
        int[] r = new int[s.size()];
        int i = 0;
        for (int x : s) r[i++] = x;
        return r;
    }
}
