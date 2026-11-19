package com.jaehoseol.canontyping.ide;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * 키 감지 경로 2. 에디터에 글자가 입력되기 직전에 IDE가 불러 주는 공식 훅이다.
 * TypingEditorListener(AWT 키 리스너)와 같은 키를 중복으로 세지 않도록
 * CanonTypingService.onKey()가 20ms 안의 중복을 걸러낸다.
 */
public final class CanonTypedHandler extends TypedHandlerDelegate implements DumbAware {
    @Override
    public @NotNull Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor,
                                           @NotNull PsiFile file, @NotNull FileType fileType) {
        CanonTypingService.getInstance().onKey("TypedHandler");
        return Result.CONTINUE;
    }
}
