package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.db.SessionEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Listener {
        void onOpen(SessionEntity s);

        void onMenu(View anchor, SessionEntity s);
    }

    private static final class Row {
        final String header;
        final SessionEntity session;

        Row(String header, SessionEntity session) {
            this.header = header;
            this.session = session;
        }
    }

    private final Context ctx;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private long currentId = 0;
    private List<SessionEntity> last = new ArrayList<>();

    public SessionAdapter(Context ctx, Listener l) {
        this.ctx = ctx;
        this.listener = l;
    }

    public void submit(List<SessionEntity> sessions) {
        last = sessions == null ? new ArrayList<>() : sessions;
        rebuild();
    }

    public void setCurrent(long id) {
        currentId = id;
        notifyDataSetChanged();
    }

    private void rebuild() {
        rows.clear();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long today = c.getTimeInMillis();
        long day = 24L * 60 * 60 * 1000;
        String lastHeader = null;
        for (SessionEntity s : last) {
            String h;
            if (s.updatedAt >= today) h = ctx.getString(R.string.group_today);
            else if (s.updatedAt >= today - day) h = ctx.getString(R.string.group_yesterday);
            else if (s.updatedAt >= today - 7 * day) h = ctx.getString(R.string.group_week);
            else h = ctx.getString(R.string.group_older);
            if (!h.equals(lastHeader)) {
                rows.add(new Row(h, null));
                lastHeader = h;
            }
            rows.add(new Row(null, s));
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header != null ? 0 : 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == 0) {
            return new HeaderHolder(inf.inflate(R.layout.item_session_header, parent, false));
        }
        return new SessionHolder(inf.inflate(R.layout.item_session, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        Row r = rows.get(position);
        if (h instanceof HeaderHolder) {
            ((HeaderHolder) h).tv.setText(r.header);
            return;
        }
        SessionHolder sh = (SessionHolder) h;
        final SessionEntity s = r.session;
        sh.title.setText(s.title == null || s.title.isEmpty() ? ctx.getString(R.string.new_chat) : s.title);
        sh.root.setBackgroundResource(s.id == currentId ? R.drawable.bg_session_selected : 0);
        sh.root.setOnClickListener(v -> listener.onOpen(s));
        sh.more.setOnClickListener(v -> listener.onMenu(v, s));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView tv;

        HeaderHolder(View v) {
            super(v);
            tv = v.findViewById(R.id.tvGroup);
        }
    }

    static class SessionHolder extends RecyclerView.ViewHolder {
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
}
