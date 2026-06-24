---
name: explore-codebase
description: Explore the DeepSeek Codex Android app structure — find files, understand architecture, trace data flow.
---

# Explore Codebase

Quickly navigate the DeepSeek Codex Android app source code.

## Architecture overview

```
app/src/main/java/com/example/app/
├── App.kt                    # Application class, holds AppContainer
├── AppContainer.kt           # DI container — creates and wires all dependencies
├── MainActivity.kt           # Single activity with NavHost (chat/history/settings)
├── data/
│   ├── api/DeepSeekClient.kt # HTTP client for DeepSeek API
│   ├── local/
│   │   ├── ConversationStore.kt # JSON file persistence for conversations
│   │   └── SettingsStore.kt     # DataStore preferences for API key/URL/model
│   ├── model/
│   │   ├── ChatModels.kt     # API request/response data classes
│   │   └── ConversationModels.kt # PersistedConversation, ConversationMeta
│   └── repository/
│       ├── ChatRepository.kt # Main chat logic, SSE streaming, tool execution
│       └── SettingsRepository.kt
├── domain/
│   ├── LinuxEnvironment.kt   # Proot-based Linux environment
│   └── ToolExecutor.kt       # Shell command execution, file ops
├── util/
│   └── ConversationManager.kt # In-memory conversation state, message list
└── ui/
    ├── chat/
    │   ├── ChatScreen.kt     # Main chat UI with LazyColumn
    │   ├── ChatViewModel.kt  # Message sending, conversation persistence
    │   └── components/
    │       ├── ChatInputBar.kt
    │       └── MessageBubble.kt
    ├── history/
    │   ├── HistoryScreen.kt
    │   └── HistoryViewModel.kt
    └── settings/
        ├── SettingsScreen.kt
        └── SettingsViewModel.kt
```

## Key patterns

- **ViewModel pattern**: All ViewModels extend `AndroidViewModel`, get dependencies from `(application as App).container`
- **State**: ViewModels expose `StateFlow`, screens collect with `collectAsState()`
- **Navigation**: Compose Navigation with `NavHost`, routes: `chat`, `history`, `settings`
- **Persistence**: Conversation messages serialized as JSON files in `filesDir/conversations/`
- **Workspace isolation**: Each conversation gets `filesDir/workspace/{uuid}/`
- **Thread safety**: `ToolExecutor` uses `AtomicReference<Map>` for tool swapping; `ConversationStore` uses `Mutex`

## File storage layout

```
filesDir/
├── conversations/
│   ├── index.json           # ConversationIndex (lightweight metadata)
│   └── {uuid}.json           # PersistedConversation (full message list)
├── workspace/
│   ├── default/              # Default workspace (no active conversation)
│   └── {uuid}/               # Per-conversation isolated workspace
└── settings.preferences_pb   # DataStore preferences
```

## Search tips

- Grep for `com.example.app` to find all project source files
- UI code is under `ui/` with one subdirectory per screen
- Data layer is under `data/` — models, stores, repositories
- Domain logic (Linux env, tool execution) is under `domain/`
