# Mandatory Development Workflow Rule

This rule defines the mandatory development, testing, and documentation standards for Quiblo TV.

## 1. Branching Strategy (GitFlow)
- **Separate Branches**: Every feature, bugfix, or hotfix MUST be developed on its own dedicated Git branch before merging into `main`:
  - Features: `feat/<feature-name>`
  - Bugfixes: `bugfix/<bug-name>`
  - Hotfixes: `hotfix/<fix-name>`
- **Merge Process**: Always commit changes on the working branch, test the module, switch to `main`, and merge the branch.

## 2. Mandatory Unit Testing
- **No Untested Features**: Unit testing is NOT optional. Every single new data structure, enum, helper method, parser, or configuration logic MUST be covered by unit tests.
- **Verification**: Run unit test suites using `.\gradlew :core:model:test :source:xtream:test :source:m3u:test` and module tests before claiming completion.

## 3. Documentation Maintenance
- **Keep Docs Updated**: Whenever a new feature, UI enhancement, or architecture change is made, immediately update:
  - `README.md` (Features overview, status, architecture)
  - `docs/PLAN.md` (Execution plan & feature list)
  - `walkthrough.md` (Summary of accomplishments & changes)
