package com.sentia.ai.app.net;

import java.util.ArrayList;
import java.util.List;

public class ChatMsg {
    public static class ImagePart {
        public String mime;
        public String b64;
    }

    public String role;
    public String text = "";
    public List<ImagePart> images = new ArrayList<>();
}
