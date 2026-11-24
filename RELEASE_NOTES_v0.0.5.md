Tag: v0.0.5
Title: Sentia 0.0.5 - Workspace, file explorer and code editor (Phase 2A)

New
- Workspace for every project: blank, import a folder, or import a ZIP
- File explorer (full screen and a right drawer in project chats): tree, search, new, rename, duplicate, move, delete, share, multi-select, recent files
- Code editor with syntax highlight, find and replace, go to line, symbol bar, undo and redo, draft recovery
- Image viewer with zoom
- Export workspace as ZIP (share or save)
- Read and ListDir tools (not used by the AI yet) and a Tool tester in Settings > Developer
- Settings: code editor options

Safe by design
- Files cannot leave the workspace: .. and symbolic links are refused, saves are atomic

Notes
- Database upgraded to version 4. Export your chats before updating (Settings > Export all chats).
- The AI cannot use files yet. That comes in 2B and 2C.
