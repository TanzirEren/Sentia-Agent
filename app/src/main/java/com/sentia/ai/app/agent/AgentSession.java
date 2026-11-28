package com.sentia.ai.app.agent;

import com.sentia.ai.app.model.Provider;

/**
 * One run of the agent: from the user's message until the model gives a final answer.
 * A "round" is one model reply that asked for tools. Fields without volatile are used on the main thread only.
 */
public final class AgentSession {
    public final long sessionId;
    public final long projectId;
    public final String projectName;
    public final Provider provider;
    public final String modelId;
    public final int maxRounds;

    public int rounds;
    /** Set by the Stop button. Read by the tool thread. */
    public volatile boolean cancelled;

    public AgentSession(long sessionId, long projectId, String projectName, Provider provider, String modelId, int maxRounds) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.projectName = projectName == null ? "" : projectName;
        this.provider = provider;
        this.modelId = modelId;
        this.maxRounds = Math.max(1, maxRounds);
    }

    public boolean roundsExhausted() {
        return rounds >= maxRounds;
    }
}
