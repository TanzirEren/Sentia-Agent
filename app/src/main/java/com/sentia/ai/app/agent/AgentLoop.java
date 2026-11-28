package com.sentia.ai.app.agent;

import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.net.ToolCall;
import com.sentia.ai.app.tools.Tool;
import com.sentia.ai.app.tools.ToolContext;
import com.sentia.ai.app.tools.ToolRegistry;
import com.sentia.ai.app.tools.ToolResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Runs the tool calls of one model reply. The loop itself (ask the model, run tools, ask again) is driven by
 * ChatViewModel, because it owns the stream and the screen. This class makes every call safe:
 * bad arguments, unknown tools, errors, huge output and the Stop button all become a result the model can read.
 */
public final class AgentLoop {
    public static final int MAX_RESULT_CHARS = 30000;
    public static final String CANCELLED_TEXT = "Cancelled by the user before this tool ran.";

    /** Called on the tool thread after each call, in order. */
    public interface Sink {
        void onResult(ToolCall call, ToolResult result, long millis);
    }

    private AgentLoop() {}

    /** Every call gets a result, also after Stop: the model must never see a call without an answer. */
    public static void runBatch(AgentSession s, List<ToolCall> calls, ToolContext ctx, ToolRegistry reg,
                                PermissionGate gate, Sink sink) {
        for (ToolCall call : calls) {
            if (s.cancelled) {
                sink.onResult(call, ToolResult.error(CANCELLED_TEXT), 0);
                continue;
            }
            long t0 = System.nanoTime();
            ToolResult r = runOne(call, ctx, reg, gate);
            long ms = (System.nanoTime() - t0) / 1000000L;
            sink.onResult(call, r, ms);
        }
    }

    static ToolResult runOne(ToolCall call, ToolContext ctx, ToolRegistry reg, PermissionGate gate) {
        try {
            Tool t = reg.find(call.name);
            if (t == null) {
                StringBuilder names = new StringBuilder();
                for (Tool x : reg.all()) {
                    if (names.length() > 0) names.append(", ");
                    names.append(x.name());
                }
                return ToolResult.error("Unknown tool: " + call.name + ". Available tools: " + names + ".");
            }
            JSONObject input;
            try {
                String raw = call.argsJson == null || call.argsJson.trim().isEmpty() ? "{}" : call.argsJson;
                input = new JSONObject(raw);
            } catch (JSONException e) {
                return ToolResult.error("The arguments are not valid JSON (" + e.getMessage()
                        + "). Send one JSON object with the parameters of " + call.name + ".");
            }
            if (t.needsPermission(input) && !gate.allow(t, input, call)) {
                return ToolResult.error("The user did not allow this action.");
            }
            ToolResult r = reg.execute(call.name, input, ctx);
            if (r.text.length() > MAX_RESULT_CHARS) {
                return r.error ? ToolResult.error(ToolResult.cut(r.text, MAX_RESULT_CHARS))
                        : ToolResult.ok(ToolResult.cut(r.text, MAX_RESULT_CHARS), true);
            }
            return r;
        } catch (RuntimeException | Error e) {
            String m = e.getMessage();
            return ToolResult.error("Tool crashed: " + (m == null ? e.getClass().getSimpleName() : m));
        }
    }

    /** The result as a message of the chat (role "tool"). The id is set when it is inserted. */
    public static MessageEntity resultMessage(long sessionId, ToolCall call, ToolResult r, long millis) {
        MessageEntity m = new MessageEntity();
        m.sessionId = sessionId;
        m.role = "tool";
        m.content = r.error ? "Error: " + r.text : r.text;
        m.toolCallId = call.id;
        m.toolName = call.name;
        m.toolError = r.error;
        m.toolMs = millis;
        m.createdAt = System.currentTimeMillis();
        m.status = MessageEntity.DONE;
        return m;
    }
}
