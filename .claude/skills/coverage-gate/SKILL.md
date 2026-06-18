---
name: coverage-gate
description: Verify that test coverage on NEW code is >= 80% (mirrors the SonarCloud "Coverage on New Code" quality gate). ALWAYS run this before any `git commit` or `git push` that touches wallet_system/src/main — including when the user simply says "commit" — and when a SonarCloud quality gate has failed on coverage.
---

# Coverage Gate (new code ≥ 80%)

Coverage on new code below **80%** fails the quality gate. This is enforced in **two** places:
- **Locally / by the agent** — run this skill before every commit or push (see steps below).
- **In CI** — `.github/workflows/ci.yml` runs the same script on every pull request and
  fails the build if new-code coverage is under 80%.

Running it locally first means the CI gate and the SonarCloud gate never fail after the fact.

## When to run
- **Before every `git commit` or `git push`** touching `wallet_system/src/main` — this includes
  the case where the user just asks you to "commit": confirm coverage **first**, then commit.
- After a SonarCloud or CI quality gate reports a coverage failure (to find the uncovered lines).

## Steps

1. Generate a fresh JaCoCo report (also runs the full test suite — both must pass):
   ```bash
   cd wallet_system && ./mvnw test jacoco:report
   ```
   If any test fails, fix it first — do not proceed.

2. From the **repo root**, compute coverage on new code vs the base branch:
   ```bash
   python3 scripts/check_new_code_coverage.py --base master
   ```
   (The same tracked script CI runs — see `.github/workflows/ci.yml`.)
   It prints per-file uncovered/partial lines and an overall percentage,
   and **exits non-zero if below 80%**.

3. Interpret the result:
   - **PASS (≥ 80%)** → proceed with the commit/push.
   - **FAIL (< 80%)** → the script lists the exact uncovered/partial new lines.
     Add targeted unit/integration tests for those lines, then repeat from step 1.
     Do **not** commit/push until it reports PASS.

## Notes
- The metric counts both line and condition (branch) coverage on added lines, the same
  way SonarCloud does — so partially-covered branches (e.g. only the `if` side tested)
  also count against you.
- If a new line is genuinely untestable infra (e.g. a `@Profile("!test")` bean or a pure
  record), prefer adding it to `sonar.coverage.exclusions` in `wallet_system/pom.xml`
  rather than writing a hollow test.
- Adjust the base branch with `--base <branch>` when the PR targets something other than `master`.