package com.jaehoseol.canontyping.score;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "F#5", "Bb3", "C4" 같은 음 이름을 MIDI 번호(C4 = 60)로 바꿔 준다. */
public final class NoteName {
    private static final Pattern P = Pattern.compile("([A-Ga-g])([#b]?)(-?\\d+)");
    private static final int[] SEMITONE = {9, 11, 0, 2, 4, 5, 7}; // A B C D E F G

    private NoteName() {}

    public static int parse(String name) {
        Matcher m = P.matcher(name.trim());
        if (!m.matches()) throw new IllegalArgumentException("Bad note name: " + name);
        int semi = SEMITONE[Character.toUpperCase(m.group(1).charAt(0)) - 'A'];
        if (m.group(2).equals("#")) semi++;
        else if (m.group(2).equals("b")) semi--;
        int octave = Integer.parseInt(m.group(3));
        return (octave + 1) * 12 + semi;
    }

    /** 공백으로 구분된 음 이름들 → MIDI 번호 배열 */
    public static int[] parseAll(String names) {
        String t = names.trim();
        if (t.isEmpty()) return new int[0];
        String[] parts = t.split("\\s+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = parse(parts[i]);
        return out;
    }
}
