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

Loadout lets you write prompt guidance once as markdown fragments, then compose them into loadouts. Loadouts are named profiles that produce the agent files your tools already read: `AGENTS.md`, `CLAUDE.md`, and `GEMINI.md` by default, or [whichever files the repository chooses](#output-files).

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

# In shared mode (the default), that gives you:
#   - An empty, active "default" loadout
#   - Generated AGENTS.md, CLAUDE.md, and GEMINI.md
#   - .githooks/ with post-checkout and post-merge scripts (committed)
#   - git configured to use .githooks/ as the hooks path
#   - Ignore rules for the generated files in .git/info/exclude (.gitignore is untouched)
```

Already have a hand-written `CLAUDE.md` or `AGENTS.md`? `loadout init` backs it up before replacing it and prints the
commands that turn it into a fragment. See [Backups](#backups).

To add guidance, copy fragments from a local Markdown file or a trusted raw HTTPS URL, review them, and link them
explicitly:

```bash
# Remote hosts are denied until the repository trusts them.
loadout config --trust-fragment-host raw.githubusercontent.com

loadout fragment copy \
  https://raw.githubusercontent.com/noheltcj/loadout/refs/heads/main/fragments/universal/uses-unambiguous-modeling.md \
  universal/unambiguous.md

loadout fragment link fragments/universal/unambiguous.md --to default
loadout sync
```

From here, the workflow is: **copy or write fragments → compose loadouts → sync outputs.**

```bash
# See what's active
loadout

# List all loadouts
loadout list

# Create a new loadout
loadout create <name> --desc "Usually the purpose of the loadout / profile"

# Copy and compose fragments
loadout fragment copy <local-path-or-raw-https-url> [destination.md]
loadout fragment link path/to/fragment.md --to <loadout>
loadout fragment unlink path/to/fragment.md --from <loadout>

# Switch to a different loadout
loadout use <name>

# Regenerate outputs after editing fragment content
loadout sync

# Preview output without writing files
loadout use <name> --std-out

# Delete a loadout
loadout remove <name>

# View or set the repository default loadout (used by auto-sync hooks)
loadout config
loadout config --default-loadout <name>
loadout config --unset-default-loadout
loadout config --fragments-home <repository-relative-directory>
loadout config --trust-fragment-host <exact-hostname>
loadout config --untrust-fragment-host <exact-hostname>

# Choose the agent files the repository generates
loadout config --output-files CLAUDE.md,.github/copilot-instructions.md
loadout config --unset-output-files

# Choose the agent files you get in every repository
loadout config --user
loadout config --user --output-files CLAUDE.md
loadout config --user --unset-output-files
```

---

## How It Works

Loadout manages three things:

| Concept       | What it is                                                                 |
|---------------|---------------------------------------------------------------------------|
| **Fragment**  | A markdown file containing focused guidance on one concern.                |
| **Loadout**   | A named profile that references a set of fragments.                        |
| **Output**    | A generated file your agent reads, such as `AGENTS.md` or `CLAUDE.md`.     |

Fragments live wherever makes sense:
- Simple projects or project-specialized fragments may keep them in-repo under `fragments/`.
- For teams or multi-repository projects, it's recommended to keep fragments in a shared, version-controlled fragment library. This enables sharing of common conventions across projects.

### Shared vs. Local Mode

```bash
loadout init              # shared mode (default) - loadout definitions are committed
loadout init --mode local # local mode - nothing Loadout writes is committed
```

Shared mode lets a team standardize on the same set of agent profiles. Local mode is for personal workflows where each developer curates their own.

- `.loadout.json` stores tracked repository settings such as the default loadout for hook-driven sync.
- Repository settings also define the fragments home, trusted remote source hosts, the fragment copy size limit, and
  the output files.
- `loadout config` rejects invalid settings. If a hand edit leaves an invalid value in `.loadout.json`, `loadout use`,
  `loadout sync`, and `loadout init` warn and use a safe value instead, and the next `loadout config` change saves the
  repair. `loadout fragment copy` still refuses to run until the settings are repaired.
- `.loadout.local.json` stores local-only runtime state: the active loadout and a record of the output files it last
  wrote.

Running `loadout init` again with the other `--mode` switches the repository between modes.

### Output Files

Loadout writes the composed guidance to `CLAUDE.md`, `AGENTS.md`, and `GEMINI.md` unless you or the repository choose
other files. Only use Claude Code? Choose your own list once, and it follows you into every repository:

```bash
loadout config --user --output-files CLAUDE.md
loadout sync
```

A repository can set a default for contributors who have not chosen:

```bash
loadout config --output-files CLAUDE.md,.github/copilot-instructions.md

# Return to the built-in default
loadout config --unset-output-files
```

Your list wins over the repository's, which wins over the built-in default:

```text
user setting  >  repository setting  >  built-in default (CLAUDE.md, AGENTS.md, GEMINI.md)
```

Both settings answer the same question for different people, and the generated files are per-clone files that serve
only your agents, so which agents you run is your call. The repository setting is the default for everyone who has not
chosen. A list replaces the whole list below it; lists never merge. `loadout config` shows which list applies and why:

```text
Output files: CLAUDE.md (your user setting; overrides the repository setting CLAUDE.md, AGENTS.md)
```

- The repository list is stored in `.loadout.json`, so everyone who works in the repository gets it. Your list is stored
  in `${XDG_CONFIG_HOME:-$HOME/.config}/loadout/config.json` on macOS and Linux, and in
  `%APPDATA%\loadout\config.json` on Windows. A relative `XDG_CONFIG_HOME` is ignored. Only `loadout config --user`
  creates the file, and it works outside any repository.
- Entries are comma-separated, repository-relative Markdown paths. Missing directories, such as `.github/`, are created
  when Loadout writes. Paths inside `.git/`, `.githooks/`, `.loadouts/`, `.loadout-backups/`, or the fragments home are
  rejected, and so are two entries that differ only by case, because macOS and Windows treat them as one file.
- Loadout records the files it wrote and a digest of their content. When a file drops out of the list, the next sync
  deletes it if it is unchanged. If someone edited it, Loadout keeps it, stops managing and ignoring it, and says so.
- Until the next `loadout sync`, `loadout` and every other command warn that the output files changed since the last
  sync and name the files that sync will add or remove.
- `loadout config` refuses to move the fragments home over a configured output file. An entry that a hand edit makes
  invalid is ignored with a warning, and the next `loadout config` change removes it from `.loadout.json`.
- Fragments homes differ by repository, so `loadout config --user` cannot check them. When a repository resolves your
  list, an entry inside its fragments home is ignored with a warning. A list with no usable entries left falls back to
  the repository setting.
- If your settings file cannot be read, `loadout use`, `loadout sync`, and `loadout init` warn and fall back to the
  repository setting. `loadout config --user --output-files` replaces the file and says so. If no config directory can
  be found, `loadout config --user` asks you to set `XDG_CONFIG_HOME` or `HOME` (`APPDATA` on Windows), and other
  commands skip your settings.

### Ignore Rules

Loadout never edits `.gitignore`. Generated files and runtime state belong to one clone, so Loadout keeps their
ignore rules in that clone's `.git/info/exclude`, inside sections it manages:

```gitignore
# >>> loadout (worktree: main) - managed by Loadout, do not edit
/CLAUDE.md
/AGENTS.md
/GEMINI.md
# <<< loadout (worktree: main)

# >>> loadout (repository: shared mode) - managed by Loadout, do not edit
/.loadout.local.json
/.loadout-backups/
# <<< loadout (repository: shared mode)
```

- Patterns are anchored to the worktree root, so a hand-written `packages/api/CLAUDE.md` stays visible to git.
- All worktrees of a repository share the exclude file, so each worktree keeps its own section listing the output
  files it writes. Linked worktrees are keyed as `worktrees/<name>`, and a removed worktree's section is dropped on the
  next sync.
- In local mode, the repository section also lists `/.loadout.json`, `/.loadouts/`, and the fragments home.
- `loadout use`, `loadout sync`, and `loadout init` keep the sections current. Lines outside them are left alone.

Some tools, such as Prettier, read `.gitignore` but not `.git/info/exclude`. If one of them picks up the generated
files, add your own `.gitignore` entries.

### Backups

`loadout use`, `loadout sync`, and `loadout init` replace every output file. Before replacing a file Loadout did not
write, such as a hand-written `CLAUDE.md`, Loadout copies it to `.loadout-backups/<UTC timestamp>/<path>.backup` and
prints the commands that move it into a fragment and link it:

```text
Warning: Backed up CLAUDE.md to .loadout-backups/20261020T174205Z/CLAUDE.md.backup before replacing it.
To keep its guidance, move it into a fragment and link it:
  mkdir -p fragments
  mv .loadout-backups/20261020T174205Z/CLAUDE.md.backup fragments/claude.md
  loadout fragment link fragments/claude.md --to default
  loadout sync
```

When several files are backed up, Loadout reports each one and then prints one set of commands for all of them.

Loadout also backs up its own output when someone edited it, and then regenerates the file:

```text
Warning: Backed up your edits to CLAUDE.md to .loadout-backups/20261021T091502Z/CLAUDE.md.backup and regenerated it.
Put lasting guidance in a fragment.
```

- Loadout recognizes its own output by the `<!-- Generated by Loadout CLI -->` line it writes first, and recognizes
  edits by comparing the file with a digest of the bytes it last wrote there.
- Files backed up by one command share a folder. If that folder already exists, it gets a numeric suffix (`-2`).
- If any backup fails, Loadout writes no output files and exits with an error.
- Loadout never writes through a symbolic link. It backs up the content the link points to, replaces the link with a
  regular file, and leaves the link's target unchanged. It refuses to write an output file or a backup inside a
  symbolically linked directory, including a linked `.loadout-backups/`.
- Backup notices go to stderr, so they still show when the auto-sync hooks discard stdout.
- `.loadout-backups/` is listed in `.git/info/exclude`, so backups are never committed. Loadout never deletes them.

### Copying External Fragments Safely

`loadout fragment copy` accepts local `.md` files and direct HTTPS Markdown documents. Remote copying uses an exact
repository-owned hostname allowlist, disables redirects, rejects HTML and encoded responses, and limits documents to
200,000 bytes. Projects may configure a lower limit with `loadout config --maximum-fragment-bytes <bytes>`.

GitHub `blob/...` links are HTML pages and are rejected. Use the corresponding `raw.githubusercontent.com` URL.
Copied content is never linked or composed automatically: review it before adding it to a loadout, because fragment
text becomes instructions for the agents that consume the generated files.

### Auto-Sync with Git Hooks

In shared mode, `loadout init` writes `.githooks/` hooks for you to commit alongside your loadout definitions. On every branch checkout and merge, those hooks run `loadout sync --auto` to regenerate the output files from whichever loadout is set as the repository default.

The reason this matters: generated output files are never committed, because Loadout ignores them through `.git/info/exclude`. A fresh clone or a new worktree won't have them. Without the hooks, every developer (and every agent session) would need to run `loadout sync` manually before getting the right context.

This is especially relevant when using agentic tools like Codex or Antigravity that spin up isolated git worktrees. Each worktree gets the correct guidance files on first checkout — no manual setup, no missing context.

Set the default loadout the hooks will use with:

```bash
loadout config --default-loadout <name>
```

If no default is configured, `--auto` is a no-op and the hooks exit cleanly.

### Worktrees and Cloud Sessions

Agent tools change quickly, and their worktrees or cloud sessions can start without Loadout's generated files. As a workaround, commit `AGENTS.md`, which most agents read:

```bash
git add -f AGENTS.md
```

Ignore rules don't apply to tracked files. Loadout keeps regenerating `AGENTS.md`, so commit it again when your guidance changes.

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

- `src/commonMain/kotlin/cli/` - Command definitions and output rendering
- `src/commonMain/kotlin/domain/` - Entities, services, and use cases
- `src/commonMain/kotlin/data/` - Repository implementations and serialization
- `src/nativeMain/kotlin/` - Native entrypoints and platform wiring

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
