# ServiceLoop — SL-4 Requirement Coverage

**Status:** Development checkpoint on `codex/sl-4-history-recovery`. Independent testing-AI-model verification and owner review remain required; this is not an acceptance claim.

## Implemented

| Area | Coverage |
|---|---|
| Room v5 | Additive v4→5 migration, retained migration chain, exported schema, immutable correction/revision links, lifecycle/move change events, durable correction drafts, and recovery metadata. No destructive migration fallback. |
| History | Global and Customer/Site/Equipment scoped timelines covering final service records, cancellations, contacts, follow-ups/events, corrections, voids, lifecycle, schedule, and move provenance. Type/date/search filters and event-date/recorded-time sorts retain both dates and captured context. |
| Corrections/voids | Durable resumable draft, fixed subject links, captured identity/date/work/outcome/next-date edits, append-only revision commit, schedule-impact acknowledgement and downstream claim blocker, stable retry token, retained prior reports, explicit public/private void reasons, and retained inspectable versions. |
| Reports | Multiple renditions per revision. A missing READY file is recreated as a new `RECREATED` version with its true PDF version in the footer; old metadata remains. Missing/failed reports feed Attention. |
| Lifecycle/move | Reasoned Customer/Site archive/restore, Equipment retire/return, Plan pause/resume/end, dependency review with routable blockers, and reasoned Equipment move. FC-03 blocks a move while a correction is open and preserves old/new historical context while carrying plans/dates. |
| Recovery | SAF `.slbackup` create/open flows; versioned logical Room snapshot plus durable owned files; per-file path/type/size/hash manifest; bounded archive parsing; complete versus acknowledged incomplete copy; PBKDF2-HMAC-SHA256 (310,000 iterations, fresh salt) and AES-256-GCM (fresh nonce, authenticated header/payload); wrong-passphrase/tamper validation before mutation; inspect-first replacement with `REPLACE`; post-write readback verification before recording a verified full backup. |
| CSV | Fixed UTF-8 quoted Directory CSV, optional plaintext private fields, spreadsheet-formula escaping, and readable Records ZIP with directory, record/revision, history, report-index, attachment-index, and README datasets. Fixed create-only import validates exact headers/limits/references/parents, blocks conflicts, requires possible-duplicate decisions, commits all-or-none, and creates no plans. Blank and fictional-example files are available through SAF. |
| Erase | Data-and-recovery-only flow shows counts, offers backup first, requires acknowledgement plus `ERASE`, removes authoritative tables and owned attachment/report trees, creates a new dataset identity, and does not claim external-copy/account deletion. |
| Navigation | Home Attention, Work → More → History, global/scoped History, record correction/version/void actions, entity lifecycle/move actions, and Settings → Data and recovery are backed by real routes rather than Stage-4 placeholders. |

## Development evidence

- Host integrity tests cover immutable correction append, durable correction draft, void retention, recurrence non-replay, authenticated backup round-trip, wrong-passphrase and tamper rejection, erase/restore, CSV formula safety, create-only idempotency, and missing-report rendition recreation.
- Migration builders now exercise the retained migration chain through v5; dedicated device execution remains part of the final verification gate.
- Debug unit/build/lint and canonical non-destructive smoke results are recorded in the SL-4 handoff, not inferred here.

## Known verification boundary

This development milestone does not claim independent exhaustive runtime, interruption-injection, large-data, accessibility, or owner acceptance. Testing AI verification must especially challenge schedule-changing corrections, incomplete backups, provider failures, restore interruption boundaries, CSV duplicate branches, lifecycle dependency races, report-version handoffs, and preserved v4 data.

Stage 5 remains reminders and whole-product hardening. B-008 remains a later Romanian-localized real-technician pilot against a pilot-ready build; it is not satisfied or waived by SL-4.
