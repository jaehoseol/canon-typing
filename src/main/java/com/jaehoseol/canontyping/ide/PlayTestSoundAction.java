package com.jaehoseol.canontyping.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/** Tools > Canon Typing > Play Test Sound : D장조 화음 하나를 울려 오디오 출력이 되는지 확인한다. */
public final class PlayTestSoundAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        CanonTypingService.getInstance().playTestChord();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
