Tag: v0.0.6
Title: Sentia 0.0.6 - The AI can read your project files (Phase 2B)

New
- In a project chat with a workspace, the AI can use the Read and ListDir tools to look at your files
- Works with OpenAI-compatible providers (OpenRouter, Groq, DeepSeek, Ollama, ...) and Anthropic
- Tool cards in the chat show what the AI did: tool, file, status, and the full input and output when tapped
- Stop cancels the run cleanly
- Settings > AI agent: turn tools on or off, set the maximum tool rounds
- AI Providers: a Tools tag on each model. Tap it to turn tools off for a model that does not support them

Safe by design
- Tool errors go back to the model so it can fix and retry
- Big tool output is cut, old tool output is shortened to save context
- A tool call is never sent without its result, so Stop does not break the chat

Notes
- The AI cannot change files yet. That comes in 2C, with permission questions and a diff view.
- Database upgraded to version 5. Export your chats before updating (Settings > Export all chats).
