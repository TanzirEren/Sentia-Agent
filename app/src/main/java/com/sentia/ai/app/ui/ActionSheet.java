package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.sentia.ai.app.R;

import java.util.List;

/** Generic bottom sheet with a list of icon + label rows. */
public final class ActionSheet {
    public static final class Row {
        public final int icon;
        public final String label;
        public final Runnable action;
        public final Runnable onLongPress;

        public Row(int icon, String label, Runnable action) {
            this(icon, label, action, null);
        }

        public Row(int icon, String label, Runnable action, Runnable onLongPress) {
            this.icon = icon;
            this.label = label;
            this.action = action;
            this.onLongPress = onLongPress;
        }
    }

    private ActionSheet() {}

    public static BottomSheetDialog show(Context ctx, String title, List<Row> rows) {
        BottomSheetDialog d = new BottomSheetDialog(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.sheet_actions, null);
        TextView tvTitle = v.findViewById(R.id.tvSheetTitle);
        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
            tvTitle.setVisibility(View.VISIBLE);
        }
        LinearLayout list = v.findViewById(R.id.actionsList);
        for (Row r : rows) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.item_action_row, list, false);
            ((ImageView) row.findViewById(R.id.ivActionIcon)).setImageResource(r.icon);
            ((TextView) row.findViewById(R.id.tvActionLabel)).setText(r.label);
            row.setOnClickListener(x -> {
                d.dismiss();
                if (r.action != null) r.action.run();
            });
            if (r.onLongPress != null) {
                row.setOnLongClickListener(x -> {
                    d.dismiss();
                    r.onLongPress.run();
                    return true;
                });
            }
            list.addView(row);
        }
        d.setContentView(v);
        d.show();
        return d;
    }
}
