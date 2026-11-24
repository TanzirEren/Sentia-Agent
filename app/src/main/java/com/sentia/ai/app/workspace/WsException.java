package com.sentia.ai.app.workspace;

import java.io.IOException;

/** A workspace problem with a stable code, so the UI can show a translated message. */
public class WsException extends IOException {
    public static final int ESCAPE = 1;
    public static final int SYMLINK = 2;
    public static final int BAD_NAME = 3;
    public static final int EXISTS = 4;
    public static final int NOT_FOUND = 5;
    public static final int NOT_FILE = 6;
    public static final int NOT_DIR = 7;
    public static final int TOO_LARGE = 8;
    public static final int BINARY = 9;
    public static final int NOT_UTF8 = 10;
    public static final int IO = 11;
    public static final int ROOT = 12;
    public static final int INSIDE_SELF = 13;
    public static final int LIMIT = 14;

    public final int code;

    public WsException(int code, String message) {
        super(message);
        this.code = code;
    }
}
