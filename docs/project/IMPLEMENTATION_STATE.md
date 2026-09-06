# ServiceLoop — Implementation State

**Updated:** 2026-09-06

## Current state

- Dedicated Android Studio project created as a Compose Empty Activity.
- Repository: `daemon-fr/serviceloop`, default branch `master`.
- Initial Android project commit observed: `a046fbe2c513dd91464dfe64cb49f5a12e34cd2c` (`Initial ServiceLoop Android project`).
- Package/application ID: `com.v16studio.serviceloop`.
- Generated project currently has only the stock starter UI; no ServiceLoop product workflow is implemented yet.
- Adopted product/UI baseline is recorded in `SOURCE_OF_TRUTH.md` and `BASELINE_DECISIONS.md`.

## Known generated toolchain baseline

- minSdk 29
- compileSdk 37
- targetSdk 37
- AGP 9.3.2
- Gradle 9.5.0
- Gradle JVM/project JDK 20
- Java source/target compatibility 11
- Kotlin 2.2.10
- Compose BOM 2026.02.01

Android Studio generated newer ordinary AndroidX libraries than Routine Repeater. Preserve the ServiceLoop-generated versions unless a concrete compatibility reason requires change. Add Room/DataStore/KSP only when the persistence foundation needs them.

## Next authorized development target

Stage 1 — Runnable foundation and representative native visual proof, including the modest integrity foundations required for later durable drafts/history/attachments and representative Home, Equipment detail, Inspection entry, and Completion review surfaces.

Fixture-driven representative screens in Stage 1 are development proof only, not a completed service workflow and not production first-launch sample data.
