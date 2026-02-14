# Rider Worktrees

Git Worktree management plugin for JetBrains IDEs (Rider, IntelliJ IDEA, WebStorm, etc.)

Manage multiple Git worktrees directly from your IDE without touching the command line.

## Features

- **Worktree Tree View** — Collapsible tree showing all worktrees with their changed files and status indicators (added, modified, deleted, untracked)
- **Inline Diff** — Double-click any changed file to see a side-by-side diff (HEAD vs Working Copy)
- **Create / Remove / Open** — Full worktree lifecycle management from the tool window
- **Lock / Unlock / Prune** — Protect worktrees from accidental deletion and clean up stale entries
- **Window Title** — Shows the current branch/worktree name in the IDE title bar for easy identification
- **Auto-detection** — Tool window appears automatically when your project has multiple worktrees
- **Switch Worktrees** — Open any worktree in a new IDE window with a double-click
- **Git Menu Integration** — Access worktree actions from the Git menu and context menus

## Requirements

- JetBrains IDE **2025.1** or later (build 251+)
- Git installed and accessible from the IDE

## Installation

### From JetBrains Marketplace

1. Open **Settings** > **Plugins** > **Marketplace**
2. Search for **"Rider Worktrees"**
3. Click **Install** and restart the IDE

### From Disk

1. Download the latest release from [Releases](https://github.com/TDavtyan-ST/rider-worktree/releases)
2. Open **Settings** > **Plugins** > gear icon > **Install Plugin from Disk...**
3. Select the downloaded `.zip` file and restart the IDE

## Usage

1. Open a project that uses Git worktrees
2. The **Git Worktrees** tool window appears in the bottom panel (auto-shows when multiple worktrees are detected)
3. Expand any worktree node to see its changed files with status icons
4. **Double-click** a file to open the diff viewer (HEAD vs Working Copy)
5. **Double-click** a worktree to open it in a new IDE window
6. **Right-click** for context menu actions (open, remove, lock/unlock)
7. Use the toolbar buttons to create new worktrees, refresh, or prune stale entries

## Building from Source

### Prerequisites

- JDK 21
- Git

### Build

```bash
# macOS (Homebrew)
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew buildPlugin

# Linux / other
./gradlew buildPlugin
```

The plugin zip will be generated at `build/distributions/rider-worktrees-<version>.zip`.

### Run in Sandbox IDE

```bash
./gradlew runIde
```

## Project Structure

```
src/main/kotlin/com/github/tdavtyan/riderworktrees/
  actions/          # Toolbar and menu actions (create, open, remove, lock, etc.)
  model/            # Data classes (WorktreeInfo, FileChange)
  services/         # Git worktree service (command execution, caching)
  settings/         # Plugin settings/configuration
  ui/               # Tool window panel, tree renderer, dialogs, frame title
src/main/resources/
  META-INF/         # plugin.xml, plugin icon
  messages/         # i18n message bundle
```

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Push to the branch and open a Pull Request

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.
