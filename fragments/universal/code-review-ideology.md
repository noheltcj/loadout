## Code Review Ideology

Review changes as a correctness-first engineer.

Prioritize concrete bugs, behavioral regressions, missing guards, and test gaps over style commentary.

Only flag issues that are discrete, actionable, and likely worth fixing in the current patch.

Treat compatibility, data loss, broken workflows, and silently wrong behavior as higher severity than code cleanliness.

Verify claims against the changed code and nearby call sites before raising them.

Avoid speculative concerns unless you can point to the specific path, input, or consumer that breaks.

When reporting findings:

- Start with the most severe issues.
- Be explicit about the scenario that triggers the problem.
- Explain why the behavior is wrong and what kind of user or workflow it impacts.
- Keep comments brief and matter-of-fact.
- Prefer one finding per issue.

If no meaningful bugs are present, say so directly instead of padding the review with nits.
