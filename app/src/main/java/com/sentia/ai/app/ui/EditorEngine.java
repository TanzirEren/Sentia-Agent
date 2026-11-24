package com.sentia.ai.app.ui;

import android.content.Context;

import com.sentia.ai.app.workspace.FileKinds;

import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;

import org.eclipse.tm4e.core.registry.IThemeSource;

/**
 * One-time setup of syntax highlighting (TextMate grammars and themes from assets/textmate).
 * The editor still works without it, only without colors.
 */
public final class EditorEngine {
    public static final String THEME_LIGHT = "quietlight";
    public static final String THEME_DARK = "darcula";

    private static boolean ready;
    private static boolean failed;

    private EditorEngine() {}

    /** Blocking: call from a background thread. Returns true when highlighting is available. */
    public static synchronized boolean ensure(Context ctx) {
        if (ready) return true;
        if (failed) return false;
        try {
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(ctx.getApplicationContext().getAssets()));
            ThemeRegistry themes = ThemeRegistry.getInstance();
            loadTheme(themes, THEME_LIGHT, false);
            loadTheme(themes, THEME_DARK, true);
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
            ready = true;
        } catch (Throwable t) {
            failed = true;
        }
        return ready;
    }

    public static synchronized boolean isReady() {
        return ready;
    }

    private static void loadTheme(ThemeRegistry themes, String name, boolean dark) throws Exception {
        String path = "textmate/" + name + ".json";
        ThemeModel m = new ThemeModel(
                IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null), name);
        m.setDark(dark);
        themes.loadTheme(m);
    }

    /** TextMate scope for a file name, or null when the file type has no grammar. */
    public static String scopeFor(String fileName) {
        switch (FileKinds.ext(fileName)) {
            case "java": return "source.java";
            case "xml":
            case "svg":
            case "gradle_xml": return "text.xml";
            case "html":
            case "htm": return "text.html.basic";
            case "css": return "source.css";
            case "js":
            case "mjs":
            case "cjs": return "source.js";
            case "json": return "source.json";
            case "md":
            case "markdown": return "text.html.markdown";
            default: return null;
        }
    }

    /** Short name shown in the editor status line. */
    public static String languageLabel(String fileName) {
        String s = scopeFor(fileName);
        if (s == null) return "Text";
        switch (s) {
            case "source.java": return "Java";
            case "text.xml": return "XML";
            case "text.html.basic": return "HTML";
            case "source.css": return "CSS";
            case "source.js": return "JavaScript";
            case "source.json": return "JSON";
            default: return "Markdown";
        }
    }
}
