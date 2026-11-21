package com.sentia.ai.app.net;

import android.net.Uri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sentia.ai.app.model.Provider;

import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

/** Reads the account balance from providers that offer it (OpenRouter, DeepSeek). Call off the main thread. */
public final class BalanceFetcher {
    public static final class Balance {
        /** false when this provider has no balance endpoint */
        public final boolean supported;
        public final boolean unlimited;
        public final String text;
        public final String detail;
        public final String error;

        Balance(boolean supported, boolean unlimited, String text, String detail, String error) {
            this.supported = supported;
            this.unlimited = unlimited;
            this.text = text;
            this.detail = detail;
            this.error = error;
        }
    }

    private BalanceFetcher() {}

    public static Balance fetch(Provider p, String apiKey) {
        String base = p.baseUrl == null ? "" : p.baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.contains("openrouter.ai")) return openRouter(base, apiKey);
        if (base.contains("deepseek.com")) return deepSeek(base, apiKey);
        return new Balance(false, false, "", "", null);
    }

    private static JsonObject get(String url, String key) throws Exception {
        Request req = new Request.Builder().url(url).get().header("Authorization", "Bearer " + key).build();
        try (Response r = Http.CLIENT.newCall(req).execute()) {
            String body = r.body() == null ? "" : r.body().string();
            if (!r.isSuccessful()) {
                String d = ApiErrors.extract(body);
                throw new Exception("HTTP " + r.code() + (d.isEmpty() ? "" : ": " + d));
            }
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static double num(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return 0;
        try {
            return o.get(k).getAsDouble();
        } catch (Exception e) {
            return 0;
        }
    }

    private static Balance openRouter(String base, String key) {
        try {
            // Account credits first. This is the real balance for prepaid accounts.
            try {
                JsonObject c = get(base + "/credits", key).getAsJsonObject("data");
                double total = num(c, "total_credits");
                double used = num(c, "total_usage");
                double left = total - used;
                String text = String.format(Locale.US, "$%.2f left", Math.max(0, left));
                String detail = String.format(Locale.US, "Used $%.2f of $%.2f in credits", used, total);
                return new Balance(true, false, text, detail, null);
            } catch (Exception ignored) {
                // fall through to the key endpoint
            }
            JsonObject k = get(base + "/key", key).getAsJsonObject("data");
            double usage = num(k, "usage");
            boolean noLimit = !k.has("limit") || k.get("limit").isJsonNull();
            if (noLimit) {
                return new Balance(true, true, "Unlimited",
                        String.format(Locale.US, "No limit on this key. Used $%.2f", usage), null);
            }
            double limit = num(k, "limit");
            double left = k.has("limit_remaining") && !k.get("limit_remaining").isJsonNull()
                    ? num(k, "limit_remaining") : limit - usage;
            return new Balance(true, false, String.format(Locale.US, "$%.2f left", Math.max(0, left)),
                    String.format(Locale.US, "Used $%.2f of the $%.2f key limit", usage, limit), null);
        } catch (Exception e) {
            return new Balance(true, false, "", "", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static Balance deepSeek(String base, String key) {
        try {
            Uri u = Uri.parse(base);
            String root = u.getScheme() + "://" + u.getHost();
            JsonObject o = get(root + "/user/balance", key);
            JsonArray arr = o.has("balance_infos") && o.get("balance_infos").isJsonArray()
                    ? o.getAsJsonArray("balance_infos") : new JsonArray();
            if (arr.size() == 0) return new Balance(true, false, "", "", "No balance information returned");
            StringBuilder text = new StringBuilder();
            for (JsonElement el : arr) {
                JsonObject b = el.getAsJsonObject();
                if (text.length() > 0) text.append("  \u00b7  ");
                text.append(b.get("total_balance").getAsString()).append(' ').append(b.get("currency").getAsString());
            }
            boolean avail = !o.has("is_available") || o.get("is_available").getAsBoolean();
            return new Balance(true, false, text + " left",
                    avail ? "Account is active" : "Balance is too low to make requests", null);
        } catch (Exception e) {
            return new Balance(true, false, "", "", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
