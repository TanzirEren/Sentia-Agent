package com.sentia.ai.app.workspace;

import android.content.Context;

import com.sentia.ai.app.R;

/** Turns a workspace error into a translated message. */
public final class WsErrors {
    private WsErrors() {}

    public static String text(Context c, Throwable t) {
        int code = t instanceof WsException ? ((WsException) t).code : WsException.IO;
        return text(c, code);
    }

    public static String text(Context c, int code) {
        switch (code) {
            case WsException.ESCAPE: return c.getString(R.string.ws_err_escape);
            case WsException.SYMLINK: return c.getString(R.string.ws_err_symlink);
            case WsException.BAD_NAME: return c.getString(R.string.ws_err_name);
            case WsException.EXISTS: return c.getString(R.string.ws_err_exists);
            case WsException.NOT_FOUND: return c.getString(R.string.ws_err_not_found);
            case WsException.NOT_FILE: return c.getString(R.string.ws_err_not_file);
            case WsException.NOT_DIR: return c.getString(R.string.ws_err_not_dir);
            case WsException.TOO_LARGE: return c.getString(R.string.ws_err_too_large);
            case WsException.BINARY: return c.getString(R.string.ws_err_binary);
            case WsException.NOT_UTF8: return c.getString(R.string.ws_err_not_utf8);
            case WsException.ROOT: return c.getString(R.string.ws_err_root);
            case WsException.INSIDE_SELF: return c.getString(R.string.ws_err_inside);
            case WsException.LIMIT: return c.getString(R.string.ws_err_limit);
            default: return c.getString(R.string.ws_err_io);
        }
    }
}
