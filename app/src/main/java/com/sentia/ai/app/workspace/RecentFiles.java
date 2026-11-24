package com.sentia.ai.app.workspace;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** The last files opened in the editor, per project. */
public final class RecentFiles {
    private static final int MAX = 12;
    private final SharedPreferences p;

    public RecentFiles(Context c) {
        p = c.getApplicationContext().getSharedPreferences("sentia_recent_files", Context.MODE_PRIVATE);
    }

    public List<String> list(long projectId) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("p" + projectId, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException e) {
            // empty list
        }
        return out;
    }

    public void add(long projectId, String rel) {
        List<String> l = list(projectId);
        l.remove(rel);
        l.add(0, rel);
        while (l.size() > MAX) l.remove(l.size() - 1);
        save(projectId, l);
    }

    public void remove(long projectId, String rel) {
        List<String> l = list(projectId);
        if (l.remove(rel)) save(projectId, l);
    }

    public void clear(long projectId) {
        p.edit().remove("p" + projectId).apply();
    }

    private void save(long projectId, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        p.edit().putString("p" + projectId, a.toString()).apply();
    }
}
