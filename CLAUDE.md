# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The README is the main documentation. It describes the integration, the API, the modules, the architecture, the development commands, the workflows and the release:

@README.adoc

## Rules for Changes

- Keep the README current. When you change the public API, the architecture, a command or a workflow, update the README in the same commit.
- A change of a Room entity needs a new database version, a migration in `DatabaseMigrator.kt` and the exported schema in `<module>/schemas/`.
- Keep Kotlin and KSP, and the three Room versions in the root `build.gradle`, together.
- `android-app` and `android-camera-service` use this repository as a submodule. Keep the Cyface library versions the same in all three.
- Follow the rules of the workflows in the README section "GitHub Workflows" for each change of `.github/workflows/`.

## Commit Style

`[TICKET-ID] Short imperative summary`. Ticket prefixes: `LEIP-###` (current), `CY-####`, `STAD-###`.
