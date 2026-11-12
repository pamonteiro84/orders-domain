---
name: update-readme
description: "Keeps README.md in line with the code, short and easy to read, with only the project's most important design decisions. Use after a change to something the README describes (current state, stack, setup, commands, API, errors, tests), after an architectural decision is settled, when the user asks to update the README, or requests /update-readme."
---

# Update README

The README is the project's front page. A reader new to the project should learn what the system
does, how to run it and why it is built this way in a few minutes.

## When

- A change affects something the README describes: current state, stack, roadmap status, setup,
  `make` targets, ports, endpoints, status codes or test setup.
- An architectural decision is settled, such as how events are published or how duplicates are
  handled. Library details, naming and code style do not belong in the README.
- Describe only what exists in the code. Planned work goes in the roadmap, never written as if it
  already exists.

## How

1. Read `README.md` in full, and the diff or code of the change.
2. Find every line the change makes stale, not only the obvious one. New infrastructure, for
   example, affects the current state, the stack, `make run`, the `make` table and the tests.
3. Edit only those lines, keeping the existing sections and their order:
   current state → stack → roadmap → running locally → API → errors → design decisions → tests and CI.
4. Check every claim against the code: ports in `.env.example`, targets in `Makefile`, status codes
   in the exception handlers, containers in the Testcontainers configuration.

## Style

- English, short sentences, one idea per sentence. Explain a term the first time it appears if an
  engineer from another stack might not know it.
- No internal task ids or history of how the code got here. Write for someone seeing the project for
  the first time.
- Concrete over abstract: real endpoints, ports, status codes and example values.
- **Design decisions**: only those that shape the architecture, about twelve at most. One bullet
  each: a bold title, what was done and why, in two to four lines. When a decision changes, rewrite
  its bullet instead of adding another. When the list grows past twelve, merge or drop the weakest.
- Keep the file about the same length: replace stale text rather than adding next to it.

## Limits

- Edit only `README.md`.
- Never run `git add`, `git commit` or `git push`. The user reviews and commits.

## Reply

Reply in the user's language, compactly: what changed in each section, one line each, anything left
out and why, and how to review it (`git diff README.md`).
