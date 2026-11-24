package com.sentia.ai.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.EventReceiver;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.Unsubscribe;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * The only place in Sentia that touches Sora Editor. The rest of the app talks to this wrapper,
 * so the editor library can be replaced later without touching other screens.
 */
public class CodeEditorView extends FrameLayout {
    public interface Listener {
        void onContentChanged();

        void onCursor(int line, int column);
    }

    private final CodeEditor editor;
    private Listener listener;
    private boolean silent;

    public CodeEditorView(Context c) {
        this(c, null);
    }

    public CodeEditorView(Context c, AttributeSet attrs) {
        super(c, attrs);
        setSaveEnabled(false);
        editor = new CodeEditor(c);
        editor.setSaveEnabled(false);
        addView(editor, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        editor.setLineNumberEnabled(true);
        editor.setScalable(true);
        editor.setHighlightCurrentLine(true);
        editor.setTabWidth(4);
        editor.getSearcher().setCyclicJumping(true);
        editor.subscribeEvent(ContentChangeEvent.class, new EventReceiver<ContentChangeEvent>() {
            @Override
            public void onReceive(@NonNull ContentChangeEvent event, @NonNull Unsubscribe unsubscribe) {
                if (!silent && listener != null) listener.onContentChanged();
            }
        });
        editor.subscribeEvent(SelectionChangeEvent.class, new EventReceiver<SelectionChangeEvent>() {
            @Override
            public void onReceive(@NonNull SelectionChangeEvent event, @NonNull Unsubscribe unsubscribe) {
                if (listener != null) listener.onCursor(event.getLeft().line + 1, event.getLeft().column + 1);
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    // ------------------------------------------------------------ text

    /** Replaces all text and clears undo history. Does not count as a user change. */
    public void setContent(String text) {
        silent = true;
        try {
            editor.setText(text == null ? "" : text);
        } finally {
            silent = false;
        }
    }

    public String getContent() {
        return editor.getText().toString();
    }

    public int lineCount() {
        return editor.getText().getLineCount();
    }

    public void setReadOnly(boolean readOnly) {
        editor.setEditable(!readOnly);
    }

    public boolean canUndo() {
        return editor.canUndo();
    }

    public boolean canRedo() {
        return editor.canRedo();
    }

    public void undo() {
        editor.undo();
    }

    public void redo() {
        editor.redo();
    }

    /** Types text at the cursor. cursorInside is the cursor position inside the inserted text. */
    public void insert(String text, int cursorInside) {
        editor.insertText(text, cursorInside);
    }

    public void goToLine(int line1) {
        int max = Math.max(1, lineCount());
        int l = Math.max(1, Math.min(max, line1));
        editor.setSelection(l - 1, 0, true);
        editor.requestFocus();
    }

    // ------------------------------------------------------------ look

    public void setWordWrap(boolean on) {
        editor.setWordwrap(on);
    }

    public boolean isWordWrap() {
        return editor.isWordwrap();
    }

    public void setLineNumbers(boolean on) {
        editor.setLineNumberEnabled(on);
    }

    public void setTabSize(int n) {
        editor.setTabWidth(n);
    }

    public void setFontSizeSp(float sp) {
        editor.setTextSize(sp);
    }

    public float getFontSizeSp() {
        float onePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, getResources().getDisplayMetrics());
        return editor.getTextSizePx() / onePx;
    }

    public void setMonoTypeface(Typeface tf) {
        if (tf != null) editor.setTypefaceText(tf);
    }

    /**
     * Colors and language. Highlighting is skipped quietly when the engine is not ready.
     * The colors follow the Sentia palette, only the token colors come from the TextMate theme.
     */
    public void applyLook(String fileName, boolean night, int bg, int fg, int faint, int line) {
        try {
            if (EditorEngine.isReady()) {
                ThemeRegistry.getInstance().setTheme(night ? EditorEngine.THEME_DARK : EditorEngine.THEME_LIGHT);
                TextMateColorScheme scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
                override(scheme, bg, fg, faint, line);
                editor.setColorScheme(scheme);
                String scope = EditorEngine.scopeFor(fileName);
                editor.setEditorLanguage(scope == null ? null : TextMateLanguage.create(scope, false));
                return;
            }
        } catch (Throwable t) {
            // fall through to plain colors
        }
        EditorColorScheme plain = editor.getColorScheme();
        override(plain, bg, fg, faint, line);
        editor.setColorScheme(plain);
    }

    private static void override(EditorColorScheme s, int bg, int fg, int faint, int line) {
        s.setColor(EditorColorScheme.WHOLE_BACKGROUND, bg);
        s.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bg);
        s.setColor(EditorColorScheme.LINE_NUMBER, faint);
        s.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, fg);
        s.setColor(EditorColorScheme.LINE_DIVIDER, line);
        s.setColor(EditorColorScheme.TEXT_NORMAL, fg);
        s.setColor(EditorColorScheme.CURRENT_LINE, (line & 0x00FFFFFF) | 0x55000000);
    }

    // ------------------------------------------------------------ find and replace

    public void find(String query, boolean ignoreCase, boolean regex) {
        EditorSearcher s = editor.getSearcher();
        if (query == null || query.isEmpty()) {
            s.stopSearch();
            return;
        }
        try {
            s.search(query, new EditorSearcher.SearchOptions(ignoreCase, regex));
        } catch (RuntimeException e) {
            s.stopSearch();
        }
    }

    public void stopFind() {
        editor.getSearcher().stopSearch();
    }

    public boolean findNext() {
        EditorSearcher s = editor.getSearcher();
        return s.hasQuery() && s.gotoNext();
    }

    public boolean findPrevious() {
        EditorSearcher s = editor.getSearcher();
        return s.hasQuery() && s.gotoPrevious();
    }

    public int matchCount() {
        EditorSearcher s = editor.getSearcher();
        return s.hasQuery() ? s.getMatchedPositionCount() : 0;
    }

    /** 1-based index of the selected match, or 0. */
    public int matchIndex() {
        EditorSearcher s = editor.getSearcher();
        if (!s.hasQuery() || !s.isMatchedPositionSelected()) return 0;
        return s.getCurrentMatchedPositionIndex() + 1;
    }

    public void replaceCurrent(String with) {
        EditorSearcher s = editor.getSearcher();
        if (s.hasQuery() && s.isMatchedPositionSelected()) s.replaceThis(with);
    }

    public void replaceAll(String with) {
        EditorSearcher s = editor.getSearcher();
        if (s.hasQuery()) s.replaceAll(with);
    }

    public void release() {
        try {
            editor.release();
        } catch (RuntimeException e) {
            // already released
        }
    }
}
