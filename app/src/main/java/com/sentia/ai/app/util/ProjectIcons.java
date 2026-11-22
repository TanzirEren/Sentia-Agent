package com.sentia.ai.app.util;

import com.sentia.ai.app.R;

/** The 30 ready-made project icons. A project stores only the index. */
public final class ProjectIcons {
    public static final int[] ALL = {
            R.drawable.ic_pj_folder,
            R.drawable.ic_pj_code,
            R.drawable.ic_pj_terminal,
            R.drawable.ic_pj_rocket,
            R.drawable.ic_pj_book,
            R.drawable.ic_pj_bulb,
            R.drawable.ic_pj_star,
            R.drawable.ic_pj_heart,
            R.drawable.ic_pj_globe,
            R.drawable.ic_pj_phone,
            R.drawable.ic_pj_database,
            R.drawable.ic_pj_cloud,
            R.drawable.ic_pj_palette,
            R.drawable.ic_pj_camera,
            R.drawable.ic_pj_music,
            R.drawable.ic_pj_video,
            R.drawable.ic_pj_gamepad,
            R.drawable.ic_pj_bag,
            R.drawable.ic_pj_briefcase,
            R.drawable.ic_pj_cap,
            R.drawable.ic_pj_flask,
            R.drawable.ic_pj_cpu,
            R.drawable.ic_pj_shield,
            R.drawable.ic_pj_bug,
            R.drawable.ic_pj_layers,
            R.drawable.ic_pj_chat,
            R.drawable.ic_pj_pen,
            R.drawable.ic_pj_leaf,
            R.drawable.ic_pj_bolt,
            R.drawable.ic_pj_flag
    };

    public static final int COUNT = ALL.length;

    private ProjectIcons() {}

    public static int res(int index) {
        if (index < 0 || index >= ALL.length) return ALL[0];
        return ALL[index];
    }
}
