package com.jaehoseol.canontyping.score;

import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * 악보 = "스텝"의 나열. 키를 한 번 누를 때마다 스텝 하나가 연주된다.
 * 스텝 하나는 동시에 울리는 MIDI 음 번호들(단음이면 1개, 화음이면 여러 개).
 */
public final class Score {
    private final String name;
    private final List<int[]> steps;

    public Score(String name, List<int[]> steps) {
        if (steps.isEmpty()) throw new IllegalArgumentException("empty score");
        this.name = name;
        this.steps = Collections.unmodifiableList(steps);
    }

    public String name() { return name; }
    public int size() { return steps.size(); }
    public int[] step(int i) { return steps.get(i); }

    /** 이 악보에 쓰이는 모든 음 (소리를 미리 만들어 둘 때 사용) */
    public TreeSet<Integer> allNotes() {
        TreeSet<Integer> s = new TreeSet<>();
        for (int[] st : steps) for (int n : st) s.add(n);
        return s;
    }
}
