package com.sentia.ai.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits a markdown message into plain-markdown text blocks and fenced code blocks. */
public final class BlockParser {
    public static final int TEXT = 0;
    public static final int CODE = 1;

    public static final class Block {
        public final int type;
        public final String lang;
        public final String text;

        Block(int type, String lang, String text) {
            this.type = type;
            this.lang = lang;
            this.text = text;
        }
    }

    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})\\s*([A-Za-z0-9_+#.\\-]*).*$");

    private BlockParser() {}

    public static List<Block> parse(String md) {
        List<Block> out = new ArrayList<>();
        if (md == null || md.isEmpty()) return out;
        String[] lines = md.replace("\r\n", "\n").split("\n", -1);
        StringBuilder text = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean inCode = false;
        char fenceChar = '`';
        int fenceLen = 3;
        String lang = "";

        for (String line : lines) {
            if (!inCode) {
                Matcher m = FENCE.matcher(line);
                if (m.matches()) {
                    flushText(out, text);
                    inCode = true;
                    String f = m.group(1);
                    fenceChar = f.charAt(0);
                    fenceLen = f.length();
                    lang = m.group(2) == null ? "" : m.group(2);
                    code.setLength(0);
                } else {
                    text.append(line).append('\n');
                }
            } else {
                String t = line.trim();
                if (isClosingFence(t, fenceChar, fenceLen)) {
                    out.add(new Block(CODE, lang, stripLastNewline(code)));
                    inCode = false;
                    code.setLength(0);
                } else {
                    code.append(line).append('\n');
                }
            }
        }
        if (inCode) {
            out.add(new Block(CODE, lang, stripLastNewline(code)));
        } else {
            flushText(out, text);
        }
        return out;
    }

    private static boolean isClosingFence(String t, char c, int len) {
        if (t.length() < len) return false;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) != c) return false;
        return true;
    }

    private static String stripLastNewline(StringBuilder sb) {
        int n = sb.length();
        if (n > 0 && sb.charAt(n - 1) == '\n') n--;
        return sb.substring(0, n);
    }

    private static void flushText(List<Block> out, StringBuilder text) {
        String s = text.toString().trim();
        text.setLength(0);
        if (!s.isEmpty()) out.add(new Block(TEXT, "", s));
    }
}
