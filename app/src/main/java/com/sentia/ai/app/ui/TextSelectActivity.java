package com.sentia.ai.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.SettingsStore;

/** Full-screen view with selectable text, like Claude's "Select text". */
public class TextSelectActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_select);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);

        final String text = getIntent().getStringExtra("text") == null ? "" : getIntent().getStringExtra("text");
        TextView tv = findViewById(R.id.tvSelectable);
        tv.setTextSize(SettingsStore.get(this).textSizeSp() + 1f);
        tv.setText(text);
        findViewById(R.id.btnSelClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnSelCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Sentia", text));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        });
    }
}
