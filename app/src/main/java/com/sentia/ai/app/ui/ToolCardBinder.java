package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.agent.HistoryBuilder;
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.net.ToolCall;
import com.sentia.ai.app.tools.Tool;
import com.sentia.ai.app.tools.ToolRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Draws the tool cards of one assistant message: tool, short input, status, and details when opened. */
final class ToolCardBinder {
    private static final int SHOW_CHARS = 3000;

    private ToolCardBinder() {}

    /**
     * @param results saved results by call id
     * @param busy    true while the agent is running, so a call without result is "running", not "not run"
     * @param opened  ids of the calls whose details are open
     */
    static void bind(final Context ctx, LinearLayout container, MessageEntity m, final Map<String, MessageEntity> results,
                     final boolean busy, final Set<String> opened) {
        final List<ToolCall> calls = HistoryBuilder.parseCalls(m.toolCallsJson);
        if (calls.isEmpty()) {
            container.setVisibility(View.GONE);
            return;
        }
        container.setVisibility(View.VISIBLE);
        LayoutInflater inf = LayoutInflater.from(ctx);
        while (container.getChildCount() < calls.size()) {
            container.addView(inf.inflate(R.layout.item_tool_card, container, false));
        }
        while (container.getChildCount() > calls.size()) {
            container.removeViewAt(container.getChildCount() - 1);
        }
        for (int i = 0; i < calls.size(); i++) {
            bindOne(ctx, container.getChildAt(i), calls.get(i), results.get(calls.get(i).id), busy, opened);
        }
    }

    private static void bindOne(Context ctx, final View card, final ToolCall call, MessageEntity result, boolean busy,
                                final Set<String> opened) {
        card.setSaveFromParentEnabled(false);
        ImageView icon = card.findViewById(R.id.ivToolIcon);
        TextView name = card.findViewById(R.id.tvToolName);
        TextView summary = card.findViewById(R.id.tvToolSummary);
        ProgressBar pb = card.findViewById(R.id.pbTool);
        ImageView stIcon = card.findViewById(R.id.ivToolStatus);
        TextView stText = card.findViewById(R.id.tvToolStatus);
        ImageView chevron = card.findViewById(R.id.ivToolChevron);
        View body = card.findViewById(R.id.toolBody);

        name.setText(call.name);
        icon.setImageResource(iconFor(call.name));
        String args = call.argsJson == null ? "" : call.argsJson;
        String pretty = args;
        String sum = "";
        try {
            JSONObject in = new JSONObject(args.isEmpty() ? "{}" : args);
            pretty = in.toString(2);
            Tool t = ToolRegistry.get().find(call.name);
            if (t != null) sum = t.summary(in);
        } catch (JSONException e) {
            sum = ctx.getString(R.string.tool_bad_args);
        }
        summary.setText(sum);

        if (result != null) {
            pb.setVisibility(View.GONE);
            stIcon.setVisibility(View.VISIBLE);
            if (result.toolError) {
                stIcon.setImageResource(R.drawable.ic_alert);
                stIcon.setColorFilter(ctx.getColor(R.color.ink));
                stText.setText(R.string.tool_failed);
            } else {
                stIcon.setImageResource(R.drawable.ic_check);
                stIcon.setColorFilter(ctx.getColor(R.color.ink_muted));
                stText.setText(ctx.getString(R.string.tool_ms, result.toolMs));
            }
        } else if (busy) {
            pb.setVisibility(View.VISIBLE);
            stIcon.setVisibility(View.GONE);
            stText.setText(R.string.tool_running);
        } else {
            pb.setVisibility(View.GONE);
            stIcon.setVisibility(View.VISIBLE);
            stIcon.setImageResource(R.drawable.ic_clock);
            stIcon.setColorFilter(ctx.getColor(R.color.ink_faint));
            stText.setText(R.string.tool_not_run);
        }

        final boolean open = call.id != null && opened.contains(call.id);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        chevron.setRotation(open ? 180f : 0f);
        final String fPretty = pretty;
        final MessageEntity fResult = result;
        final Context fCtx = ctx;
        if (open) fillBody(fCtx, card, fPretty, fResult);
        card.findViewById(R.id.toolHeader).setOnClickListener(v -> {
            if (call.id == null) return;
            boolean nowOpen = !opened.contains(call.id);
            if (nowOpen) opened.add(call.id);
            else opened.remove(call.id);
            card.findViewById(R.id.toolBody).setVisibility(nowOpen ? View.VISIBLE : View.GONE);
            ((ImageView) card.findViewById(R.id.ivToolChevron)).setRotation(nowOpen ? 180f : 0f);
            if (nowOpen) fillBody(fCtx, card, fPretty, fResult);
        });
    }

    /** The details are built only for open cards. */
    private static void fillBody(Context ctx, View card, String input, MessageEntity result) {
        ((TextView) card.findViewById(R.id.tvToolIn)).setText(input);
        TextView tvOut = card.findViewById(R.id.tvToolOut);
        if (result == null) {
            tvOut.setText(R.string.tool_no_output);
            return;
        }
        String out = result.content == null ? "" : result.content;
        if (out.length() > SHOW_CHARS) out = out.substring(0, SHOW_CHARS) + "\n" + ctx.getString(R.string.tool_cut);
        tvOut.setText(out.isEmpty() ? ctx.getString(R.string.tool_no_output) : out);
    }

    private static int iconFor(String tool) {
        if ("Read".equals(tool)) return R.drawable.ic_file_code;
        if ("ListDir".equals(tool)) return R.drawable.ic_folder;
        return R.drawable.ic_workspace;
    }
}
