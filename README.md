# 🎒 Loadout CLI

> Composable, shareable `.md` system prompts for agentic AI coding systems.  
> A Kotlin Multiplatform (KMP) CLI for composing `AGENTS.md`, `CLAUDE.md`, and other prompt fragments into production-ready `.md` profiles.

---

## 📖 Overview

**Loadout CLI** is a command-line tool implemented with Kotlin Multiplatform to provide a single, well-tested binary on macOS, Linux, and Windows. It encourages a modular, maintainable workflow for system prompts by letting you compose short Markdown fragments into final agent specifications.

Key benefits:
- **CLI-first**: designed to be scriptable and CI-friendly.
- **Composable**: re-use fragments across agents and projects.
- **Open Source**: source-first workflow and easy local builds.

---

## ✨ Features

- Compose multiple `.md` files selectively into one final `AGENTS.md` (Claude is supported as well).
- Simple, consistent CLI.
- Designed for fast local development and CI integration.

---

## 🚀 Recommended Install Methods

### 1) Homebrew (macOS / Linux)
The easiest way for macOS and Linux users is to use Homebrew:

```bash
# Install the CLI
brew tap noheltcj/loadout
brew install noheltcj/loadout

# After install, run:
loadout --help
```

---

### 2) GitHub Releases (Windows / Manual install)
If you prefer manual installation or are on a platform without a package manager, download a release archive:

```bash
# Download and extract (example for macOS x86_64)
curl -L -o loadout.tar.gz \
  "https://github.com/noheltcj/loadout/releases/download/v0.1.0/loadout-cli-mingw-x86.tar.gz"
tar -xzf loadout.tar.gz
# Move the binary into your PATH
sudo mv loadout /usr/local/bin/loadout
loadout --version
```

---

### 3) Build from source (recommended for contributors)

The repository includes Gradle build scripts that support JVM and native targets. From the project root:

```bash
# Clone
git clone https://github.com/noheltcj/loadout.git
cd loadout

# Use the Gradle wrapper to build the native executable
./gradlew build

# After successful build, the release binary will be at:
# build/bin/<your_cpu_architecture>/releaseExecutable/loadout.kexe
./build/bin/<your_cpu_architecture>/releaseExecutable/loadout.kexe --help
```

If the project includes native targets (Kotlin/Native or GraalVM native-image), there will be additional Gradle tasks such as:

```bash
# Example native-ish assemble (task name will depend on the build setup)
./gradlew assemble
# Or a more specific native task (configured in the project)
./gradlew assembleMacosX64
```

---

## ⚡ Quick Start — CLI Usage

It's recommended to add CLAUDE.md, AGENTS.md, and .loadout.json to your .gitignore as these will be managed by the tool:

```gitignore
# Loadout CLI generated files and config
.loadout.json
CLAUDE.md
AGENTS.md
```

```bash
# Display your current loadout
loadout

# List available loadouts
loadout list

# Swap to a loadout
loadout use <name>

# Preview without writing files
loadout use <name> --std-out

# Create an empty loadout
loadout create <name> \
  --desc "Short description"

# Create a loadout with fragments
loadout create <name> \
  --desc "Short description" \
  --fragment fragments/project-structure.md \
  --fragment ~/.loadout/fragments/mvi-architecture.md

# Add a fragment to your loadout
loadout add prompts/new-tool.md --to <name>

# Remove a fragment from your loadout
loadout remove prompts/old-experiment.md --from <name>
```

---

## 🛠 CLI Options
- `--std-out`: Print the new AGENTS.md to standard output; no changes.

(Exact flags depend on the current CLI implementation; use `loadout --help`.)

---

## 🧑‍💻 Development notes

- The CLI codebase uses Kotlin Multiplatform to share logic, tooling, and serializable entities with the backend.
- Prefer the Gradle wrapper (`./gradlew`) so CI and contributors run the same toolchain.
- Keep core composition logic platform-agnostic; CLI plumbing lives under `cli/` module.

---

## 🧑‍🤝‍🧑 Contributing

Contributions are welcome. Here's a typical workflow:

1. Fork the repo.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Run tests: `./gradlew check`.
4. Open a PR with a clear description and small, focused commits.

See `CONTRIBUTING.md` for detailed guidelines (code style, commit conventions, CI checks).

---

## 📜 License

[MIT](LICENSE)

---

**Loadout CLI** — a cross-platform way to manage, compose, and share system prompts for agent agnostic task specialization.
