package com.jaehoseol.canontyping.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/** Tools > Canon Typing > Enable Canon Typing (체크 메뉴) */
public final class ToggleCanonTypingAction extends ToggleAction implements DumbAware {
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return CanonTypingService.getInstance().isEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        CanonTypingService.getInstance().setEnabled(state);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
