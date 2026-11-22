# Sentia (v0.0.4) — Phase 1A + 1B + 1C + 1D

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

## Added in 1C

- Projects (folders) in the drawer: create, rename, delete (keep or delete chats), export as Markdown,
  start a new chat inside a project, per-project instructions sent with every chat in it
- Chat menu (three dots): rename, move to project, export (share as text, Markdown file, JSON file), delete
- Profile page: photo, name, role, about, languages, skills, interests, current projects, tone, answer style,
  custom instructions, on/off switch, preview of what the AI sees, import and export as JSON, clear.
  Saved on the phone and added to the system prompt. The empty chat screen greets you by name.
- Token page: current model, balance (OpenRouter and DeepSeek report it, others show the infinity icon),
  usage for today / 7 days / 30 days / all time, input and output split, replies and average per reply,
  7-day bar chart, usage by model, tokens in the open chat
- Database upgraded to version 2 with a migration, so existing chats are kept


## Added in 1D (v0.0.4)

- New brand: the S-prompt logo. App icon (adaptive, round, themed monochrome), animated splash screen
  (shell pops in, the prompt slides in, the cursor blinks, SENTIA in Poppins), logo on the empty chat
  screen and in the side menu. Logo colors follow the theme: ivory in dark mode, black in light mode.
- Projects rebuilt. Side menu: New chat, then Projects (opens the Projects page), then pinned projects
  with their icon, pinned chats, and the other chats grouped by date.
- Projects page: project cards with icon, description, chat and file counts, search, + to create.
  Create or edit dialog: name, description and one of 30 ready-made icons.
- Project page: Files box (txt, md, py and other text files), Instructions box, tools row
  (Memory, Commands, Plan first, Brief), context size, chats list (pinned first), New chat button,
  pin project, edit, export, delete. Chats can be pinned, renamed, moved or deleted here.
- 5 advanced project features:
  1. Smart file context: small file sets are sent in full; large sets are indexed and only the
     excerpts that match your question (BM25 ranking, offline) are sent.
  2. Project memory: notes the AI saves by itself (hidden tag) plus notes you write. Sent in every chat.
  3. Slash commands: 6 built-in (/plan /review /explain /fix /tests /summarize) and your own, with
     suggestion chips while you type / in a project chat.
  4. Plan-first mode: the AI proposes a short plan and waits for your OK before big tasks.
  5. Project brief: the AI summarizes the project from its chats; you choose whether to save it to memory.
- Pin chats from the side menu (three dots) as well.
- Room database version 3 with an exact migration 2 to 3 (new project columns and project_files table).
  Export your chats first (Settings > Export all chats) before updating.
- Fonts: Poppins (OFL) for the splash screen. See licenses/.

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
- Poppins font (splash screen): SIL Open Font License 1.1, see `licenses/OFL-Poppins.txt`
