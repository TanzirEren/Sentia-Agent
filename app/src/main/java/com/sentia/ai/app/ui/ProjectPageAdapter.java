package com.sentia.ai.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.ProjectFileEntity;
import com.sentia.ai.app.data.db.SessionEntity;
import com.sentia.ai.app.util.Fmt;
import com.sentia.ai.app.util.ProjectContext;
import com.sentia.ai.app.util.ProjectIcons;

import java.util.ArrayList;
import java.util.List;

/** The project page list: a header (icon, boxes, tools), a CHATS label, then the chats. */
public class ProjectPageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onFiles();

        void onInstructions();

        void onMemory();

        void onCommands();

        void onTogglePlan();

        void onBrief();

        void onOpenChat(SessionEntity s);

        void onChatMenu(View anchor, SessionEntity s);
    }

    private static final int T_HEADER = 0, T_LABEL = 1, T_CHAT = 2, T_EMPTY = 3;

    private final Listener listener;
    private FolderEntity folder;
    private List<SessionEntity> chats = new ArrayList<>();
    private List<ProjectFileEntity> files = new ArrayList<>();

    public ProjectPageAdapter(Listener l) {
        this.listener = l;
    }

    public void submit(FolderEntity f, List<SessionEntity> c, List<ProjectFileEntity> fl) {
        folder = f;
        chats = c == null ? new ArrayList<SessionEntity>() : c;
        files = fl == null ? new ArrayList<ProjectFileEntity>() : fl;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return T_HEADER;
        if (position == 1) return T_LABEL;
        return chats.isEmpty() ? T_EMPTY : T_CHAT;
    }

    @Override
    public int getItemCount() {
        if (folder == null) return 0;
        return 2 + (chats.isEmpty() ? 1 : chats.size());
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (type) {
            case T_HEADER:
                return new HeaderHolder(inf.inflate(R.layout.item_project_header, parent, false));
            case T_LABEL:
                return new TextHolder(inf.inflate(R.layout.item_session_header, parent, false), R.id.tvGroup);
            case T_CHAT:
                return new ChatHolder(inf.inflate(R.layout.item_project_chat, parent, false));
            default:
                return new TextHolder(inf.inflate(R.layout.item_drawer_hint, parent, false), R.id.tvDrawerHint);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        Context c = h.itemView.getContext();
        if (h instanceof HeaderHolder) {
            bindHeader((HeaderHolder) h, c);
        } else if (h instanceof ChatHolder) {
            final SessionEntity s = chats.get(position - 2);
            ChatHolder ch = (ChatHolder) h;
            ch.title.setText(s.title == null || s.title.isEmpty() ? c.getString(R.string.new_chat) : s.title);
            ch.date.setText(DateUtils.getRelativeTimeSpanString(s.updatedAt, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
            ch.pinned.setVisibility(s.pinned ? View.VISIBLE : View.GONE);
            ch.root.setOnClickListener(v -> listener.onOpenChat(s));
            ch.more.setOnClickListener(v -> listener.onChatMenu(v, s));
        } else if (h instanceof TextHolder) {
            TextHolder t = (TextHolder) h;
            if (getItemViewType(position) == T_LABEL) {
                t.tv.setText(R.string.project_chats_title);
                t.tv.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 6));
            } else {
                t.tv.setText(R.string.project_no_chats);
                t.tv.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 8));
            }
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private void bindHeader(HeaderHolder h, Context c) {
        h.icon.setImageResource(ProjectIcons.res(folder.icon));
        h.name.setText(folder.name);
        boolean hasDesc = folder.description != null && !folder.description.trim().isEmpty();
        h.desc.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        if (hasDesc) h.desc.setText(folder.description.trim());

        int enabled = 0;
        for (ProjectFileEntity f : files) if (f.enabled) enabled++;
        h.filesSub.setText(files.isEmpty() ? c.getString(R.string.files_none) : c.getString(R.string.files_count, files.size()));
        boolean hasInstr = folder.instructions != null && !folder.instructions.trim().isEmpty();
        h.instrSub.setText(hasInstr ? folder.instructions.trim().replace('\n', ' ') : c.getString(R.string.instr_none));

        int notes = 0;
        if (folder.memory != null) for (String l : folder.memory.split("\n")) if (!l.trim().isEmpty()) notes++;
        h.tvMemory.setText(notes > 0 ? c.getString(R.string.chip_memory) + " " + notes : c.getString(R.string.chip_memory));
        int cmds = ProjectRepo.parseCommands(folder.commands).size();
        h.tvCommands.setText(cmds > 0 ? c.getString(R.string.chip_commands) + " " + cmds : c.getString(R.string.chip_commands));
        h.tvPlan.setText(R.string.chip_plan);
        h.tvBrief.setText(R.string.chip_brief);

        int ink = ContextCompat.getColor(c, R.color.ink);
        int onInk = ContextCompat.getColor(c, R.color.on_ink);
        boolean plan = folder.planFirst;
        h.chipPlan.setBackgroundResource(plan ? R.drawable.bg_chip_on : R.drawable.bg_chip_outline);
        h.tvPlan.setTextColor(plan ? onInk : ink);
        ImageViewCompat.setImageTintList(h.ivPlan, ColorStateList.valueOf(plan ? onInk : ink));

        h.tvContext.setText(c.getString(R.string.project_context,
                Fmt.compact(ProjectContext.estimateTokens(folder, files))));

        h.boxFiles.setOnClickListener(v -> listener.onFiles());
        h.boxInstr.setOnClickListener(v -> listener.onInstructions());
        h.chipMemory.setOnClickListener(v -> listener.onMemory());
        h.chipCommands.setOnClickListener(v -> listener.onCommands());
        h.chipPlan.setOnClickListener(v -> listener.onTogglePlan());
        h.chipBrief.setOnClickListener(v -> listener.onBrief());
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ImageView icon, ivPlan;
        final TextView name, desc, filesSub, instrSub, tvMemory, tvCommands, tvPlan, tvBrief, tvContext;
        final View boxFiles, boxInstr, chipMemory, chipCommands, chipPlan, chipBrief;

        HeaderHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.ivIcon);
            name = v.findViewById(R.id.tvName);
            desc = v.findViewById(R.id.tvDesc);
            filesSub = v.findViewById(R.id.tvFilesSub);
            instrSub = v.findViewById(R.id.tvInstrSub);
            tvMemory = v.findViewById(R.id.tvChipMemory);
            tvCommands = v.findViewById(R.id.tvChipCommands);
            tvPlan = v.findViewById(R.id.tvChipPlan);
            tvBrief = v.findViewById(R.id.tvChipBrief);
            ivPlan = v.findViewById(R.id.ivChipPlan);
            tvContext = v.findViewById(R.id.tvContext);
            boxFiles = v.findViewById(R.id.boxFiles);
            boxInstr = v.findViewById(R.id.boxInstr);
            chipMemory = v.findViewById(R.id.chipMemory);
            chipCommands = v.findViewById(R.id.chipCommands);
            chipPlan = v.findViewById(R.id.chipPlan);
            chipBrief = v.findViewById(R.id.chipBrief);
        }
    }

    static final class ChatHolder extends RecyclerView.ViewHolder {
        final View root;
        final TextView title, date;
        final ImageView pinned;
        final ImageButton more;

        ChatHolder(View v) {
            super(v);
            root = v.findViewById(R.id.chatRoot);
            title = v.findViewById(R.id.tvChatTitle);
            date = v.findViewById(R.id.tvChatDate);
            pinned = v.findViewById(R.id.ivChatPinned);
            more = v.findViewById(R.id.btnChatMore);
        }
    }

    static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView tv;

        TextHolder(View v, int id) {
            super(v);
            tv = v.findViewById(id);
        }
    }
}
