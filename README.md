# Sentia (v0.0.5) — Phase 1 + 2A

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


## Fix in 0.0.4.1

- Chat text was almost invisible (about 1 sp). Cause: the settings rows share the same view ids, and when
  Settings was recreated (for example after changing the theme) Android copied one slider value into another,
  so the Text size slider got the Temperature value. Fixed: saved state is no longer shared between rows,
  values are saved only when the user moves a slider, and bad stored values are repaired automatically.
  The same protection was added to Profile fields, API key fields and the project files sheet.

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

## Added in 2A (v0.0.5): workspace, file explorer, code editor

Sentia is still not an agent. 2A builds the base the agent will stand on: a safe workspace, the file tools
and a place to read and edit files by hand.

- **Workspace per project.** Files live inside the app (`files/workspaces/<project id>`). Create a blank one,
  import a folder (Storage Access Framework) or a ZIP. The Project page has a Workspace card (file count, size,
  Open, menu). Import into a non-empty workspace goes into a new sub folder, nothing is overwritten.
  Limits: 5000 files, 100 MB in total, 20 MB per file. A single top folder inside a ZIP is removed.
- **Safe file layer (`WorkspaceFs`).** Every file action goes through it: `..` is refused, symbolic links are
  refused, names are checked, saves are atomic (temp file, then rename), hidden folders (`.git`, `node_modules`,
  `build`, `.gradle`, ...) are left out unless you turn them on. UTF-8, BOM and CRLF are kept when you save.
- **Tool skeleton.** `Tool`, `ToolResult`, `ToolContext`, `ToolRegistry` and two read-only tools:
  `Read` (line numbers, offset and limit, big files are cut with a marker) and `ListDir` (depth 1 to 5).
  Settings > Developer > Tool tester runs them by hand.
- **File explorer.** Tree with expand and collapse, breadcrumb, name filter, new file or folder, rename, duplicate,
  move, delete, copy path, share, multi-select (zip and delete), recent files, show hidden. It is a full screen
  (Project page > Workspace > Open) and a right-side drawer in a project chat (Files button in the top bar).
- **Code editor** (Sora Editor behind `CodeEditorView`). Highlighting for Java, XML, HTML, CSS, JavaScript, JSON
  and Markdown, symbol bar, undo and redo, save with unsaved dot, find and replace (case, regex), go to line, word
  wrap, pinch to zoom the text, draft recovery, check for changes made outside the editor, large file warning
  (read-only option), image viewer with zoom, clear message for binary files.
- **Export.** Share the workspace (or selected items) as a ZIP, or save the ZIP to the phone.
- Settings: Code editor section (text size, word wrap, line numbers, tab size, font, hidden folders), Developer section.
- Database version 4 (migration 3 to 4: three new columns on `folders`, nothing removed).

New libraries: Sora Editor 0.23.4 (`editor`, `language-textmate`), core library desugaring (needed by Sora TextMate),
`androidx.documentfile`. Bundled: TextMate grammars (VS Code, MIT), JetBrains Mono (OFL).

Not verified on a device: everything in 2A. See the delivery notes for the exact checks that were run.
