package com.jaehoseol.canontyping.score;

import java.util.ArrayList;
import java.util.List;

/**
 * 파헬벨 '캐논' (D장조) 피아노 편곡 - 간략판.
 *
 * 원곡의 8화음 진행(D A Bm F#m G D G A)과 베이스(D A B F# G D G A),
 * 그리고 위에서 계단식으로 내려오는 선율(F# E D C# B A B C#)을 뼈대로 만든 것이라
 * 정식 악보를 그대로 옮긴 것은 아니다. 더 정확한 악보를 쓰고 싶으면
 * MIDI 파일을 불러오면 된다 (Tools > Canon Typing > Load MIDI Score...).
 *
 * 화음 하나 = 2박. 키 한 번 = 1박이므로 화음마다 키 2번.
 * 한 바퀴(8화음) = 키 16번, 총 4바퀴 + 마지막 마침화음 1개 = 65스텝, 끝나면 처음으로.
 */
public final class CanonInD {
    private CanonInD() {}

    private static final String[] BASS   = {"D3", "A2", "B2", "F#2", "G2", "D2", "G2", "A2"};
    private static final String[] TRIAD  = {
            "D4 F#4 A4", "C#4 E4 A4", "D4 F#4 B4", "C#4 F#4 A4",
            "D4 G4 B4",  "D4 F#4 A4", "D4 G4 B4",  "C#4 E4 A4"};
    /** 1·3·4바퀴의 선율 (2분음표) */
    private static final String[] MELODY = {"F#5", "E5", "D5", "C#5", "B4", "A4", "B4", "C#5"};
    /** 2바퀴의 선율 (4분음표 2개씩) */
    private static final String[][] PAIRS = {
            {"F#5", "D5"}, {"E5", "C#5"}, {"D5", "B4"}, {"C#5", "A4"},
            {"B4", "G4"},  {"A4", "F#4"}, {"B4", "D5"}, {"C#5", "E5"}};

    public static Score create() {
        List<int[]> steps = new ArrayList<>();

        // 1바퀴: 베이스 + 선율 / 화음
        for (int i = 0; i < 8; i++) {
            steps.add(join(bassWithOctave(i), n(MELODY[i])));
            steps.add(n(TRIAD[i]));
        }
        // 2바퀴: 선율이 4분음표로 잘게 움직임
        for (int i = 0; i < 8; i++) {
            steps.add(join(bassWithOctave(i), n(PAIRS[i][0])));
            steps.add(n(PAIRS[i][1]));
        }
        // 3바퀴: 꽉 찬 화음 + 한 옥타브 겹친 선율
        for (int i = 0; i < 8; i++) {
            int m = n(MELODY[i])[0];
            steps.add(join(bassWithOctave(i), n(TRIAD[i]), new int[]{m, m + 12}));
            steps.add(n(TRIAD[i]));
        }
        // 4바퀴: 선율을 한 옥타브 올려서 반짝이게
        for (int i = 0; i < 8; i++) {
            int m = n(MELODY[i])[0] + 12;
            steps.add(join(bassWithOctave(i), new int[]{m}));
            steps.add(transpose(n(TRIAD[i]), 12));
        }
        // 마침화음 (D장조)
        steps.add(n("D2 D3 D4 F#4 A4 D5 F#5"));

        return new Score("Pachelbel - Canon in D (simplified)", steps);
    }

    private static int[] n(String names) { return NoteName.parseAll(names); }

    private static int[] bassWithOctave(int i) {
        int b = NoteName.parse(BASS[i]);
        return new int[]{b, b + 12};
    }

    private static int[] transpose(int[] notes, int semis) {
        int[] r = new int[notes.length];
        for (int i = 0; i < r.length; i++) r[i] = notes[i] + semis;
        return r;
    }

    private static int[] join(int[]... parts) {
        int len = 0;
        for (int[] p : parts) len += p.length;
        int[] r = new int[len];
        int k = 0;
        for (int[] p : parts) for (int x : p) r[k++] = x;
        java.util.Arrays.sort(r);
        return r;
    }
}
