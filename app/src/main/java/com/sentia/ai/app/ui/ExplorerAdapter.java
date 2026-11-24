package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.util.Fmt;
import com.sentia.ai.app.workspace.FileKinds;
import com.sentia.ai.app.workspace.WsEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Flat list of the visible tree rows. Indent shows the depth. */
public class ExplorerAdapter extends RecyclerView.Adapter<ExplorerAdapter.VH> {
    /** One visible row. */
    public static final class Node {
        public final WsEntry e;
        public final int depth;
        public boolean expanded;
        /** Search results show the parent folder under the name. */
        public boolean showParent;

        public Node(WsEntry e, int depth) {
            this.e = e;
            this.depth = depth;
        }
    }

    public interface Listener {
        void onClick(Node n);

        void onLongClick(Node n);
    }

    private final Listener listener;
    private List<Node> items = new ArrayList<>();
    private Set<String> selected = new HashSet<>();
    private boolean selecting;

    public ExplorerAdapter(Listener l) {
        this.listener = l;
    }

    public void submit(List<Node> list, boolean selecting, Set<String> selected) {
        this.items = list == null ? new ArrayList<Node>() : list;
        this.selecting = selecting;
        this.selected = selected == null ? new HashSet<String>() : selected;
        notifyDataSetChanged();
    }

    public void setSelection(boolean selecting, Set<String> selected) {
        this.selecting = selecting;
        this.selected = selected == null ? new HashSet<String>() : selected;
        notifyDataSetChanged();
    }

    public List<Node> items() {
        return items;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ws_node, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Node n = items.get(position);
        Context c = h.itemView.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int indent = n.showParent ? 0 : n.depth;
        h.root.setPadding(Math.round((12 + indent * 16) * d), 0, Math.round(12 * d), 0);
        h.name.setText(n.e.name);
        if (n.showParent && !n.e.parent().isEmpty()) {
            h.sub.setText(n.e.parent());
            h.sub.setVisibility(View.VISIBLE);
        } else {
            h.sub.setVisibility(View.GONE);
        }
        if (n.e.dir) {
            h.chevron.setVisibility(n.showParent ? View.INVISIBLE : View.VISIBLE);
            h.chevron.setRotation(n.expanded ? 90f : 0f);
            h.icon.setImageResource(R.drawable.ic_folder);
            h.size.setText("");
        } else {
            h.chevron.setVisibility(View.INVISIBLE);
            h.chevron.setRotation(0f);
            h.icon.setImageResource(iconFor(n.e.name));
            h.size.setText(Fmt.bytes(n.e.size));
        }
        h.check.setVisibility(selecting ? View.VISIBLE : View.GONE);
        h.check.setAlpha(selected.contains(n.e.path) ? 1f : 0.18f);
        h.root.setActivated(selected.contains(n.e.path));
        h.root.setOnClickListener(v -> listener.onClick(n));
        h.root.setOnLongClickListener(v -> {
            listener.onLongClick(n);
            return true;
        });
    }

    public static int iconFor(String name) {
        int kind = FileKinds.kind(name);
        if (kind == FileKinds.IMAGE) return R.drawable.ic_image;
        String e = FileKinds.ext(name);
        if (e.equals("zip") || e.equals("apk") || e.equals("jar") || e.equals("aab")) return R.drawable.ic_zip;
        switch (e) {
            case "java":
            case "xml":
            case "html":
            case "htm":
            case "css":
            case "js":
            case "json":
            case "md":
            case "gradle":
            case "kt":
            case "py":
            case "sh":
            case "yml":
            case "yaml":
                return R.drawable.ic_file_code;
            default:
                return R.drawable.ic_file;
        }
    }

    static final class VH extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final ImageView chevron, icon, check;
        final TextView name, sub, size;

        VH(View v) {
            super(v);
            root = v.findViewById(R.id.nodeRoot);
            chevron = v.findViewById(R.id.ivChevron);
            icon = v.findViewById(R.id.ivNodeIcon);
            check = v.findViewById(R.id.ivNodeCheck);
            name = v.findViewById(R.id.tvNodeName);
            sub = v.findViewById(R.id.tvNodeSub);
            size = v.findViewById(R.id.tvNodeSize);
        }
    }
}
