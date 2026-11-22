package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.util.ProjectIcons;

/** Create or edit a project: name, description and one of 30 icons. */
public final class ProjectDialog {
    public interface OnSave {
        void onSave(String name, String desc, int icon);
    }

    private ProjectDialog() {}

    public static void show(final Context ctx, FolderEntity existing, final OnSave cb) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_project_form, null);
        final TextInputEditText etName = v.findViewById(R.id.etProjName);
        final TextInputEditText etDesc = v.findViewById(R.id.etProjDesc);
        GridLayout grid = v.findViewById(R.id.iconGrid);
        final int[] selected = {existing == null ? 0 : existing.icon};
        if (existing != null) {
            etName.setText(existing.name);
            etName.setSelection(etName.getText() == null ? 0 : etName.getText().length());
            etDesc.setText(existing.description);
        }

        final int ink = ContextCompat.getColor(ctx, R.color.ink);
        final int onInk = ContextCompat.getColor(ctx, R.color.on_ink);
        final View[] cells = new View[ProjectIcons.COUNT];
        final ImageView[] icons = new ImageView[ProjectIcons.COUNT];
        for (int i = 0; i < ProjectIcons.COUNT; i++) {
            View cell = LayoutInflater.from(ctx).inflate(R.layout.item_project_icon, grid, false);
            View box = cell.findViewById(R.id.iconCell);
            ImageView iv = cell.findViewById(R.id.ivCellIcon);
            iv.setImageResource(ProjectIcons.res(i));
            cells[i] = box;
            icons[i] = iv;
            final int idx = i;
            box.setOnClickListener(x -> {
                selected[0] = idx;
                paint(cells, icons, selected[0], ink, onInk);
            });
            grid.addView(cell);
        }
        paint(cells, icons, selected[0], ink, onInk);

        final boolean edit = existing != null;
        final AlertDialog d = new MaterialAlertDialogBuilder(ctx)
                .setTitle(edit ? R.string.project_dialog_edit : R.string.project_dialog_new)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(edit ? R.string.save : R.string.create, null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = etName.getText() == null ? "" : etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError(ctx.getString(R.string.project_name_required));
                return;
            }
            String desc = etDesc.getText() == null ? "" : etDesc.getText().toString().trim();
            d.dismiss();
            cb.onSave(name, desc, selected[0]);
        });
    }

    private static void paint(View[] cells, ImageView[] icons, int sel, int ink, int onInk) {
        for (int i = 0; i < cells.length; i++) {
            boolean on = i == sel;
            cells[i].setSelected(on);
            ImageViewCompat.setImageTintList(icons[i], ColorStateList.valueOf(on ? onInk : ink));
        }
    }
}
