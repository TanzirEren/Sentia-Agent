package com.sentia.ai.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Project memory protocol. In a project chat the AI may end a reply with
 * a line such as remember-tag-open note remember-tag-close. The app saves the note to the
 * project memory and hides the tag from the chat.
 */
public final class MemoryTags {
    private static final String OPEN = "<remember>";
    private static final Pattern TAG = Pattern.compile("<remember>(.*?)</remember>",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private MemoryTags() {}

    /** Notes found in a finished reply. */
    public static List<String> extract(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            String n = m.group(1).replace('\n', ' ').trim();
            if (n.length() > 300) n = n.substring(0, 300).trim();
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    /** Removes complete tags and any half-written tag at the end (used while streaming and when saving). */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        String s = TAG.matcher(text).replaceAll("");
        int lt = s.lastIndexOf('<');
        if (lt >= 0) {
            String tail = s.substring(lt).toLowerCase();
            if (tail.startsWith(OPEN) || (tail.length() < OPEN.length() && OPEN.startsWith(tail))) {
                s = s.substring(0, lt);
            }
        }
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
