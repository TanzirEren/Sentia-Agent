package com.sentia.ai.app.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProvidersActivity extends AppCompatActivity {
    private ProviderStore store;
    private RecyclerView rv;
    private View empty;
    private final List<Provider> items = new ArrayList<>();
    private RecyclerView.Adapter<Holder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_providers);
        Bars.apply(this);
        store = ProviderStore.get(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        rv = findViewById(R.id.rvProviders);
        empty = findViewById(R.id.tvEmptyProviders);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerView.Adapter<Holder>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_provider, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull Holder h, int position) {
                final Provider p = items.get(position);
                String initial = p.name == null || p.name.isEmpty() ? "?" : p.name.substring(0, 1).toUpperCase(Locale.ROOT);
                h.avatar.setText(initial);
                h.title.setText(p.name);
                h.sub.setText(getString(R.string.provider_sub, p.enabledModelCount(), p.models.size())
                        + "  \u00b7  " + host(p.baseUrl));
                h.sw.setOnCheckedChangeListener(null);
                h.sw.setChecked(p.enabled);
                h.sw.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
                    p.enabled = checked;
                    store.save(p);
                });
                h.itemView.setOnClickListener(v -> ProviderSheet.show(ProvidersActivity.this, p, ProvidersActivity.this::refresh));
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        rv.setAdapter(adapter);
        findViewById(R.id.btnAddProvider).setOnClickListener(v -> ProviderSheet.show(this, null, this::refresh));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        items.clear();
        items.addAll(store.all());
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? url : h;
        } catch (Exception e) {
            return url;
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView avatar, title, sub;
        final MaterialSwitch sw;

        Holder(View v) {
            super(v);
            avatar = v.findViewById(R.id.tvAvatar);
            title = v.findViewById(R.id.tvProviderTitle);
            sub = v.findViewById(R.id.tvProviderSub);
            sw = v.findViewById(R.id.swProvider);
        }
    }
}
