package com.sentia.ai.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.db.ProjectFileEntity;
import com.sentia.ai.app.util.Fmt;

import java.util.List;

/** Bottom sheet that lists a project's files: switch on/off, remove, add. */
public final class ProjectFilesSheet {
    private ProjectFilesSheet() {}

    public static void show(final AppCompatActivity act, final ProjectRepo repo, long folderId, final Runnable onAdd) {
        final BottomSheetDialog d = new BottomSheetDialog(act);
        final View v = LayoutInflater.from(act).inflate(R.layout.sheet_project_files, null);
        final LinearLayout list = v.findViewById(R.id.filesList);
        final TextView empty = v.findViewById(R.id.tvFilesEmpty);
        v.findViewById(R.id.btnAddFiles).setOnClickListener(x -> onAdd.run());

        final LiveData<List<ProjectFileEntity>> live = repo.files(folderId);
        final Observer<List<ProjectFileEntity>> obs = new Observer<List<ProjectFileEntity>>() {
            @Override
            public void onChanged(List<ProjectFileEntity> files) {
                list.removeAllViews();
                boolean none = files == null || files.isEmpty();
                empty.setVisibility(none ? View.VISIBLE : View.GONE);
                if (none) return;
                for (final ProjectFileEntity f : files) {
                    View row = LayoutInflater.from(act).inflate(R.layout.item_project_file, list, false);
                    ((TextView) row.findViewById(R.id.tvFileName)).setText(f.name);
                    ((TextView) row.findViewById(R.id.tvFileSize)).setText(Fmt.compact(f.size) + " B");
                    MaterialSwitch sw = row.findViewById(R.id.swFile);
                    sw.setChecked(f.enabled);
                    sw.setOnCheckedChangeListener((b, on) -> repo.setFileEnabled(f.id, on));
                    row.findViewById(R.id.btnFileDelete).setOnClickListener(x ->
                            new MaterialAlertDialogBuilder(act)
                                    .setTitle(R.string.file_delete_title)
                                    .setMessage(f.name)
                                    .setNegativeButton(R.string.cancel, null)
                                    .setPositiveButton(R.string.delete, (dd, w) -> repo.deleteFile(f.id))
                                    .show());
                    list.addView(row);
                }
            }
        };
        live.observe(act, obs);
        d.setOnDismissListener(x -> live.removeObserver(obs));
        d.setContentView(v);
        d.show();
    }
}
