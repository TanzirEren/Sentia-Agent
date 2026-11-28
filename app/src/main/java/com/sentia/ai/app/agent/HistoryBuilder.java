package com.sentia.ai.app.agent;

import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.net.ChatMsg;
import com.sentia.ai.app.net.ToolCall;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the saved messages of a chat into the list sent to the model.
 * The important job: a tool call must always be followed by its results, and a result must always follow its call.
 * Providers reject a history where this is broken (for example after Stop), so broken parts are repaired here.
 */
public final class HistoryBuilder {
    /** Tool results older than this many are shortened, to save context. */
    public static final int KEEP_FULL_RESULTS = 6;
    public static final int OLD_RESULT_CHARS = 1500;

    /** Converts a user message (with its attachments). Supplied by the caller. */
    public interface UserConverter {
        ChatMsg convert(MessageEntity m);
    }

    private static final Gson GSON = new Gson();

    private HistoryBuilder() {}

    public static List<ToolCall> parseCalls(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            List<ToolCall> l = GSON.fromJson(json, new TypeToken<List<ToolCall>>() {}.getType());
            return l == null ? new ArrayList<ToolCall>() : l;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    public static List<ChatMsg> build(List<MessageEntity> history, int budgetChars, boolean toolsOn, UserConverter uc) {
        List<ChatMsg> all = new ArrayList<>();
        List<Boolean> isTool = new ArrayList<>();

        int i = 0;
        int n = history.size();
        while (i < n) {
            MessageEntity m = history.get(i);
            String role = m.role;
            if ("user".equals(role)) {
                boolean noText = m.content == null || m.content.isEmpty();
                if (!(noText && m.attachmentsJson == null)) {
                    all.add(uc.convert(m));
                    isTool.add(false);
                }
                i++;
            } else if ("assistant".equals(role)) {
                List<ToolCall> calls = toolsOn ? parseCalls(m.toolCallsJson) : new ArrayList<ToolCall>();
                // the results that follow this message
                int j = i + 1;
                Map<String, MessageEntity> results = new HashMap<>();
                while (j < n && "tool".equals(history.get(j).role)) {
                    MessageEntity t = history.get(j);
                    if (t.toolCallId != null) results.put(t.toolCallId, t);
                    j++;
                }
                boolean complete = !calls.isEmpty();
                for (ToolCall c : calls) {
                    if (c.id == null || !results.containsKey(c.id)) complete = false;
                }
                String text = m.content == null ? "" : m.content;
                if (complete) {
                    ChatMsg a = new ChatMsg();
                    a.role = "assistant";
                    a.text = text;
                    a.toolCalls = calls;
                    all.add(a);
                    isTool.add(false);
                    for (ToolCall c : calls) {
                        MessageEntity t = results.get(c.id);
                        ChatMsg tm = new ChatMsg();
                        tm.role = "tool";
                        tm.toolCallId = c.id;
                        tm.toolName = c.name;
                        tm.toolError = t.toolError;
                        tm.text = t.content == null ? "" : t.content;
                        all.add(tm);
                        isTool.add(true);
                    }
                } else if (!text.isEmpty()) {
                    // no calls, or calls that were never answered: keep only the words
                    ChatMsg a = new ChatMsg();
                    a.role = "assistant";
                    a.text = text;
                    all.add(a);
                    isTool.add(false);
                }
                i = j;
            } else {
                // errors, and tool results that do not follow a call
                i++;
            }
        }

        // shorten old tool results
        int seen = 0;
        for (int k = all.size() - 1; k >= 0; k--) {
            if (!isTool.get(k)) continue;
            seen++;
            if (seen > KEEP_FULL_RESULTS) {
                ChatMsg t = all.get(k);
                if (t.text.length() > OLD_RESULT_CHARS) {
                    t.text = t.text.substring(0, OLD_RESULT_CHARS) + "\n[older tool output shortened]";
                }
            }
        }

        // everything after the last user message is always kept, older messages only while they fit
        int lastUser = -1;
        for (int k = all.size() - 1; k >= 0; k--) {
            if ("user".equals(all.get(k).role)) {
                lastUser = k;
                break;
            }
        }
        List<ChatMsg> out = new ArrayList<>();
        int chars = 0;
        for (int k = all.size() - 1; k >= 0; k--) {
            ChatMsg c = all.get(k);
            out.add(0, c);
            chars += c.text == null ? 0 : c.text.length();
            if (k <= lastUser && chars > budgetChars && out.size() > 1) break;
        }
        while (!out.isEmpty() && !"user".equals(out.get(0).role)) out.remove(0);
        return out;
    }

    /** Ids of all calls that have a saved result in this list. */
    public static Set<String> answeredIds(List<MessageEntity> list) {
        Set<String> ids = new HashSet<>();
        for (MessageEntity m : list) if ("tool".equals(m.role) && m.toolCallId != null) ids.add(m.toolCallId);
        return ids;
    }
}
