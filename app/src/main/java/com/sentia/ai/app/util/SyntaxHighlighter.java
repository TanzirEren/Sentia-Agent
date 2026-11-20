package com.sentia.ai.app.util;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import androidx.core.content.ContextCompat;

import com.sentia.ai.app.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small regex-based syntax highlighter for chat code blocks. */
public class SyntaxHighlighter {
    private static final int MAX_LEN = 20000;

    private static final Set<String> KW = new HashSet<>(Arrays.asList(
            "abstract", "as", "async", "await", "boolean", "break", "case", "catch", "class", "const", "continue",
            "def", "default", "del", "do", "double", "elif", "else", "enum", "except", "export", "extends", "false",
            "final", "finally", "float", "for", "fun", "func", "function", "from", "if", "implements", "import", "in",
            "instanceof", "int", "interface", "is", "lambda", "let", "long", "match", "new", "nil", "none", "not",
            "null", "object", "of", "or", "override", "package", "pass", "print", "private", "protected", "public",
            "raise", "return", "self", "short", "static", "struct", "super", "switch", "synchronized", "this", "throw",
            "throws", "true", "try", "type", "typeof", "undefined", "val", "var", "void", "volatile", "when", "while",
            "with", "yield", "and", "select", "insert", "update", "delete", "where", "join", "create", "table",
            "echo", "fi", "then", "esac", "done", "local", "mut", "impl", "pub", "use", "mod", "trait", "go", "defer"));

    private static final Pattern NUMBER = Pattern.compile("\\b0[xX][0-9a-fA-F]+\\b|\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern STR_C = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`");
    private static final Pattern STR_PY = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'");
    private static final Pattern CMT_C = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)");
    private static final Pattern CMT_HASH = Pattern.compile("#[^\\n]*");
    private static final Pattern CMT_SQL = Pattern.compile("--[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)");
    private static final Pattern CMT_XML = Pattern.compile("<!--[\\s\\S]*?(?:-->|$)");
    private static final Pattern TAG = Pattern.compile("</?[A-Za-z][\\w:.\\-]*|/?>");
    private static final Pattern JSON_KEY = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:)");

    private static final java.util.Map<String, Pattern> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final int cKeyword, cString, cComment, cNumber, cType, cTag, cKey;

    public SyntaxHighlighter(Context c) {
        cKeyword = ContextCompat.getColor(c, R.color.code_keyword);
        cString = ContextCompat.getColor(c, R.color.code_string);
        cComment = ContextCompat.getColor(c, R.color.code_comment);
        cNumber = ContextCompat.getColor(c, R.color.code_number);
        cType = ContextCompat.getColor(c, R.color.code_type);
        cTag = ContextCompat.getColor(c, R.color.code_tag);
        cKey = ContextCompat.getColor(c, R.color.code_key);
    }

    public Spannable highlight(String code, String lang) {
        SpannableStringBuilder sb = new SpannableStringBuilder(code == null ? "" : code);
        if (code == null || code.length() > MAX_LEN) return sb;
        String l = lang == null ? "" : lang.toLowerCase(Locale.ROOT);

        boolean markup = l.equals("html") || l.equals("xml") || l.equals("svg") || l.equals("vue") || l.equals("jsx");
        boolean json = l.equals("json") || l.equals("jsonc");
        boolean hash = l.equals("python") || l.equals("py") || l.equals("bash") || l.equals("sh") || l.equals("shell")
                || l.equals("yaml") || l.equals("yml") || l.equals("toml") || l.equals("ruby") || l.equals("rb")
                || l.equals("dockerfile") || l.equals("makefile") || l.equals("zsh") || l.equals("r");
        boolean sql = l.equals("sql");
        boolean py = l.equals("python") || l.equals("py");

        // Comments and strings are matched in one left-to-right pass so that
        // "http://x" is a string and "// don't" is a comment.
        boolean[] taken = new boolean[sb.length()];
        Pattern cmt = markup ? CMT_XML : hash ? CMT_HASH : sql ? CMT_SQL : json ? null : CMT_C;
        Pattern str = py ? STR_PY : STR_C;
        if (json) apply(sb, taken, JSON_KEY, cKey);
        String key = (cmt == null ? "none" : cmt.pattern()) + "|" + str.pattern();
        Pattern combo = CACHE.get(key);
        if (combo == null) {
            String src = (cmt == null ? "" : "(?<c>" + cmt.pattern() + ")|") + "(?<s>" + str.pattern() + ")";
            combo = Pattern.compile(src);
            CACHE.put(key, combo);
        }
        Matcher cm = combo.matcher(sb);
        while (cm.find()) {
            if (cm.end() <= cm.start() || taken[cm.start()]) continue;
            boolean isComment = cmt != null && cm.group("c") != null;
            sb.setSpan(new ForegroundColorSpan(isComment ? cComment : cString), cm.start(), cm.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            for (int i = cm.start(); i < cm.end() && i < taken.length; i++) taken[i] = true;
        }

        if (markup) {
            apply(sb, taken, TAG, cTag);
            return sb;
        }
        apply(sb, taken, NUMBER, cNumber);

        Matcher m = WORD.matcher(sb);
        while (m.find()) {
            if (taken[m.start()]) continue;
            String w = m.group();
            String lw = w.toLowerCase(Locale.ROOT);
            boolean isKw = KW.contains(w) || (sql && KW.contains(lw));
            if (isKw) {
                sb.setSpan(new ForegroundColorSpan(cKeyword), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (!json && Character.isUpperCase(w.charAt(0)) && w.length() > 1) {
                sb.setSpan(new ForegroundColorSpan(cType), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return sb;
    }

    private void apply(SpannableStringBuilder sb, boolean[] taken, Pattern p, int color) {
        Matcher m = p.matcher(sb);
        while (m.find()) {
            if (m.end() <= m.start() || taken[m.start()]) continue;
            sb.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            for (int i = m.start(); i < m.end() && i < taken.length; i++) taken[i] = true;
        }
    }
}
