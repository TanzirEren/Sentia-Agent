package com.sentia.ai.app.agent;

import com.sentia.ai.app.net.ToolCall;
import com.sentia.ai.app.tools.Tool;

import org.json.JSONObject;

/**
 * Asked before a tool that needs permission runs. It is called on the tool thread and may block
 * while the user decides. In 2B only read tools exist, so nothing asks yet; 2C plugs in the dialog.
 */
public interface PermissionGate {
    boolean allow(Tool tool, JSONObject input, ToolCall call);

    PermissionGate ALLOW_ALL = new PermissionGate() {
        @Override
        public boolean allow(Tool tool, JSONObject input, ToolCall call) {
            return true;
        }
    };
}
