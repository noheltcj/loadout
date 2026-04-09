## Pre-Launch Product Stage

This product has not launched yet. There are no production users, no migration compatibility constraints, and no uptime obligations to preserve.

### What This Means

- Be an idealist. Prefer the cleanest long-term design over temporary compatibility workarounds.
- Reshape canonical domain models when they are awkward, misleading, or unnecessarily coupled.
- Rewrite repository contracts, serialization shapes, and filesystem boundaries when a clearer architecture is available.
- Do not preserve obsolete structure just because it already exists in the repo.

### How to Apply

- Make small structural improvements inline when the target shape is obvious.
- For larger refactors that change core command behavior or touch many files, leave a concrete handoff plan in `plans/`.
- Optimize for architecture that scales as the CLI surface and fragment library grow.
