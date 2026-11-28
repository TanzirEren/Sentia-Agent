package com.sentia.ai.app.net;

import java.util.ArrayList;
import java.util.List;

public class ChatMsg {
    public static class ImagePart {
        public String mime;
        public String b64;
    }

    /** "user", "assistant" or "tool" */
    public String role;
    public String text = "";
    public List<ImagePart> images = new ArrayList<>();
    /** Assistant messages: the tools the model called in this message. */
    public List<ToolCall> toolCalls = new ArrayList<>();
    /** Tool messages: which call this answers, which tool it was, and whether it failed. */
    public String toolCallId;
    public String toolName;
    public boolean toolError;
}
