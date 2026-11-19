package com.jaehoseol.canontyping.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/** Tools > Canon Typing > Auto-Tempo Settings... : 박자로 넘어가는 타수 기준과 BPM을 정한다. */
public final class AutoTempoSettingsAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        CanonTypingService svc = CanonTypingService.getInstance();

        String thr = Messages.showInputDialog(project,
                "Switch to a steady beat when you type faster than this many keys per second (0.2 - 10).\n"
                        + "It goes back to one beat per key press when your average speed drops to half of this.",
                "Auto-Tempo Settings (1/2): Speed Threshold",
                Messages.getQuestionIcon(), String.valueOf(svc.getTempoThreshold()), (InputValidator) null);
        if (thr == null) return;
        String bpmText = Messages.showInputDialog(project,
                "Tempo of the steady beat, in beats per minute (30 - 240).",
                "Auto-Tempo Settings (2/2): Tempo (BPM)",
                Messages.getQuestionIcon(), String.valueOf(svc.getTempoBpm()), (InputValidator) null);
        if (bpmText == null) return;

        try {
            double threshold = Double.parseDouble(thr.trim());
            double bpm = Double.parseDouble(bpmText.trim());
            if (threshold < 0.2 || threshold > 10 || bpm < 30 || bpm > 240) {
                throw new NumberFormatException("out of range");
            }
            svc.setTempoParams(threshold, bpm);
        } catch (NumberFormatException ex) {
            Messages.showErrorDialog(project,
                    "Please enter numbers in range: speed 0.2 - 10 keys/sec, tempo 30 - 240 BPM.",
                    "Canon Typing");
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
