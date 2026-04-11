# 🎒 Loadout

[![GitHub Release](https://img.shields.io/github/v/release/noheltcj/loadout)](https://github.com/noheltcj/loadout/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Lint](https://github.com/noheltcj/loadout/actions/workflows/lint.yml/badge.svg)](https://github.com/noheltcj/loadout/actions/workflows/lint.yml)
[![macOS](https://img.shields.io/badge/macOS-arm64%20%7C%20x64-blue)](https://github.com/noheltcj/loadout/releases)
[![Linux](https://img.shields.io/badge/Linux-arm64%20%7C%20x64-blue)](https://github.com/noheltcj/loadout/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)

**Composable system prompts for AI coding agents.**

Your `AGENTS.md` shouldn't be a 500-line wall of text that you copy between repos and pray stays consistent. Loadout treats agent guidance the way package managers treat dependencies: small, focused fragments that you compose into purpose-built profiles.

---

## The Problem

Most teams manage their AI agent prompts by hand as one monolithic `CLAUDE.md` or `AGENTS.md` per repo, maintained through copy-paste. This breaks down fast:

- **Drift.** The same coding conventions exist in twelve repos, worded twelve different ways.
- **Bloat.** Every agent sees every instruction, whether it's relevant to its task or not.
- **No specialization.** A code-review agent and a feature-development agent have fundamentally different jobs, but they read the same prompt.

## The Fix

Loadout lets you write prompt guidance once as markdown fragments, then compose them into loadouts — named profiles that produce the final `AGENTS.md`, `CLAUDE.md`, and `GEMINI.md` files your tools already read.

```
fragments/
├── universal/
│   ├── naming-conventions.md
│   └── code-review-ideology.md
├── kotlin/
│   └── kmp-project-structure.md
└── testing/
    └── behavior-first-testing.md
```

```bash
# One loadout for day-to-day development
loadout create engineer --desc "Kotlin-Multiplatform Engineer" \
  --fragment fragments/universal/naming-conventions.md \
  --fragment fragments/kotlin/kmp-project-structure.md \
  --fragment fragments/testing/behavior-first-testing.md

# Another for code review, with different priorities
loadout create code-reviewer \
  --clone engineer \
  --desc "Ideology-Aware Code Reviewer" \
  --fragment fragments/universal/code-review-ideology.md

# Switch contexts in one command
loadout use code-reviewer
```

Each `loadout use` or `loadout sync` regenerates the output files. Your agent reads the right guidance for the right job.

---

## Install

### Homebrew (macOS / Linux)

```bash
brew tap noheltcj/loadout
brew install loadout
```

### GitHub Releases

Prebuilt binaries for macOS (arm64/x64), Linux (arm64/x64), and Windows (x64) are available on the [releases page](https://github.com/noheltcj/loadout/releases).

### Build from Source

```bash
git clone https://github.com/noheltcj/loadout.git
cd loadout

# Supported targets: LinuxArm64, LinuxX64, MacosArm64, MacosX64, MingwX64
./gradlew linkReleaseExecutableMacosArm64

./build/bin/macosArm64/releaseExecutable/loadout.kexe --help
```

---

## Quick Start

```bash
# Initialize Loadout in your project
loadout init

# That's it. You now have:
#   - A starter fragment at fragments/loadout-architect.md
#   - A "default" loadout that includes it
#   - Generated AGENTS.md, CLAUDE.md, and GEMINI.md
```

From here, the workflow is: **write fragments → compose loadouts → sync outputs.**

```bash
# See what's active
loadout

# List all loadouts
loadout list

# Create a new loadout
loadout create <name> --desc "What this profile is for"

# Add or remove fragments
loadout link path/to/fragment.md --to <loadout>
loadout unlink path/to/fragment.md --from <loadout>

# Switch to a different loadout
loadout use <name>

# Regenerate outputs after editing fragment content
loadout sync

# Preview output without writing files
loadout use <name> --std-out

# Delete a loadout
loadout remove <name>
```

---

## How It Works

Loadout manages three things:

| Concept       | What it is                                                                 |
|---------------|---------------------------------------------------------------------------|
| **Fragment**  | A markdown file containing focused guidance on one concern.                |
| **Loadout**   | A named profile that references a set of fragments.                        |
| **Output**    | The generated `AGENTS.md` / `CLAUDE.md` / `GEMINI.md` your agent reads.   |

Fragments live wherever makes sense:
- Simple projects or project-specialized fragments may keep them in-repo under `fragments/`.
- For teams or multi-repository projects, it's recommended to keep fragments in a shared, version-controlled fragment library. This enables sharing of common conventions across projects.

### Shared vs. Local Mode

```bash
loadout init              # shared mode (default) — loadout definitions are committed
loadout init --mode local # local mode — everything stays gitignored
```

Shared mode lets a team standardize on the same set of agent profiles. Local mode is for personal workflows where each developer curates their own.

---

## Why Fragments?

The value compounds as the fragment library grows:

- **Reuse across projects.** Write your naming conventions, architecture rules, or review ideology once. Reference them from any repo.
- **Specialize per task.** A loadout for feature work includes testing guidance. A loadout for code review includes review ideology. Neither carries the other's baggage.
- **Evolve independently.** Update a fragment or checkout a new fragment library commit and `loadout sync` will propagate the change.
- **Keep it readable.** Each fragment is a short, self-contained markdown file focused on a single concern. They should be easy to review and refine.

---

## Development

The CLI is built with Kotlin Multiplatform targeting native executables. The codebase follows a clean-architecture layout:

- `src/commonMain/kotlin/cli/` — Command definitions and output rendering
- `src/commonMain/kotlin/domain/` — Entities, services, and use cases
- `src/commonMain/kotlin/data/` — Repository implementations and serialization
- `src/nativeMain/kotlin/` — Native entrypoints and platform wiring

```bash
# Run the test suite
./gradlew check
```

---

## Contributing

Contributions are welcome.

1. Fork the repo.
2. Create a feature branch: `git checkout -b feature/your-feature`.
3. Run tests: `./gradlew check`.
4. Open a PR with a clear description and small, focused commits.

---

## License

[MIT](LICENSE)
