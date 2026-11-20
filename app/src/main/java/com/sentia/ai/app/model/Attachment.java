package com.sentia.ai.app.model;

public class Attachment {
    public static final String IMAGE = "image";
    public static final String FILE = "file";

    public String kind;
    public String name;
    public String mime;
    public String path;

    public Attachment() {}

    public Attachment(String kind, String name, String mime, String path) {
        this.kind = kind;
        this.name = name;
        this.mime = mime;
        this.path = path;
    }
}
