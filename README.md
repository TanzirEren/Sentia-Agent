# Sentia (v0.0.2) — Phase 1A + 1B

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

## Added in 1B

- Settings page: AI providers, system prompt editor, theme, text size (with live preview), font weight,
  Markdown / syntax highlight / token count / model name / haptics / auto-scroll / Enter-to-send toggles,
  reply language, include date and time, custom temperature, max reply length, context size, voice language,
  export all chats (JSON or Markdown), saved prompts, clear attachment files, delete all chats,
  privacy policy, licenses, share, reset settings
- About: version, developer (Sh. Tanzir) and four links: Sentia Telegram, website, GitHub, developer Telegram
- AI Providers page: list with on/off switch per provider, add and edit in a full-height dialog
- 30 provider presets plus custom OpenAI-compatible and Anthropic-compatible
- Several API keys per provider, with automatic switch to the next key on HTTP 401, 402, 403 or 429
- Fetch models from the provider (GET /models), FREE tag, filter, select all / select free / clear,
  add model IDs by hand, per-model on/off

Drawer buttons for Profile and Token usage still show "next update" until 1C.

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
