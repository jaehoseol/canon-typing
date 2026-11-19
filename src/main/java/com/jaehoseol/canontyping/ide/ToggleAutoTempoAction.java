package com.jaehoseol.canontyping.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * Tools > Canon Typing > Auto-Tempo (체크 메뉴, 기본 꺼짐).
 * 꺼짐: 키 한 번에 한 박자.
 * 켜짐: 타수가 기준(기본 1키/초)을 넘으면 키와 무관하게 정해진 BPM으로 박자를 이어 가고,
 *       느려지면 다시 키 한 번에 한 박자로 돌아간다.
 */
public final class ToggleAutoTempoAction extends ToggleAction implements DumbAware {
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return CanonTypingService.getInstance().isAutoTempo();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        CanonTypingService.getInstance().setAutoTempo(state);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
