# 🎒 Loadout CLI

[![GitHub Release](https://img.shields.io/github/v/release/noheltcj/loadout)](https://github.com/noheltcj/loadout/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Lint](https://github.com/noheltcj/loadout/actions/workflows/lint.yml/badge.svg)](https://github.com/noheltcj/loadout/actions/workflows/lint.yml)
[![macOS](https://img.shields.io/badge/macOS-arm64%20%7C%20x64-blue)](https://github.com/noheltcj/loadout/releases)
[![Linux](https://img.shields.io/badge/Linux-arm64%20%7C%20x64-blue)](https://github.com/noheltcj/loadout/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)

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

# Use the Gradle wrapper to assemble the correct release executable for your system
# Supported Architectures: LinuxArm64, LinuxX64, MacosArm64, MacosX64, and MingwX64
./gradlew linkReleaseExecutable<YourTargetArchitecture>

# After successful build, the release binary will be available
./build/bin/<targetArchitectureCamelCase>/releaseExecutable/loadout.kexe --help
```

---

## ⚡ Quick Start — CLI Usage

Before using Loadout in a project, run the init command:
```bash
# Initialize with shared loadouts (default, recommended for teams)
loadout init

# Or initialize with local-only configuration
loadout init --mode local
```

The `init` command does the following:
1. **Configures `.gitignore`** with appropriate patterns for your chosen mode
2. **Creates a starter fragment** at `fragments/loadout-architect.md` (if it doesn't exist)
3. **Creates a "default" loadout** with the starter fragment and activates it (if no loadouts exist)

**Modes:**
- **Shared mode** (default): Generated files are ignored, but loadout definitions (`.loadouts/`, `fragments/`) are committed and shared with your team
- **Local mode**: Everything including loadout configurations is ignored and remains local to each developer

```bash
# Display your current loadout
loadout

# Initialize project for Loadout
loadout init                    # shared mode (default)
loadout init --mode local       # local-only mode

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
loadout add path/to/fragments/desired_fragment.md --to <loadout_name>

# Remove a fragment from your loadout
loadout remove path/to/fragments/undesired_fragment.md --from <loadout_name>

# After modifying a loadout or the content of a fragment, don't forget to sync your changes:
loadout sync
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
