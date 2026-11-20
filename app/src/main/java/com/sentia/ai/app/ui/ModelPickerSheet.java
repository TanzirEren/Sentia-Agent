package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.model.ModelEntry;
import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.List;

/** Bottom sheet listing every enabled provider and its models. */
public final class ModelPickerSheet {
    public interface Callbacks {
        void onSelected();

        void onAddProvider();

        void onEditProvider(Provider p);
    }

    private static final class Item {
        final Provider provider;
        final ModelEntry model;

        Item(Provider provider, ModelEntry model) {
            this.provider = provider;
            this.model = model;
        }
    }

    private ModelPickerSheet() {}

    public static void show(Context ctx, Callbacks cb) {
        final SettingsStore settings = SettingsStore.get(ctx);
        final ProviderStore store = ProviderStore.get(ctx);
        final BottomSheetDialog d = new BottomSheetDialog(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.sheet_model_picker, null);
        RecyclerView rv = v.findViewById(R.id.rvModels);
        TextView empty = v.findViewById(R.id.tvNoProviders);

        final List<Item> items = new ArrayList<>();
        for (Provider p : store.all()) {
            if (!p.enabled) continue;
            items.add(new Item(p, null));
            for (ModelEntry m : p.models) {
                if (m.enabled) items.add(new Item(p, m));
            }
        }
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);

        final String selProvider = settings.selectedProviderId();
        final String selModel = settings.selectedModelId();

        rv.setLayoutManager(new LinearLayoutManager(ctx));
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public int getItemViewType(int position) {
                return items.get(position).model == null ? 0 : 1;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LayoutInflater inf = LayoutInflater.from(parent.getContext());
                if (viewType == 0) {
                    return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_model_provider, parent, false)) {};
                }
                return new RecyclerView.ViewHolder(inf.inflate(R.layout.item_model_row, parent, false)) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
                final Item it = items.get(position);
                View root = h.itemView;
                if (it.model == null) {
                    ((TextView) root.findViewById(R.id.tvProviderName)).setText(it.provider.name);
                    View edit = root.findViewById(R.id.tvProviderEdit);
                    edit.setOnClickListener(x -> {
                        d.dismiss();
                        cb.onEditProvider(it.provider);
                    });
                } else {
                    ((TextView) root.findViewById(R.id.tvModelId)).setText(it.model.id);
                    root.findViewById(R.id.tvFree).setVisibility(it.model.free ? View.VISIBLE : View.GONE);
                    boolean selected = it.provider.id.equals(selProvider) && it.model.id.equals(selModel);
                    ((ImageView) root.findViewById(R.id.ivSelected)).setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                    root.setOnClickListener(x -> {
                        settings.setSelected(it.provider.id, it.model.id);
                        d.dismiss();
                        cb.onSelected();
                    });
                }
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        });

        v.findViewById(R.id.btnAddProvider).setOnClickListener(x -> {
            d.dismiss();
            cb.onAddProvider();
        });
        d.setContentView(v);
        d.show();
    }
}
