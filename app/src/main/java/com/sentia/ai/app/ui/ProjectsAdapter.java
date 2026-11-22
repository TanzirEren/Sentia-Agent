package com.sentia.ai.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.util.ProjectIcons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Project cards on the Projects page. */
public class ProjectsAdapter extends RecyclerView.Adapter<ProjectsAdapter.Holder> {
    public interface Listener {
        void onOpen(FolderEntity f);

        void onMenu(View anchor, FolderEntity f);
    }

    private final Listener listener;
    private List<FolderEntity> items = new ArrayList<>();
    private Map<Long, Integer> chats = new HashMap<>();
    private Map<Long, Integer> files = new HashMap<>();

    public ProjectsAdapter(Listener l) {
        this.listener = l;
    }

    public void submit(List<FolderEntity> list, Map<Long, Integer> chatCounts, Map<Long, Integer> fileCounts) {
        items = list == null ? new ArrayList<FolderEntity>() : list;
        chats = chatCounts;
        files = fileCounts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final FolderEntity f = items.get(position);
        h.icon.setImageResource(ProjectIcons.res(f.icon));
        h.name.setText(f.name);
        boolean hasDesc = f.description != null && !f.description.trim().isEmpty();
        h.desc.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        if (hasDesc) h.desc.setText(f.description.trim());
        Integer c = chats.get(f.id);
        Integer fl = files.get(f.id);
        h.meta.setText(h.itemView.getContext().getString(R.string.project_meta, c == null ? 0 : c, fl == null ? 0 : fl));
        h.pinned.setVisibility(f.pinned ? View.VISIBLE : View.GONE);
        h.root.setOnClickListener(v -> listener.onOpen(f));
        h.more.setOnClickListener(v -> listener.onMenu(v, f));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon, pinned;
        final TextView name, desc, meta;
        final ImageButton more;

        Holder(View v) {
            super(v);
            root = v.findViewById(R.id.projectRoot);
            icon = v.findViewById(R.id.ivIcon);
            pinned = v.findViewById(R.id.ivPinned);
            name = v.findViewById(R.id.tvName);
            desc = v.findViewById(R.id.tvDesc);
            meta = v.findViewById(R.id.tvMeta);
            more = v.findViewById(R.id.btnMore);
        }
    }
}
