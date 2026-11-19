package com.jaehoseol.canontyping.ide;

import com.jaehoseol.canontyping.score.Score;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Tools > Canon Typing > Load MIDI Score...
 * 내장 편곡 대신 원하는 .mid 파일로 연주한다. 동시에 시작하는 음은 한 박자(화음)로 묶인다.
 */
public final class LoadMidiAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
        descriptor.setTitle("Choose a MIDI File");
        FileChooser.chooseFile(descriptor, project, null, vf -> {
            String name = vf.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".mid") && !name.endsWith(".midi")) {
                Messages.showErrorDialog(project, "Please choose a .mid or .midi file.", "Canon Typing");
                return;
            }
            try {
                Score s = CanonTypingService.getInstance().loadMidi(new File(vf.getPath()));
                Messages.showInfoMessage(project,
                        "Loaded \"" + s.name() + "\": " + s.size() + " beats (one per key press).",
                        "Canon Typing");
            } catch (IOException ex) {
                Messages.showErrorDialog(project, "Could not load the MIDI file:\n" + ex.getMessage(), "Canon Typing");
            }
        });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
