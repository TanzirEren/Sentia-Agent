package com.sentia.ai.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sentia.ai.app.R;
import com.sentia.ai.app.util.Slash;

import java.util.ArrayList;
import java.util.List;

/** Horizontal chips with matching slash commands above the chat box. */
public class SlashAdapter extends RecyclerView.Adapter<SlashAdapter.Holder> {
    public interface OnPick {
        void onPick(Slash.Item item);
    }

    private final OnPick listener;
    private List<Slash.Item> items = new ArrayList<>();

    public SlashAdapter(OnPick l) {
        this.listener = l;
    }

    public void submit(List<Slash.Item> list) {
        items = list == null ? new ArrayList<Slash.Item>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_slash, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final Slash.Item it = items.get(position);
        h.name.setText("/" + it.name);
        h.hint.setText(it.hint);
        h.itemView.setOnClickListener(v -> listener.onPick(it));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, hint;

        Holder(View v) {
            super(v);
            name = v.findViewById(R.id.tvSlashName);
            hint = v.findViewById(R.id.tvSlashHint);
        }
    }
}
