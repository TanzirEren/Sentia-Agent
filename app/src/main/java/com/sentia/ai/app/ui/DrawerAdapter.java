package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.SessionEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The drawer list: projects (folders with their chats), then the remaining chats grouped by date. */
public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onOpenSession(SessionEntity s);

        void onSessionMenu(View anchor, SessionEntity s);

        void onFolderMenu(View anchor, FolderEntity f);

        void onAddFolder();
    }

    private static final int T_SECTION = 0, T_FOLDER = 1, T_SESSION = 2, T_DATE = 3, T_HINT = 4;

    private static final class Row {
        int type;
        String text;
        boolean showAdd;
        FolderEntity folder;
        int count;
        boolean expanded;
        SessionEntity session;
        boolean indent;
    }

    private final Context ctx;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private final Set<Long> expanded = new HashSet<>();
    private List<FolderEntity> folders = new ArrayList<>();
    private List<SessionEntity> sessions = new ArrayList<>();
    private String query = "";
    private long currentId = 0;

    public DrawerAdapter(Context ctx, Listener l) {
        this.ctx = ctx;
        this.listener = l;
    }

    public void submit(List<FolderEntity> f, List<SessionEntity> s, String q) {
        folders = f == null ? new ArrayList<>() : f;
        sessions = s == null ? new ArrayList<>() : s;
        query = q == null ? "" : q;
        rebuild();
    }

    public void setCurrent(long id) {
        currentId = id;
        for (SessionEntity s : sessions) {
            if (s.id == id && s.folderId != 0) expanded.add(s.folderId);
        }
        rebuild();
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private Row section(String text, boolean add) {
        Row r = new Row();
        r.type = T_SECTION;
        r.text = text;
        r.showAdd = add;
        return r;
    }

    private Row text(int type, String text, boolean indent) {
        Row r = new Row();
        r.type = type;
        r.text = text;
        r.indent = indent;
        return r;
    }

    private Row session(SessionEntity s, boolean indent) {
        Row r = new Row();
        r.type = T_SESSION;
        r.session = s;
        r.indent = indent;
        return r;
    }

    private void rebuild() {
        rows.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);

        if (!q.isEmpty()) {
            for (FolderEntity f : folders) {
                if (f.name != null && f.name.toLowerCase(Locale.ROOT).contains(q)) {
                    Row r = new Row();
                    r.type = T_FOLDER;
                    r.folder = f;
                    r.count = countIn(f.id);
                    rows.add(r);
                }
            }
            for (SessionEntity s : sessions) rows.add(session(s, false));
            notifyDataSetChanged();
            return;
        }

        rows.add(section(ctx.getString(R.string.sec_projects), true));
        if (folders.isEmpty()) rows.add(text(T_HINT, ctx.getString(R.string.no_projects_hint), false));

        Set<Long> folderIds = new HashSet<>();
        for (FolderEntity f : folders) folderIds.add(f.id);
        Map<Long, List<SessionEntity>> byFolder = new HashMap<>();
        List<SessionEntity> unfiled = new ArrayList<>();
        for (SessionEntity s : sessions) {
            if (s.folderId != 0 && folderIds.contains(s.folderId)) {
                byFolder.computeIfAbsent(s.folderId, k -> new ArrayList<>()).add(s);
            } else {
                unfiled.add(s);
            }
        }
        for (FolderEntity f : folders) {
            List<SessionEntity> in = byFolder.get(f.id);
            if (in == null) in = new ArrayList<>();
            boolean exp = expanded.contains(f.id);
            Row fr = new Row();
            fr.type = T_FOLDER;
            fr.folder = f;
            fr.count = in.size();
            fr.expanded = exp;
            rows.add(fr);
            if (exp) {
                if (in.isEmpty()) rows.add(text(T_HINT, ctx.getString(R.string.project_empty), true));
                for (SessionEntity s : in) rows.add(session(s, true));
            }
        }

        if (!unfiled.isEmpty()) {
            rows.add(section(ctx.getString(R.string.sec_chats), false));
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long today = c.getTimeInMillis();
            long day = 24L * 60 * 60 * 1000;
            String last = null;
            for (SessionEntity s : unfiled) {
                String h;
                if (s.updatedAt >= today) h = ctx.getString(R.string.group_today);
                else if (s.updatedAt >= today - day) h = ctx.getString(R.string.group_yesterday);
                else if (s.updatedAt >= today - 7 * day) h = ctx.getString(R.string.group_week);
                else h = ctx.getString(R.string.group_older);
                if (!h.equals(last)) {
                    rows.add(text(T_DATE, h, false));
                    last = h;
                }
                rows.add(session(s, false));
            }
        }
        notifyDataSetChanged();
    }

    private int countIn(long folderId) {
        int n = 0;
        for (SessionEntity s : sessions) if (s.folderId == folderId) n++;
        return n;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case T_SECTION:
                return new SectionHolder(inf.inflate(R.layout.item_drawer_section, parent, false));
            case T_FOLDER:
                return new FolderHolder(inf.inflate(R.layout.item_folder, parent, false));
            case T_SESSION:
                return new SessionHolder(inf.inflate(R.layout.item_session, parent, false));
            case T_DATE:
                return new TextHolder(inf.inflate(R.layout.item_session_header, parent, false), R.id.tvGroup);
            default:
                return new TextHolder(inf.inflate(R.layout.item_drawer_hint, parent, false), R.id.tvDrawerHint);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        final Row r = rows.get(position);
        if (h instanceof SectionHolder) {
            SectionHolder s = (SectionHolder) h;
            s.title.setText(r.text);
            s.add.setVisibility(r.showAdd ? View.VISIBLE : View.GONE);
            s.add.setOnClickListener(v -> listener.onAddFolder());
        } else if (h instanceof FolderHolder) {
            final FolderHolder f = (FolderHolder) h;
            f.name.setText(r.folder.name);
            f.count.setText(String.valueOf(r.count));
            f.chevron.setImageResource(r.expanded ? R.drawable.ic_chevron_down : R.drawable.ic_chevron_right);
            f.root.setOnClickListener(v -> {
                if (expanded.contains(r.folder.id)) expanded.remove(r.folder.id);
                else expanded.add(r.folder.id);
                rebuild();
            });
            f.more.setOnClickListener(v -> listener.onFolderMenu(v, r.folder));
        } else if (h instanceof SessionHolder) {
            SessionHolder s = (SessionHolder) h;
            final SessionEntity se = r.session;
            s.title.setText(se.title == null || se.title.isEmpty() ? ctx.getString(R.string.new_chat) : se.title);
            s.root.setPadding(dp(r.indent ? 36 : 12), 0, 0, 0);
            s.root.setBackgroundResource(se.id == currentId ? R.drawable.bg_session_selected : 0);
            s.root.setOnClickListener(v -> listener.onOpenSession(se));
            s.more.setOnClickListener(v -> listener.onSessionMenu(v, se));
        } else if (h instanceof TextHolder) {
            TextHolder t = (TextHolder) h;
            t.tv.setText(r.text);
            if (r.type == T_HINT) t.tv.setPadding(dp(r.indent ? 36 : 14), dp(2), dp(14), dp(8));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private static final class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageButton add;

        SectionHolder(View v) {
            super(v);
            title = v.findViewById(R.id.tvSection);
            add = v.findViewById(R.id.btnSectionAdd);
        }
    }

    private static final class FolderHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final ImageView chevron;
        final TextView name, count;
        final ImageButton more;

        FolderHolder(View v) {
            super(v);
            root = v.findViewById(R.id.folderRoot);
            chevron = v.findViewById(R.id.ivFolderChevron);
            name = v.findViewById(R.id.tvFolderName);
            count = v.findViewById(R.id.tvFolderCount);
            more = v.findViewById(R.id.btnFolderMore);
        }
    }

    private static final class SessionHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final TextView title;
        final ImageButton more;

        SessionHolder(View v) {
            super(v);
            root = v.findViewById(R.id.sessionRoot);
            title = v.findViewById(R.id.tvSessionTitle);
            more = v.findViewById(R.id.btnSessionMore);
        }
    }

    private static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView tv;

        TextHolder(View v, int id) {
            super(v);
            tv = v.findViewById(id);
        }
    }
}
