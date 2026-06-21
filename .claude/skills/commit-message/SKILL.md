---
name: commit-message
description: Generate a professional English commit message following Conventional Commits specification. Trigger whenever the user says "我要 commit", "幫我寫 commit message", "幫我產生 commit", "commit 訊息", "write a commit", or any variation of wanting to write or prepare a git commit message. Even if the user only describes their code changes without explicitly mentioning commit, if the context implies they're about to commit, use this skill.
---

# Commit Message Generator

Generate a clean, professional commit message in English following the **Conventional Commits** specification.

## Conventional Commits Format

```
<type>(<scope>): <short summary>

[optional body]

[optional footer]
```

---

## Type Reference

| Type | When to use |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `test` | Adding or updating tests |
| `docs` | Documentation only changes |
| `chore` | Build process, dependencies, tooling |
| `ci` | CI/CD configuration changes |
| `style` | Formatting, missing semicolons (no logic change) |
| `revert` | Reverts a previous commit |

---

## Workflow

1. **Gather context** — If the user hasn't provided enough info, ask:
   - What changed? (describe the code change)
   - Is this a fix, feature, refactor, or something else?
   - Which module/scope was affected? (e.g., `auth`, `user`, `payment`)
   - Is there a breaking change?
   - Is there a related issue/ticket number?

2. **Pick the type** from the table above.

3. **Output the commit message** using the rules below.

---

## Output Rules

### Summary line (required)
- Format: `<type>(<scope>): <summary>`
- **Lowercase** type and scope
- Summary in **imperative mood** — "add", "fix", "update" (not "added", "fixed")
- Max **72 characters** for the whole first line
- No period at the end

### Body (optional, add when change needs explanation)
- Blank line between summary and body
- Explain **what** and **why**, not **how**
- Wrap at 72 characters

### Footer (optional)
- Breaking change: `BREAKING CHANGE: <description>`
- Issue reference: `Closes #123` or `Refs #456`
- DCO sign-off (for OSS): `Signed-off-by: Name <email>`

---

## Examples

### Simple fix
```
fix(auth): handle null pointer when token expires
```

### Feature with scope
```
feat(user): add email verification on registration
```

### Refactor with body
```
refactor(payment): extract charge logic into PaymentService

Previously the charge logic was scattered across three controllers.
Centralising it makes testing and future changes easier.
```

### Breaking change
```
feat(api): change response envelope to follow JSON:API spec

BREAKING CHANGE: all endpoints now wrap data in a `data` key.
Clients must update their response parsing logic.

Closes #88
```

### Chore
```
chore(deps): upgrade Spring Boot from 3.1 to 3.3
```

### OSS contribution with DCO
```
fix(seed): prevent duplicate inserts using WHERE NOT EXISTS

Closes #456
Signed-off-by: Clark <clark@example.com>
```

---

## Output Format

Always output:
1. The commit message in a **code block**
2. A brief **one-line explanation** of why you chose that type and scope
3. Ask: *"Anything you'd like to adjust?"*
