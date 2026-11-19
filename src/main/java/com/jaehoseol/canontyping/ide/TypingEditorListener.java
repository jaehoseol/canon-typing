package com.jaehoseol.canontyping.ide;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * 에디터가 만들어질 때마다 그 에디터에 키 감지기를 붙이고, 닫힐 때 뗀다.
 *
 * 두 가지 경로로 "키가 눌렸다"를 알아낸다.
 *  1) KeyListener.keyPressed  : 영문·숫자·기호·스페이스·엔터·백스페이스 등 일반 입력.
 *     Ctrl+S 같은 IDE 단축키는 IDE가 먼저 가로채서 여기까지 오지 않는다.
 *  2) InputMethodListener     : 한글 IME로 조합 중인 글자. IME가 키를 먼저 소비하면
 *     KeyEvent가 오지 않고 조합 결과만 InputMethodEvent로 오기 때문에 따로 들어야 한다.
 * 같은 키가 두 경로로 중복 집계되지 않도록 직전 키 이후 짧은 시간 안의 IME 이벤트는 무시한다.
 */
public final class TypingEditorListener implements EditorFactoryListener {
    private static final Key<Handler> HANDLER_KEY = Key.create("canontyping.handler");
    private static final long DEDUP_NANOS = 40_000_000L; // 40 ms

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        // 서비스를 미리 띄운다 (터미널용 전역 키 감지기가 여기서 설치된다)
        CanonTypingService.getInstance();

        // 파일 편집 창 + 콘솔(Python 콘솔, 새 터미널의 입력창). diff·검색창 등은 제외
        EditorKind kind = editor.getEditorKind();
        if (kind != EditorKind.MAIN_EDITOR && kind != EditorKind.CONSOLE) return;

        Handler handler = new Handler(editor);
        editor.getContentComponent().addKeyListener(handler);
        editor.getContentComponent().addInputMethodListener(handler);
        editor.putUserData(HANDLER_KEY, handler);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Handler handler = editor.getUserData(HANDLER_KEY);
        if (handler != null) {
            editor.getContentComponent().removeKeyListener(handler);
            editor.getContentComponent().removeInputMethodListener(handler);
            editor.putUserData(HANDLER_KEY, null);
        }
    }

    private static final class Handler extends KeyAdapter implements InputMethodListener {
        private final Editor editor;
        private long lastKeyNanos = Long.MIN_VALUE / 2;

        Handler(Editor editor) {
            this.editor = editor;
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (!isTypingKey(e)) return;
            lastKeyNanos = System.nanoTime();
            fire();
        }

        @Override
        public void inputMethodTextChanged(InputMethodEvent e) {
            long now = System.nanoTime();
            if (now - lastKeyNanos < DEDUP_NANOS) return; // 방금 KeyEvent로 이미 셌다
            lastKeyNanos = now;
            fire();
        }

        @Override
        public void caretPositionChanged(InputMethodEvent e) {
            // 조합 중 커서 이동은 키 입력으로 세지 않는다
        }

        private void fire() {
            if (!editor.isViewer()) CanonTypingService.getInstance().onKey("KeyListener/IME");
        }
    }

    /** 글자·숫자·기호·스페이스·엔터·백스페이스·탭. 방향키, Shift 단독, Esc, 단축키 조합은 제외. */
    static boolean isTypingKey(KeyEvent e) {
        boolean ctrl = e.isControlDown() && !e.isAltGraphDown();
        boolean alt = e.isAltDown() && !e.isAltGraphDown();
        if (ctrl || alt || e.isMetaDown()) return false;
        char c = e.getKeyChar();
        return c != KeyEvent.CHAR_UNDEFINED && c != 27 /* Esc */;
    }
}
