package com.sentia.ai.app.util;

import android.content.Context;

import com.sentia.ai.app.R;
import com.sentia.ai.app.model.ProjectCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Slash commands for project chats: a few built-in ones plus the project's own. */
public final class Slash {
    public static final class Item {
        public final String name;
        public final String hint;
        public final String prompt;
        public final boolean builtin;

        Item(String name, String hint, String prompt, boolean builtin) {
            this.name = name;
            this.hint = hint;
            this.prompt = prompt;
            this.builtin = builtin;
        }
    }

    private Slash() {}

    public static List<Item> available(Context c, List<ProjectCommand> custom) {
        List<Item> out = new ArrayList<>();
        out.add(new Item("plan", c.getString(R.string.slash_plan_hint), c.getString(R.string.slash_plan_prompt), true));
        out.add(new Item("review", c.getString(R.string.slash_review_hint), c.getString(R.string.slash_review_prompt), true));
        out.add(new Item("explain", c.getString(R.string.slash_explain_hint), c.getString(R.string.slash_explain_prompt), true));
        out.add(new Item("fix", c.getString(R.string.slash_fix_hint), c.getString(R.string.slash_fix_prompt), true));
        out.add(new Item("tests", c.getString(R.string.slash_tests_hint), c.getString(R.string.slash_tests_prompt), true));
        out.add(new Item("summarize", c.getString(R.string.slash_summarize_hint), c.getString(R.string.slash_summarize_prompt), true));
        if (custom != null) {
            for (ProjectCommand pc : custom) {
                if (pc == null || pc.name == null || pc.prompt == null) continue;
                String n = clean(pc.name);
                if (n.isEmpty()) continue;
                out.add(new Item(n, firstLine(pc.prompt), pc.prompt, false));
            }
        }
        return out;
    }

    /** Lower-case letters, digits, dash and underscore only. */
    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) s = s.substring(1);
        return s.replaceAll("[^a-z0-9_-]", "");
    }

    private static String firstLine(String s) {
        String t = s.trim().split("\n")[0];
        return t.length() > 60 ? t.substring(0, 60) + "..." : t;
    }

    /** Returns the expanded prompt, or the original text when it is not a known command. */
    public static String expand(String text, List<Item> items) {
        if (text == null) return "";
        String t = text.trim();
        if (!t.startsWith("/") || t.length() < 2) return text;
        int sp = t.indexOf(' ');
        int nl = t.indexOf('\n');
        if (sp < 0 || (nl >= 0 && nl < sp)) sp = nl;
        String name = (sp < 0 ? t.substring(1) : t.substring(1, sp)).toLowerCase(Locale.ROOT);
        String args = sp < 0 ? "" : t.substring(sp + 1).trim();
        Item found = null;
        for (int i = items.size() - 1; i >= 0; i--) { // custom commands win over built-in ones
            if (items.get(i).name.equals(name)) {
                found = items.get(i);
                break;
            }
        }
        if (found == null) return text;
        String p = found.prompt;
        if (p.contains("$ARGUMENTS")) return p.replace("$ARGUMENTS", args);
        return args.isEmpty() ? p : p + "\n\n" + args;
    }
}
