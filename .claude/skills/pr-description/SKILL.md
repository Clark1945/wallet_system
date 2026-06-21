---
name: pr-description
description: Generate a professional English Pull Request description in standard format. Trigger whenever the user says "我要發 PR", "幫我寫 PR", "幫我準備 PR description", "PR 描述", "open a PR", "create a PR", or any variation of wanting to write or prepare a pull request. Even if the user only describes their code changes without explicitly mentioning PR, if the context implies they're about to submit a pull request, use this skill.
---

# PR Description Generator

Generate a clean, professional Pull Request description in English based on the user's code changes or description.

## Workflow

1. **Gather context** — If the user hasn't provided enough info, ask for:
   - What changed? (feature, bugfix, refactor, hotfix)
   - Why was this change needed? (issue link, motivation)
   - Any risks or breaking changes?
   - Is there an issue/ticket number to close?

2. **Classify the PR type** and pick the right template below.

3. **Output the PR description** — always in English, using markdown.

---

## Templates

### Standard (default)

```markdown
## 📋 Summary
<!-- 1–2 sentences: what this PR does -->

## 🎯 Motivation
<!-- Why is this change needed? Link the issue if applicable -->
Closes #

## 🔧 Changes
- 
- 
- 

## 🧪 How to Test
1. 
2. 
3. Expected result:

## ⚠️ Notes / Risks
<!-- Breaking changes? Migration needed? Impact on other services? -->
None

## ✅ Checklist
- [ ] Tests written or updated
- [ ] Documentation updated (if applicable)
- [ ] Tested locally
- [ ] No hardcoded secrets or credentials
```

---

### Hotfix (use when user says hotfix / urgent fix / production bug)

```markdown
## 🚨 Hotfix Summary
<!-- What broke and what this fixes -->

## 🐛 Root Cause
<!-- What caused the issue -->

## 🔧 Fix
<!-- What was changed to resolve it -->

## 🧪 Verification
1. 
2. Expected result:

## ⚠️ Risk Assessment
- Scope of impact:
- Rollback plan:

## ✅ Checklist
- [ ] Tested in staging
- [ ] No unintended side effects
- [ ] Monitoring/alerts checked
```

---

### OSS Contribution (use when user mentions open source / GitHub / OWASP / upstream project)

```markdown
## 📋 Summary
<!-- Concise description of the change -->

## 🎯 Motivation
<!-- What problem does this solve? Why is it valuable? -->
Closes #

## 🔧 Changes
- 
- 

## 🧪 How to Test
1. 
2. Expected result:

## 📸 Screenshots (if applicable)
<!-- UI or output changes -->

## ✅ Checklist
- [ ] Follows project's CONTRIBUTING.md
- [ ] Tests added/updated
- [ ] DCO sign-off included (`git commit -s`)
- [ ] No breaking changes (or clearly documented)
```

---

## Output Rules

- Always write in **English only**
- Use the emoji headers as shown — they aid scannability
- Keep Summary to **1–2 sentences max**
- If the user provided an issue number, fill in `Closes #<number>`
- If something is unknown, leave the placeholder comment rather than guessing
- After outputting the template, ask: *"Anything you'd like to adjust?"*
