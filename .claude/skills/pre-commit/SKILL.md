---
name: pre-commit
description: "Checks whether the changes are ready to commit by running CI and reviewing the diff. Use when the user asks if they can commit, if the work is ready to commit (for example 'posso fazer commit?', 'está pronto para commit?'), or requests /pre-commit."
---

# Pre-commit

Confirm whether the working tree is ready to commit. Follow the steps in order, and do not say
"ready" unless you ran them in this invocation.

## Steps

1. **Check the state.** Run `git status --short --untracked-files=all` and `git diff HEAD`, and also
   read the new files (`??`), which `git diff` does not show. Without `--untracked-files=all`, a new
   directory appears as a single line and hides the files inside it. If there are no changes, say
   there is nothing to commit and stop.

2. **Run CI.**
   - If the `Makefile` has a `ci` target, run `make ci`.
   - Otherwise, read `.github/workflows/` and run the same steps.
   - If neither exists, say there is no CI to run. Never assume it is green.

   `make ci` stops at the first stage that fails. Name the stages that did not run: a lint failure
   means the tests have not been run yet.

3. **Review the diff.** Look for:
   - debug leftovers, commented-out code and unresolved `TODO`s;
   - names, status codes, titles and messages copied from elsewhere without adapting them, such as a
     `422` in a `409` handler or a property with a typo in its name;
   - changes to a Flyway migration already committed: never edit it, add a new one instead;
   - secrets, and files that should be in `.gitignore`, such as `.env`.

4. **Reply.** In European Portuguese, even though this file is in English, and compactly. Start with
   **pronto** or **não pronto**.
   - If not ready: the real output of what failed and what is wrong in each file.
   - If ready: the `git add` and `git commit` commands for each commit, for the user to run.

## Limits

- Do not run `git add`, `git commit` or `git push`: only propose the commands.
- Do not fix the code: point out what is wrong. Fix it only if the user asks.
- Do not run `make format`, because it changes files. If lint fails on formatting, suggest the
  command.
