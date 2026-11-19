package com.jaehoseol.canontyping.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * Tools > Canon Typing > Play Everywhere (체크 메뉴).
 * 켜면 IDE 어디서든(터미널, Copilot 등 다른 플러그인 창, 검색창, 대화상자) 글자 키에 소리가 난다.
 * 끄면 에디터와 터미널에서만 난다.
 */
public final class ToggleEverywhereAction extends ToggleAction implements DumbAware {
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return CanonTypingService.getInstance().isEverywhere();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        CanonTypingService.getInstance().setEverywhere(state);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
