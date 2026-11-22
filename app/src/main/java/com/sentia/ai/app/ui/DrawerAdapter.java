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
import com.sentia.ai.app.util.ProjectIcons;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The side menu list: pinned projects (icon and name), pinned chats, then the other chats
 * grouped by date. Chats that live inside a project are shown on that project's page.
 */
public class DrawerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onOpenSession(SessionEntity s);

        void onSessionMenu(View anchor, SessionEntity s);

        void onOpenProject(FolderEntity f);

        void onProjectMenu(View anchor, FolderEntity f);
    }

    private static final int T_SECTION = 0, T_PROJECT = 1, T_SESSION = 2, T_DATE = 3;

    private static final class Row {
        int type;
        String text;
        FolderEntity folder;
        SessionEntity session;
    }

    private final Context ctx;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private List<FolderEntity> folders = new ArrayList<>();
    private List<SessionEntity> sessions = new ArrayList<>();
    private String query = "";
    private long currentId = 0;

    public DrawerAdapter(Context ctx, Listener l) {
        this.ctx = ctx;
        this.listener = l;
    }

    public void submit(List<FolderEntity> f, List<SessionEntity> s, String q) {
        folders = f == null ? new ArrayList<FolderEntity>() : f;
        sessions = s == null ? new ArrayList<SessionEntity>() : s;
        query = q == null ? "" : q;
        rebuild();
    }

    public void setCurrent(long id) {
        currentId = id;
        rebuild();
    }

    private Row text(int type, String text) {
        Row r = new Row();
        r.type = type;
        r.text = text;
        return r;
    }

    private Row session(SessionEntity s) {
        Row r = new Row();
        r.type = T_SESSION;
        r.session = s;
        return r;
    }

    private Row project(FolderEntity f) {
        Row r = new Row();
        r.type = T_PROJECT;
        r.folder = f;
        return r;
    }

    private void rebuild() {
        rows.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);

        if (!q.isEmpty()) {
            for (FolderEntity f : folders) {
                if (f.name != null && f.name.toLowerCase(Locale.ROOT).contains(q)) rows.add(project(f));
            }
            for (SessionEntity s : sessions) rows.add(session(s));
            notifyDataSetChanged();
            return;
        }

        for (FolderEntity f : folders) if (f.pinned) rows.add(project(f));

        Set<Long> folderIds = new HashSet<>();
        for (FolderEntity f : folders) folderIds.add(f.id);
        List<SessionEntity> pinned = new ArrayList<>();
        List<SessionEntity> unfiled = new ArrayList<>();
        for (SessionEntity s : sessions) {
            boolean inProject = s.folderId != 0 && folderIds.contains(s.folderId);
            if (inProject) continue;
            if (s.pinned) pinned.add(s);
            else unfiled.add(s);
        }

        if (!pinned.isEmpty()) {
            rows.add(text(T_SECTION, ctx.getString(R.string.sec_pinned)));
            for (SessionEntity s : pinned) rows.add(session(s));
        }
        if (!unfiled.isEmpty()) {
            rows.add(text(T_SECTION, ctx.getString(R.string.sec_chats)));
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
                    rows.add(text(T_DATE, h));
                    last = h;
                }
                rows.add(session(s));
            }
        }
        notifyDataSetChanged();
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
            case T_PROJECT:
                return new ProjectHolder(inf.inflate(R.layout.item_drawer_project, parent, false));
            case T_SESSION:
                return new SessionHolder(inf.inflate(R.layout.item_session, parent, false));
            default:
                return new TextHolder(inf.inflate(R.layout.item_session_header, parent, false), R.id.tvGroup);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        final Row r = rows.get(position);
        if (h instanceof SectionHolder) {
            SectionHolder s = (SectionHolder) h;
            s.title.setText(r.text);
            s.add.setVisibility(View.GONE);
        } else if (h instanceof ProjectHolder) {
            ProjectHolder p = (ProjectHolder) h;
            p.icon.setImageResource(ProjectIcons.res(r.folder.icon));
            p.name.setText(r.folder.name);
            p.root.setOnClickListener(v -> listener.onOpenProject(r.folder));
            p.more.setOnClickListener(v -> listener.onProjectMenu(v, r.folder));
        } else if (h instanceof SessionHolder) {
            SessionHolder s = (SessionHolder) h;
            final SessionEntity se = r.session;
            s.title.setText(se.title == null || se.title.isEmpty() ? ctx.getString(R.string.new_chat) : se.title);
            s.root.setBackgroundResource(se.id == currentId ? R.drawable.bg_session_selected : 0);
            s.root.setOnClickListener(v -> listener.onOpenSession(se));
            s.more.setOnClickListener(v -> listener.onSessionMenu(v, se));
        } else if (h instanceof TextHolder) {
            ((TextHolder) h).tv.setText(r.text);
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

    private static final class ProjectHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView name;
        final ImageButton more;

        ProjectHolder(View v) {
            super(v);
            root = v.findViewById(R.id.projectRoot);
            icon = v.findViewById(R.id.ivProjIcon);
            name = v.findViewById(R.id.tvProjName);
            more = v.findViewById(R.id.btnProjMore);
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
