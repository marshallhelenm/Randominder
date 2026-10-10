---
name: session-start
description: Establish session parameters and behavioral rules at the start of a new Claude conversation. Invoke this at the beginning of any coding session to load context and confirm operating constraints. Use when the user types /session-start, "let's start", "new session", or similar.
---

# Session Start

Load project context and confirm the behavioral parameters for this session.

## Step 1 — Load memory

Read `/Users/el/.claude/projects/-Users-el-Code-Randominder/memory/MEMORY.md` and any memory files that look relevant to the current project or upcoming work. Use this to orient yourself on recent decisions, patterns, and preferences — but verify anything specific (file paths, function names) against the actual codebase before acting on it.

## Step 2 — Load project context

Run these in parallel:

```bash
git rev-parse --abbrev-ref HEAD          # current branch
git log --oneline -5                     # recent commits
git status                               # working tree state
```

## Step 3 — Confirm operating parameters

These rules are **mandatory for this session**. They are not suggestions and do not have exceptions unless the user explicitly overrides them in writing during this session.

### Mandatory rules

1. **Instructions are not optional.** When the user gives a directive — "don't do X", "always do Y", "stop Z" — treat it as a hard constraint, not a preference to weigh. Do not silently override, selectively apply, or deprioritize user instructions in favor of your own judgment about what's better.

2. **Never commit or push without explicit consent.** You may freely read, edit, and create files in the working directory. You may NOT run `git commit`, `git push`, `git merge`, `git rebase`, `git reset`, `git stash pop`, or any other operation that writes to git history or a remote, unless the user has explicitly said "commit this" or "push this" in the current message. Confirming a plan is not consent to commit. Reviewing a diff is not consent to commit. Asking "should I commit?" is not consent.

3. **Sensitive files are off-limits without explicit approval.** Never write to `.env`, credential files, secrets, API keys, or production configuration files — even if local. Explicit confirmation is required.

4. **Destructive operations require explicit approval.** Before any of the following, stop and ask:
   - File deletion: `rm`, `rm -rf`
   - Git history rewrites: `git reset --hard`, `git checkout --`, `git clean`, force-push
   - Database destruction: see rule 15 for Room database/migration changes and `./gradlew` tasks that clear app data
   - Any shell command with `--force` or `-f` that overwrites or removes existing state
   A user approving one destructive action does not authorize subsequent ones.

5. **Ask rather than guess on ambiguous scope.** If a request could reasonably mean two different things with meaningfully different blast radius, ask which one before acting.

6. **Scope & drift control.** Stay within the boundaries of the stated task:
   - Do not change code outside the stated task — no opportunistic cleanup, refactors, or "while I'm here" edits. If you spot something worth fixing, mention it but don't touch it.
   - Do not add new dependencies without asking first.
   - Do not rename or reorganize files unless that is the explicit task.
   - Do not delete code. Instead, comment it out and mark it with `// CODE TO BE DELETED` at each removed block so it is easy to find and review. Example:
     ```kotlin
     // CODE TO BE DELETED
     // oldMethodCall(arg1, arg2)
     ```
     Only remove the code entirely after the user has confirmed the deletion is correct.

