package com.sentia.ai.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.model.Attachment;
import com.sentia.ai.app.util.BlockParser;
import com.sentia.ai.app.util.SyntaxHighlighter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class ChatAdapter extends ListAdapter<MessageEntity, RecyclerView.ViewHolder> {

    public interface Listener {
        void onActions(MessageEntity m);

        void onCopy(MessageEntity m);

        void onRegenerate(MessageEntity m);

        void onRetry(MessageEntity m);
    }

    private static final int T_USER = 0, T_AI = 1, T_ERR = 2;
    private static final String PAYLOAD_CONTENT = "content";
    private static final String PAYLOAD_FOOTER = "footer";

    private static final DiffUtil.ItemCallback<MessageEntity> DIFF = new DiffUtil.ItemCallback<MessageEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull MessageEntity a, @NonNull MessageEntity b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull MessageEntity a, @NonNull MessageEntity b) {
            return eq(a.role, b.role) && eq(a.content, b.content) && a.status == b.status
                    && a.tokensOut == b.tokensOut && eq(a.attachmentsJson, b.attachmentsJson);
        }

        @Override
        public Object getChangePayload(@NonNull MessageEntity a, @NonNull MessageEntity b) {
            if (eq(a.role, b.role) && a.status == b.status) return PAYLOAD_CONTENT;
            return null;
        }
    };

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private final Context ctx;
    private final Listener listener;
    private final SettingsStore settings;
    private final Markwon markwon;
    private final SyntaxHighlighter highlighter;
    private final Gson gson = new Gson();
    private long lastAssistantId = -1;
    private final Typeface tfLight;
    private final Typeface tfRegular;

    public ChatAdapter(Context ctx, Listener listener) {
        super(DIFF);
        this.ctx = ctx;
        this.listener = listener;
        this.settings = SettingsStore.get(ctx);
        this.highlighter = new SyntaxHighlighter(ctx);
        this.markwon = buildMarkwon(ctx);
        this.tfLight = ResourcesCompat.getFont(ctx, R.font.hind_light);
        this.tfRegular = ResourcesCompat.getFont(ctx, R.font.hind_regular);
    }

    private Markwon buildMarkwon(Context c) {
        final int link = ContextCompat.getColor(c, R.color.link);
        final int codeText = ContextCompat.getColor(c, R.color.ink);
        final int codeBg = ContextCompat.getColor(c, R.color.code_bg);
        final int line = ContextCompat.getColor(c, R.color.line);
        return Markwon.builder(c)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(c))
                .usePlugin(TaskListPlugin.create(c))
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.linkColor(link)
                                .codeTextColor(codeText)
                                .codeBackgroundColor(codeBg)
                                .codeTypeface(Typeface.MONOSPACE)
                                .blockQuoteColor(line)
                                .thematicBreakColor(line)
                                .headingBreakHeight(0)
                                .headingTextSizeMultipliers(new float[]{1.4f, 1.25f, 1.12f, 1.06f, 1f, 1f});
                    }

                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        // keep defaults
                    }
                })
                .build();
    }

    public void submit(List<MessageEntity> list, Runnable onCommitted) {
        long newLast = -1;
        for (int i = list.size() - 1; i >= 0; i--) {
            String r = list.get(i).role;
            if ("assistant".equals(r)) {
                newLast = list.get(i).id;
                break;
            }
        }
        final long oldLast = lastAssistantId;
        lastAssistantId = newLast;
        submitList(new ArrayList<>(list), () -> {
            if (oldLast != -1 && oldLast != lastAssistantId) {
                List<MessageEntity> cur = getCurrentList();
                for (int i = 0; i < cur.size(); i++) {
                    if (cur.get(i).id == oldLast) {
                        notifyItemChanged(i, PAYLOAD_FOOTER);
                        break;
                    }
                }
            }
            if (onCommitted != null) onCommitted.run();
        });
    }

    @Override
    public int getItemViewType(int position) {
        String r = getItem(position).role;
        if ("user".equals(r)) return T_USER;
        if ("error".equals(r)) return T_ERR;
        return T_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case T_USER:
                return new UserHolder(inf.inflate(R.layout.item_message_user, parent, false));
            case T_ERR:
                return new ErrorHolder(inf.inflate(R.layout.item_message_error, parent, false));
            default:
                return new AiHolder(inf.inflate(R.layout.item_message_ai, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        MessageEntity m = getItem(position);
        if (h instanceof UserHolder) ((UserHolder) h).bind(m);
        else if (h instanceof ErrorHolder) ((ErrorHolder) h).bind(m);
        else ((AiHolder) h).bind(m, true);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(h, position);
            return;
        }
        if (h instanceof AiHolder) {
            MessageEntity m = getItem(position);
            if (payloads.contains(PAYLOAD_FOOTER)) ((AiHolder) h).bindFooter(m);
            if (payloads.contains(PAYLOAD_CONTENT)) ((AiHolder) h).bindContent(m);
        } else {
            onBindViewHolder(h, position);
        }
    }

    private float textSize() {
        return settings.textSizeSp();
    }

    private void applyFont(TextView tv) {
        Typeface tf = settings.fontWeight() == 1 ? tfRegular : tfLight;
        if (tf != null) tv.setTypeface(tf);
    }

    private void haptic(View v) {
        if (settings.haptics()) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    // ------------------------------------------------------------------ holders

    class UserHolder extends RecyclerView.ViewHolder {
        final TextView tv;
        final TextView att;

        UserHolder(View v) {
            super(v);
            tv = v.findViewById(R.id.tvUser);
            att = v.findViewById(R.id.tvAttachments);
            int max = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.82f);
            tv.setMaxWidth(max);
        }

        void bind(MessageEntity m) {
            tv.setTextSize(textSize());
            applyFont(tv);
            tv.setText(m.content);
            List<Attachment> atts = parse(m.attachmentsJson);
            if (atts.isEmpty()) {
                att.setVisibility(View.GONE);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < atts.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(atts.get(i).name);
                }
                att.setText(sb.toString());
                att.setVisibility(View.VISIBLE);
            }
            tv.setOnLongClickListener(v -> {
                haptic(v);
                listener.onActions(m);
                return true;
            });
        }
    }

    class ErrorHolder extends RecyclerView.ViewHolder {
        final TextView tv;
        final View retry;

        ErrorHolder(View v) {
            super(v);
            tv = v.findViewById(R.id.tvError);
            retry = v.findViewById(R.id.btnRetry);
        }

        void bind(MessageEntity m) {
            tv.setText(m.content);
            retry.setOnClickListener(v -> listener.onRetry(m));
        }
    }

    class AiHolder extends RecyclerView.ViewHolder {
        final LinearLayout blocks;
        final View dots;
        final View footer;
        final ImageButton btnCopy, btnRegen, btnMore;
        final TextView meta;
        final List<Integer> types = new ArrayList<>();

        AiHolder(View v) {
            super(v);
            blocks = v.findViewById(R.id.blocks);
            dots = v.findViewById(R.id.dots);
            footer = v.findViewById(R.id.footer);
            btnCopy = v.findViewById(R.id.btnCopy);
            btnRegen = v.findViewById(R.id.btnRegen);
            btnMore = v.findViewById(R.id.btnMore);
            meta = v.findViewById(R.id.tvMeta);
        }

        void bind(MessageEntity m, boolean full) {
            bindContent(m);
            bindFooter(m);
        }

        void bindFooter(MessageEntity m) {
            boolean streaming = m.status == MessageEntity.STREAMING;
            footer.setVisibility(streaming ? View.GONE : View.VISIBLE);
            btnRegen.setVisibility(m.id == lastAssistantId ? View.VISIBLE : View.GONE);
            btnCopy.setOnClickListener(v -> listener.onCopy(m));
            btnRegen.setOnClickListener(v -> listener.onRegenerate(m));
            btnMore.setOnClickListener(v -> listener.onActions(m));
            StringBuilder sb = new StringBuilder();
            if (settings.showModelName() && m.modelLabel != null && !m.modelLabel.isEmpty()) sb.append(m.modelLabel);
            if (settings.showTokens() && m.tokensOut > 0) {
                if (sb.length() > 0) sb.append("  \u00b7  ");
                sb.append(m.tokensOut).append(" tokens");
            }
            if (m.status == MessageEntity.STOPPED) {
                if (sb.length() > 0) sb.append("  \u00b7  ");
                sb.append(ctx.getString(R.string.stopped));
            }
            meta.setText(sb.toString());
        }

        void bindContent(MessageEntity m) {
            String content = m.content == null ? "" : m.content;
            boolean streaming = m.status == MessageEntity.STREAMING;
            dots.setVisibility(streaming && content.isEmpty() ? View.VISIBLE : View.GONE);

            List<BlockParser.Block> parsed = BlockParser.parse(content);
            boolean same = parsed.size() == types.size();
            if (same) {
                for (int i = 0; i < parsed.size(); i++) {
                    if (parsed.get(i).type != types.get(i)) {
                        same = false;
                        break;
                    }
                }
            }
            LayoutInflater inf = LayoutInflater.from(ctx);
            if (!same) {
                blocks.removeAllViews();
                types.clear();
                for (BlockParser.Block b : parsed) {
                    View child = b.type == BlockParser.TEXT
                            ? inf.inflate(R.layout.item_block_text, blocks, false)
                            : inf.inflate(R.layout.item_code_block, blocks, false);
                    blocks.addView(child);
                    types.add(b.type);
                }
            }
            for (int i = 0; i < parsed.size(); i++) {
                BlockParser.Block b = parsed.get(i);
                View child = blocks.getChildAt(i);
                if (b.type == BlockParser.TEXT) bindText((TextView) child, b.text, m);
                else bindCode(child, b, streaming, m);
            }
        }

        private void bindText(TextView tv, String text, MessageEntity m) {
            tv.setTextSize(textSize());
            applyFont(tv);
            boolean md = settings.markdown();
            String key = (md ? "1" : "0") + text;
            if (!key.equals(tv.getTag())) {
                tv.setTag(key);
                if (md) markwon.setMarkdown(tv, text);
                else tv.setText(text);
            }
            tv.setOnLongClickListener(v -> {
                haptic(v);
                listener.onActions(m);
                return true;
            });
        }

        private void bindCode(View root, BlockParser.Block b, boolean streaming, MessageEntity m) {
            TextView lang = root.findViewById(R.id.tvLang);
            TextView code = root.findViewById(R.id.tvCode);
            View copy = root.findViewById(R.id.btnCopyCode);
            final TextView copyLabel = root.findViewById(R.id.tvCopyCode);
            lang.setText(b.lang == null || b.lang.isEmpty() ? "code" : b.lang);
            code.setTextSize(Math.max(10f, textSize() - 1.5f));
            boolean hl = settings.syntaxHighlight() && !(streaming && b.text.length() > 6000);
            String codeKey = (hl ? "1" : "0") + b.text;
            if (!codeKey.equals(code.getTag())) {
                code.setTag(codeKey);
                if (!hl) {
                    code.setText(b.text);
                } else {
                    Spannable sp = highlighter.highlight(b.text, b.lang);
                    code.setText(sp);
                }
            }
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("code", b.text));
                copyLabel.setText(R.string.copied);
                copyLabel.postDelayed(() -> copyLabel.setText(R.string.act_copy), 1500);
            });
            code.setOnLongClickListener(v -> {
                haptic(v);
                listener.onActions(m);
                return true;
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    private List<Attachment> parse(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<Attachment>>() {}.getType();
            List<Attachment> l = gson.fromJson(json, t);
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
