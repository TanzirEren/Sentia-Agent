# Sentia (v0.0.1) — Phase 1A

Mobile AI agent app. Package: `com.sentia.ai.app`. Java + XML, Material 3, min SDK 24.

## What is in 1A

- Chat screen with streaming replies (SSE), typing indicator, stop button
- Markdown output: headings, lists, tables, task lists, links, code blocks with copy button and syntax highlight
- Long-press a message: copy, regenerate, edit and resend, share, select text, save as prompt
- Chat box: auto-expanding input, + attach (photos, files, saved prompts), model picker, voice input, send/stop
- Chat history (Room): resume, rename, delete, search (titles and message text), grouped by date
- Providers: OpenAI-compatible (OpenRouter, OpenAI, Groq, DeepSeek, Gemini, Ollama, any custom URL) and Anthropic
- API keys encrypted with the Android Keystore
- Slim Hind Siliguri font (English and Bangla), light and dark themes, palette #FDFCF8 / #E3DBCC / #101010
- Signing + GitHub Actions build

Drawer buttons for Settings, Profile and Token usage show "next update" until 1B and 1C.

## Build

Push to GitHub and run the **Build Sentia APK** workflow, or open in Android Studio.
Signing steps are in `SIGNING.md`.

## Layout

```
app/src/main/java/com/sentia/ai/app/
  data/        SettingsStore, ProviderStore, PromptStore, SecureStore
  data/db/     Room: sessions, messages
  model/       Provider, ModelEntry, Attachment, SavedPrompt
  net/         OpenAiClient, AnthropicClient (SSE streaming)
  util/        BlockParser, SyntaxHighlighter, image/file helpers
  ui/          MainActivity, ChatViewModel, ChatAdapter, sheets, dialogs
```

## Licenses

Hind Siliguri font: SIL Open Font License (`licenses/OFL-HindSiliguri.txt`).
