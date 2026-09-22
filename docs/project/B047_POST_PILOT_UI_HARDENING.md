# B047 Post-Pilot UI / UX Hardening

Status: implemented on `codex/b047-post-pilot-ui-hardening`.

This milestone hardens the post-pilot Android experience without expanding ServiceLoop into a server-backed or enterprise system.

Implemented scope:

- resilient dashboard observation after FULL_WORKSPACE replacement, with dataset-generation reset and observer restart;
- responsive action rows, one canonical accessible ServiceLoop checkbox, neutral service stripes, agenda icons/alternating rows, and one-row reminder presets;
- clearer About/report-bug metadata, technician editing, team/member spacing, dispatch reference gutters, role-aware Working Visit explanation, collapsed existing-Visit Add task editor, and self-navigation suppression in the active Service list;
- additive Room customer contacts with create/edit/remove, safe dialer/SMS/email/WhatsApp handoffs, recovery/export/erase/FULL_WORKSPACE coverage, and no `.slsync` v1 extension;
- Room v17 migration for customer contacts, technician notes, and frozen final-report technician designation;
- completion-review photo include/exclude controls before finalization, parts/photo copy corrections, semantic OTHER contact icon, private/public metadata cleanup, and bounded PDF preview zoom/pan with page-change reset;
- explicit assigned-work import role gate and directory/report designation rendering.

The primary customer contact fields remain unchanged. Additional contacts are local and recovery-portable for B047; cross-platform transport remains a future contract decision.

Verification is recorded in the handoff: unit tests, debug compilation/build, Android test APK assembly, lint, migration source updates, and device evidence are reported separately rather than inferred.
