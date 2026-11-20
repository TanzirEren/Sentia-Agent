package com.sentia.ai.app.ui;

import android.graphics.Bitmap;
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
import com.sentia.ai.app.model.Attachment;
import com.sentia.ai.app.util.ImageUtil;

import java.util.List;

public class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.Holder> {
    public interface Listener {
        void onRemove(int index);
    }

    private final List<Attachment> items;
    private final Listener listener;

    public AttachmentAdapter(List<Attachment> items, Listener l) {
        this.items = items;
        this.listener = l;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attachment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Attachment a = items.get(position);
        h.name.setText(a.name);
        if (Attachment.IMAGE.equals(a.kind)) {
            ImageViewCompat.setImageTintList(h.thumb, null);
            Bitmap b = ImageUtil.thumb(a.path, 96);
            if (b != null) h.thumb.setImageBitmap(b);
            else h.thumb.setImageResource(R.drawable.ic_image);
        } else {
            h.thumb.setImageResource(R.drawable.ic_file);
            ImageViewCompat.setImageTintList(h.thumb,
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(h.itemView.getContext(), R.color.ink_muted)));
        }
        h.remove.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onRemove(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView name;
        final ImageButton remove;

        Holder(View v) {
            super(v);
            thumb = v.findViewById(R.id.ivThumb);
            name = v.findViewById(R.id.tvAttName);
            remove = v.findViewById(R.id.btnAttRemove);
        }
    }
}
