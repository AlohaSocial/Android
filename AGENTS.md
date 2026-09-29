# AGENTS.md

Guidance for coding agents working in this repository. Humans: see
[CONTRIBUTING.md](CONTRIBUTING.md); everything there applies to agents too.

## Before changing code

- Read the issue the change belongs to and its acceptance criteria, and the
  checklist of the pull request template.
- Run `./gradlew detekt ktlintCheck lint alohaArchitectureCheck testGenericDebugUnitTest verifyRoborazziGenericDebug`
  before and after; a change is finished when it is green.

## Rules

- Follow the module graph (`ModuleRules.kt`); add a row there in the same change
  when adding a module.
- Verify every new dependency and version against Maven Central or Google Maven
  before using it, and add it to `gradle/libs.versions.toml`, never inline.
- Never add a baseline entry to make a check pass.
- Server behaviour comes from the fixture corpus and the live dev instance, not
  from assumptions; name the server behaviour in a comment when code works around it.
- Commits use Conventional Commits, `git commit -s -S`, and an
  `Assisted-by: <tool>:<model>` trailer. No `Co-Authored-By` trailer, no session links.
- Never leave two consecutive blank lines in a file.