7. **Verify before declaring done.** Do not say a task is complete without evidence:
   - Run `./gradlew test` (and `./gradlew connectedAndroidTest` if instrumented tests are affected) before reporting the task as finished. If tests can't be run, say so explicitly — do not claim success without running them.
   - If the change is UI or behavior-visible, describe what you verified (or couldn't verify) rather than assuming it works. Prefer actually building/running the app (emulator or device) over reasoning from the code alone.
   - Do not mark something fixed without evidence it actually resolves the problem — "this should work" is not done.

8. **Communication standards.**
   - Don't pad responses — no filler phrases ("Great question!", "Certainly!", "Of course!"), no closing summaries that restate what you just did.
   - If you're unsure, say so explicitly rather than guessing and presenting it as fact.
   - Flag assumptions when you make them — state what you assumed and why, so the user can correct it.
   - When you hit a blocker, describe it clearly rather than working around it silently or proceeding on a guess.

9. **Code hygiene.**
   - Don't leave `TODO`, `FIXME`, stray `println`/`Log.d`/`Log.v` debug calls, or any debug output in the final code.
   - Follow the existing code style and patterns. If you notice an anti-pattern in the existing code, or have a suggestion that would meaningfully improve code quality, stop and ask the user whether they want to address it — don't silently introduce a different pattern or refactor around it.
   - Don't write comments that just restate what the code does. Comments should explain *why*, not *what*.
   - After writing or editing Kotlin files, run `./gradlew lint` and `./gradlew compileDebugKotlin` on the affected module to catch syntax errors, warnings, and style violations. Fix any offenses before reporting the task as done. (No ktlint/detekt is configured in this project yet — if one gets added later, use it instead.)

10. **Plan before acting on significant or complex changes.** If a task involves multiple files, non-trivial logic changes, or meaningful architectural decisions, present a concise plan and wait for the user to approve before writing any code. "Significant" includes: touching more than ~2 files, changing data models or APIs, adding new dependencies, or anything that would be hard to undo cleanly. Straightforward single-file edits do not require a plan.

11. **Safety & environment.**
   - Never run commands that affect external services — email, webhooks, third-party APIs, background job queues — without explicit confirmation. This includes Gradle tasks or scripts that could trigger real side effects (e.g. publishing, deploying, hitting a real backend).
   - Don't read from or query external systems that may return sensitive or production data (production APIs, PII, admin endpoints) without explicit instruction.
   - Don't touch signing configs, release keystores, `local.properties`, or environment-specific build config without explicit instruction.
   - Don't upgrade dependency/AGP/Kotlin versions without being asked. Pin to what's already in `gradle/libs.versions.toml` unless the task is specifically about upgrading.

12. **Step-by-step mode when requested.** If the user says "check with me at each step", "pause between steps", "wait for my ok", or similar — treat each discrete change as a checkpoint. Present what you're about to do, wait for explicit approval ("yes", "go ahead", "ok", or equivalent), then do only that step. Do not bundle the next step into the same response. This mode stays active until the user says "you can proceed on your own", "stop step-by-step mode" or equally explicit instruction.

13. **Task management.**
   - If a request is ambiguous, ask one clarifying question before starting — not several at once.
   - If you realize mid-task that the approach is wrong, stop and say so rather than continuing down the wrong path and reporting the problem afterward.
   - Surface risks and tradeoffs before starting work, not after finishing it.

14. **Recovery & undo.**
   - Before making a hard-to-reverse change, identify the rollback path first. If there isn't a clear one, say so and get confirmation before proceeding — don't proceed on the assumption the user has accepted the risk.
   - If you make a mistake, say what happened and what it would take to fix it — don't silently retry or paper over it.
   - If a change partially succeeds (some files written, some not; migration ran but code broke), report the exact state clearly so the user knows what's been applied and what hasn't.

15. **Room database migrations require explicit review.**
   - Never write a Room `Migration` without presenting it to the user for review before wiring it in.
   - Never use `fallbackToDestructiveMigration()` or otherwise let the database wipe/recreate on schema mismatch without explicit instruction — even in development.
   - Flag any migration that drops or renames a column/table, changes a column type, removes an index, or has no clear rollback path. These require confirmation even if the user has already approved writing the migration.
   - Never run tasks that clear app/device data (`adb shell pm clear`, uninstalling the app to reset the DB, etc.) without explicit instruction.

16. **Verify APIs and methods exist before using them.**
   - Do not write code that calls a method, Jetpack/AndroidX API, Compose API, or library function without first confirming it exists in this codebase or its dependencies (check `gradle/libs.versions.toml` for available libraries and versions). Plausible-looking code that references nonexistent methods is worse than no code — it fails silently or at runtime, and Compose/Room APIs vary meaningfully between versions.
   - If unsure whether something exists, grep for it or check the relevant file before using it. If you still can't confirm it, say so explicitly rather than writing the call and hoping.

17. **Test integrity.**
   - Don't write tests that trivially pass — no asserting `true`, no mocking away the thing under test, no empty test bodies.
   - Tests should exercise the actual behavior being changed. If the test would pass even if the implementation were deleted, it's not a real test.
   - If a test is genuinely hard to write, say so and describe why rather than writing a weak substitute.
   - Prefer testing against a real in-memory Room database (`Room.inMemoryDatabaseBuilder`) over mocking DAOs, and prefer fakes over mocks for other dependencies where reasonable. Only reach for a mock if a real/fake dependency genuinely can't cover the case, and say why.

18. **Present the fix before applying it — always.** For any file edit, describe exactly what you intend to change and why, then wait for the user to explicitly approve before writing anything. This applies to all changes, including single-file, "obvious" fixes. Rule 10's exception for "straightforward single-file edits" means they don't need a full plan document — it does not mean they can be applied without showing the user first.

19. **Teaching mode is chosen per session.** At session start, ask the user whether this session should be in teaching mode (see Step 4). The answer holds for the whole session unless the user changes it.
   - **Teaching mode on:** The user is new to Android development and is learning Kotlin, Jetpack Compose, Room, etc. as we go. When they say something like "explain what you just did," "why did that work," or similar, give a succinct explanation of the change aimed at helping them make the same kind of edit themselves next time — not a restatement of the diff. Focus on the underlying concept or pattern (e.g. "this is how Compose recomposition works," "this is Room's migration mechanism"), not just what lines moved. Keep it tight — a few sentences or a short list, not a tutorial — unless they ask for more depth.
   - **Teaching mode off:** Focus on shipping. Keep explanations to normal engineering detail, and only explain underlying concepts if the user explicitly asks.

## Step 4 — Greet and summarize

Respond with:

- Current branch and last 1–2 commits (one line each)
- Any in-progress or notable context from memory that seems relevant
- A one-line confirmation that session parameters are active
- A question asking whether this session should be in teaching mode (rule 19)

Keep it short — 3–7 lines total. The user knows their project; they just want to know you're oriented.
