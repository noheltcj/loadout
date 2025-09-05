# 🎒 Loadout CLI

> Composable, shareable `.md` system prompts for agentic AI coding systems.  
> Manage your `AGENTS.md`, `CLAUDE.md`, and related files with a clean, CLI-driven workflow.

---

## 📖 Overview

**Loadout CLI** helps you manage system prompts as modular Markdown files.  
Instead of maintaining large, monolithic prompts, you can split them into smaller reusable components and compose them into a final `.md` profile for your AI agent.

This approach makes prompts:
- **Composable** – reuse common fragments across agents.
- **Maintainable** – edit smaller, focused files instead of one long spec.
- **Shareable** – publish or version control loadouts with your team.

---

## ✨ Features

- Build final `.md` files from multiple sources.
- Compose prompts like “loadouts” for different tasks or agent personalities.
- Clean CLI interface for local development and automation.
- Open source and extensible for future integrations.

---

## 🚀 Installation

```bash
# With npm
npm install -g loadout-cli

# Or with pnpm
pnpm add -g loadout-cli
```

---

## ⚡ Quick Start

1. Create a few `.md` fragments:

```markdown
# style.md
- Concise explanations
- Uses clean markdown formatting
```

```markdown
# debugging.md
- Always request stack traces
- Suggest minimal reproduction steps
```

2. Build your agent profile:

```bash
loadout build style.md debugging.md --out CLAUDE.md
```

3. Result: a fully composed `CLAUDE.md` ready to use with your AI system.

---

## 🖥 CLI Usage

```bash
# Compose multiple markdown files into one output
loadout build <files...> --out <output>

# Example
loadout build core.md style.md debugging.md --out final.md
```

Options:
- `--out, -o` : Output file (default: `stdout`)
- `--watch, -w` : Rebuild automatically when input files change (coming soon)

---

## 🧑‍🤝‍🧑 Community

- Contribute improvements via pull requests.
- Share your loadout compositions and best practices.
- Open issues for feature requests or discussions.

---

## 📜 License

[MIT](LICENSE)

---

**Loadout CLI** – a cleaner way to manage and share system prompts.
