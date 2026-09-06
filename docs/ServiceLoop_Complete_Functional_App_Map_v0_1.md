# ServiceLoop — Complete Functional App Map

**Version:** 0.1  
**Prepared:** 5 September 2026  
**Status:** PROPOSED BASELINE — ready for product review, not an approved specification or build authorization.  
**Platform checks:** Official Android and Google Play documentation checked on 5 September 2026.  
**Basis:** `ServiceLoop_Functional_App_Map_Prompt_v1_1.md` and the complete `ServiceLoop_Research_and_Product_Context.md`, including Appendix A.

The context file supplies the research provenance and the user's reason for exploring this product. The prompt supplies the requested scope and deliverable. Neither the other model's score nor earlier price/complexity estimates is adopted here. Historical market claims have not been reverified. Everything below that selects a product behavior is a **design recommendation**, unless expressly identified as a verified platform fact in Section G.

## How to read this map

Sections **A–I** follow the requested structure. **S01–S38** identify destinations; an editor can be a sheet rather than a separate full-screen page. **U01–U08** identify shared interaction surfaces/contracts. **Axx** suffixes identify actions. **R01–R12** identify cross-screen rules, **E01–E14** automatic behaviors, and **P01–P19** official platform references.

An action row defines a user-visible control, including its meaningful outcome. Fields listed under **Inputs** edit the local form or working draft; they do not independently alter other business records. Availability follows the stated screen state and shared contracts. There are no unspecified overflow-menu items.

# A. Product boundary and design assumptions

## A1. The proposed complete first product

**An offline service book for one independent technician:** identify customer equipment, see its outstanding service obligations, arrange a visit, record actual work, produce a customer report, and establish the next obligation.

One business, one authoritative local dataset, one device actively used for that dataset. No ServiceLoop account, application backend, staff accounts, or simultaneous editing. Core recordkeeping, inspections, history, PDF creation, and in-app report viewing work without an internet connection. External apps, file providers, and their networks remain separate dependencies.

Three primary navigation destinations are sufficient: **Home, Work, Customers**. Templates, profile, reminders, recovery, and help live under **Settings**. Equipment has its own searchable register inside Customers, not another primary navigation destination. History is reachable globally from Work and contextually from customer/site/equipment records.

## A2. Chosen defaults

| Topic | Proposed rule and reason |
|---|---|
| Visit boundary | One site and one actual service date per finalized visit. Several machines and several services per machine are supported. Work performed on different days becomes separate visits; this keeps dates and reports unambiguous. |
| Recurrence | Actual completion date plus an integer interval in days, weeks, months, or years. A technician confirms each affected next date. There is one current obligation per active plan, not a growing stack of missed interval instances. |
| Appointments | Optional for doing work. A booked visit is a scheduled container, not proof of service. There is no calendar-grid subsystem. |
| One-off work | A named visit work item can exist without a plan and optionally use a template. No invented recurring plan is required. |
| Checklists | Simple status, short-text, and numeric items. Templates and working/finalized copies are different records. There is no automatic diagnosis or certification. |
| History | Finalized visits are read-only revisions. Corrections create identifiable revisions; a mistaken visit can be explicitly voided. Neither action silently replays later scheduling history. |
| Reminders | An approximate daily summary, plus optional approximate appointment heads-ups. No exact-alarm access. The due-work list remains authoritative. |
| Migration | A fixed flat CSV for **initial** customers/sites/equipment only, into an otherwise empty business dataset. No incremental merge, history import, or service-plan import. |
| Recovery | User-initiated, complete, verified backup packages; restore replaces the dataset after validation. Backup is not synchronization. |
| Backup confidentiality | CORE backup/export files are **not password-encrypted by ServiceLoop**. They contain private business data; destination protection is the technician's responsibility. This is a consequential review decision, not an implied security guarantee. |
| Forms | Ordinary editors use explicit Save. Working visits and correction drafts save progress continuously, with an honest last-durable-save indicator. |
| Reports | One fixed A4 portrait layout, one visit per report, with multiple equipment sections. Selected photos only. Internal notes never enter the report. |
| Commercial layer | Not designed into CORE. No trial limits, price, billing flow, or read-only paywall is presumed. |

**Additional integrity recommendations:** visible void/correction handling, one live reservation of a plan obligation, explicit ownership-transfer handling, and atomic finalization/import/restore are included because the requested workflow otherwise loses or misrepresents professional records. They are not prior user approvals.

This complete surface is substantially broader than a reminder utility: the difficult commitments are relationship changes, partial work, historical revisions, report preservation, and recovery. The small navigation structure should not be mistaken for a small testing burden. No development multiplier or delivery estimate is inferred from Routine Repeater.

**OPEN:** pilot trade, launch languages/geography, commercial model, acceptable photo fidelity, and acceptance of the single-device/backup boundary. Screen labels below are written in English for review; that does not choose the launch language.

# B. Complete navigation map

`[Sxx]` is a destination; text in parentheses is an action. Shared surfaces are expanded afterward.

```text
Launch
├─ [S01] Welcome / empty-dataset start
│  ├─ (Start with a customer) → [S06]
│  ├─ (Import existing records) → [S37]
│  ├─ (Restore a backup) → [S35]
│  └─ (Skip setup) → [S02]
└─ Main navigation
   ├─ [S02] Home
   │  ├─ Overdue / due soon → [S03: Due]
   │  ├─ Booked / unfinished visits → [S03: Visits]
   │  ├─ Follow-ups → [S03: Follow-ups]
   │  ├─ Report/correction attention → [S26: relevant filter]
   │  ├─ (New visit) → [S16]
   │  └─ (Settings) → [S30]
   ├─ [S03] Work
   │  ├─ Due row → [S12] Service plan
   │  ├─ Visit row → [S17] Visit summary
   │  │  ├─ (Edit booking) → [S16]
   │  │  ├─ (Start / resume) → [S18] Working visit
   │  │  │  ├─ Work item → [S19] Service work / checklist
   │  │  │  ├─ (Change selected work) → [U06]
   │  │  │  ├─ (Create follow-up) → [S22, staged]
   │  │  │  └─ (Review completion) → [S20]
   │  │  │     ├─ (Preview report) → [S29: draft]
   │  │  │     └─ (Finalize) → [S27] Finalized record
   │  │  └─ Finalized visit → [S27]
   │  ├─ Follow-up row → [S21]
   │  │  ├─ (Edit / reschedule) → [S22]
   │  │  ├─ (Contact) → [S14] → [S15] Manual contact record
   │  │  └─ (Book / do corrective work) → [S16]
   │  └─ (History) → [S26]
   │     ├─ Service record → [S27]
   │     │  ├─ (Report / report versions) → [S29]
   │     │  └─ (Correct / void) → [S28]
   │     ├─ Contact record → [S15: view]
   │     └─ Other event → inline event detail / linked record
   └─ [S04] Customers
      ├─ Customers / Equipment switch → [S04] / [S09]
      ├─ (Add customer) → [S06]
      └─ Customer row → [S05]
         ├─ (Edit) → [S06]
         ├─ (Contact / record contact) → [S14] / [S15]
         ├─ (Add site) → [S08]
         ├─ Site row → [S07]
         │  ├─ (Edit) → [S08]
         │  ├─ (Add equipment) → [S11]
         │  ├─ (Book / start visit) → [S16]
         │  └─ Equipment row → [S10]
         │     ├─ (Edit) → [S11]
         │     ├─ (Move equipment) → [U03: move]
         │     ├─ (Add plan) → [S13]
         │     ├─ Plan row → [S12] → [S13]
         │     ├─ (One-off work) → [S16]
         │     └─ History / follow-up → [S26] / [S21–S22]
         └─ Customer/site history → [S26]

[S30] Settings
├─ [S31] Business and technician profile
├─ [S23] Inspection templates
│  └─ [S24] Template view/editor → [S25] Item editor
├─ [S32] Reminders and notification status
├─ [S33] Data and recovery
│  ├─ [S34] Create / verify backup
│  ├─ [S35] Inspect / restore backup
│  ├─ [S36] Portable CSV export
│  └─ [S37] Initial CSV import
└─ [S38] Help, privacy, and app information

Shared surfaces
[U01] Unsaved-form / leave-working-draft behavior
[U02] Scoped record chooser
[U03] Lifecycle, dependency, date-change, and move confirmation
[U04] Photo intake and photo viewer
[U05] Save-file, external-viewer, and share handoffs
[U06] Equipment/service selection for a visit
[U07] Save, storage, or unreadable-data recovery
[U08] Search / filters / sort

System handoffs, not ServiceLoop destinations
Dialer · SMS composer · email composer · map/navigation app
Camera app · selected-photo/document picker · save-document picker
Android Sharesheet · optional external PDF viewer
Android notification settings · Android storage settings · browser
```

**Back policy:** return to the actual caller with its query, filter, and scroll position preserved. A notification opens the appropriate item with Home as its fallback parent. A missing/deleted item opens its containing list with an explanation; it never recreates the record. Switching primary destinations invokes U01 when leaving an editor. At the root, Android Back leaves the app; it is not intercepted to advertise or demand a backup.

# C. Conceptual information model and vocabulary

| Record / term | Meaning and relationships | Status vocabulary |
|---|---|---|
| Business profile | The local report issuer and technician; not an online account. | Incomplete / ready for reports. |
| Customer | The person or organization receiving service; owns one or more sites in this dataset. | Active / archived. |
| Site | A named service location belonging to one customer. Its address and access notes are separate. | Active / archived; also effectively inactive if its customer is archived. |
| Equipment | A particular machine, with an app reference and current site. Keeps location-change history. | Active / retired; may also be inactive through an ancestor. |
| Service plan | A named recurring service for one machine, interval, current due date, optional template, and optional historical baseline information. | Active / paused / ended. Ended is terminal; create a new plan to restart. |
| Current obligation | The outstanding cycle of a plan. Its due date can be deliberately revised; booking does not replace it. | Upcoming / due soon / due today / overdue are derived date labels. Fulfilled or ended cycles remain historical. |
| Visit | A container for work at one site. May begin as a booking or directly as fieldwork/historical entry. | Booked → in progress → finalized. Booked → cancelled. Discarded working data leaves a cancelled visit shell. A finalized visit can later be voided through a revision. |
| Work item | One named service or one-off job on one machine within a visit. A recurring item points to one plan obligation. | Outcome unreviewed / completed / partially completed / not performed. Separately: fulfill obligation / record only. |
| Inspection copy | The questions, item types, units, required flags, and answers actually used for a work item. | Required checks complete / required checks outstanding; counts of issues and unchecked items remain separate. |
| Finding | An observation associated with a work item or checklist item. Describes what was found, not an automated safety verdict. | Historical observation; no synthetic equipment “safe” state. |
| Contact entry | Technician-entered contact attempt/outcome, date, channel, and internal note; optionally linked to a follow-up or report version. | Recorded / corrected / entered in error. Never inferred from app switching. |
| Follow-up | A specific next action: customer contact or corrective work. | Open / resolved / cancelled. “Overdue” is a date label on an open follow-up. |
| Finalized service record | A fixed revision of a visit, including identity snapshots, work outcomes, inspection content, report selection, and scheduling effects. | Current / superseded / voided; original revisions remain readable. |
| PDF report version | Stored document generated from one specific service-record revision and fixed report content. | Not yet generated / preparing / ready / failed / file unavailable. “Sent” and “received” are not automatic states. |
| Backup | A restorable, versioned snapshot of durable records plus stored attachments and generated PDFs. | Preparing / writing / verified / unverified / failed. |
| CSV export | Human-readable, relational records and file manifests, without image/PDF bytes. | A portable export, never described as a backup. |

A finding can coexist with a completed inspection. A completed inspection does not compel a “service fulfilled” decision. A fulfilled inspection plan can coexist with an open corrective follow-up. Resolving a follow-up does not rewrite the original finding or report.

**Identity:** stable internal identities are distinct from editable names, serial numbers, and display codes. Visit/report identifiers include a unique component, not merely a restorable sequential counter. Revisions also have unique identifiers; restoring an older backup cannot make two different issued files indistinguishable just because both are locally numbered “revision 2.”

# D. Screen-by-screen functional map

## D0. Shared interaction contracts

### U01 — Saving, leaving, and cancelling

**Ordinary explicit-save editors:** S06, S08, S11, S13, S15, S16, S22, S24, S25, and S31. Inputs show “Unsaved changes” after modification. Save validates and commits all fields together. The previous live record remains intact until success. Cancel or Back with changes opens U01; a clean form returns immediately. Nested child creation is explicitly saved as its own record; cancelling the parent does not delete that saved child.

**Durable working editors:** S18–S20 and S28. Changes are persisted as working progress, not final service truth. Display **Saving…**, **Saved on this device at [time]**, or **Not saved — retry**. Changes can be invalid/incomplete and still be retained in a draft; finalization is separately validated. Back means “leave this saved draft,” not cancel. No misleading Cancel control appears on an already-autosaved working screen.

Small ordinary forms retain state across normal UI recreation where possible, but unsaved edits are **not** represented as durable records. An abrupt process/device failure can lose them. Durable fieldwork is stored as it is entered, not postponed until an app-exit callback. The last displayed successful save is the recovery promise, not the last keystroke. [P05]

| ID | Control | Result / exception |
|---|---|---|
| U01-A01 | Keep editing | Close the leave sheet; change nothing. |
| U01-A02 | Discard unsaved changes | Discard only the current ordinary form; return to caller. Already-saved child records remain. |
| U01-A03 | Save and leave | Same validation/commit as that screen's Save; errors keep the form open. |
| U01-A04 | Leave working visit/draft | Flush pending changes and return. If saving fails, open U07 rather than silently discard. A force-kill can still lose uncommitted input. |

Required text is non-whitespace. Names/titles allow 120 characters, identifiers 120, short checklist answers 500, addresses 1,000, and narrative notes 4,000; counters appear near limits. These are proposed product limits, not Android limits. Optional blank fields are omitted or labelled “Not recorded”; they are never fabricated. Phone fields tolerate international prefixes and formatting; email validation checks obvious syntax, not deliverability. Date fields support picker and accessible typed entry. Numeric readings accept finite signed decimals, including zero; localized decimal input is normalized without changing its numeric value.

### U02 — Scoped record chooser

**Purpose/entry:** relationship fields in S08, S11, S13, S16, S22 and S24, plus relevant list filters. The caller supplies entity type, scope, whether clearing is allowed, and whether creation is offered. Back returns without replacing the current value.

Shows name, identifier/context, inactive label where applicable, and current selection. Default excludes inactive entities. Selecting a different customer clears incompatible site/equipment choices in the **form**, with a visible explanation. Nothing is silently reparented.

| ID | Control | Result / exception |
|---|---|---|
| U02-A01 | Search / clear search | Filter available choices by that record's names/identifiers; no live-data change. |
| U02-A02 | Record row / Select | Return the chosen active record; revalidate if it changed before Save. |
| U02-A03 | None / clear selection | Offered only for optional links, such as a plan template or contact follow-up site. |
| U02-A04 | Add new [record] | Offered only where the caller explicitly allows it; open its editor, then return the saved record. Cancelling creation restores the chooser. |
| U02-A05 | Include inactive | Only historical-entry and archival-inspection callers permit it. Inactive choices are clearly labelled and cannot create future work. |
| U02-A06 | Cancel / Back | Leave the caller's selection unchanged. |

### U03 — Lifecycle, dependency, date-change, and move sheets

**Purpose/entry:** named archive/retire/pause/end/restore/delete/change-date/move actions below. Each sheet names the target, describes the exact downstream effects, and lists blockers before any mutation. Reason is required for ending a plan, manual due-date changes, ownership moves, visit cancellation, and cascading cancellations; optional for ordinary pause/archive. The caller supplies a meaningful default reason for automatic cascades.

| ID | Control | Result / exception |
|---|---|---|
| U03-A01 | Confirm [specific action] | Commit the advertised changes together; disabled while blocked or required input is missing. Failure leaves all affected live records unchanged. |
| U03-A02 | Cancel / Back | No change. |
| U03-A03 | Open blocking visit / dependent record | Open the exact blocker; return to recheck the proposed operation afterward. |
| U03-A04 | Destination customer/site | Move mode only; U02 active choices. Show same-customer versus ownership-transfer consequences. |
| U03-A05 | New due date / reason | Date-change mode only; show old/new dates and “This does not record a service.” |

Deletion is offered only where specifically permitted below. There is no general “delete anything” action or hidden swipe-to-delete. Archive is not deletion and does not free historical attachments.

### U04 — Photo intake and viewer

**Entry:** business logo in S31; equipment identification photo in S11; work photos in S19; new correction photos in S28. Historical photos are view-only. Camera/picker results are copied into the app's own persistent storage before being labelled attached; a temporary picker URI is not the permanent record. [P06–P08]

One logo and one current equipment photo are supported. Work items support multiple still images, subject to available storage; there is no claim of unlimited capacity. Imported images become orientation-corrected, size-optimized copies with location metadata removed. The original external image is not edited or deleted. The exact production resolution/quality requires legibility testing, especially serial plates; the UI explicitly says “Optimized copy stored.” There is no annotation, video, OCR, or image-enhancement subsystem.

| ID | Control | Result / exception |
|---|---|---|
| U04-A01 | Take photo | Open an installed camera for a supplied private output location. Missing handler → explain and offer Choose photo. Cancelling does not attach an empty file. |
| U04-A02 | Choose photo | System selected-photo picker, with document-picker fallback when appropriate. Only chosen files are read; no broad photo-library access. Cancellation leaves existing photos unchanged. |
| U04-A03 | Use photo | Validate readable image, copy/optimize, then attach to the form or durable draft. Show copying progress; unreadable/oversized-for-safe-decoding inputs are rejected with “Choose another image.” |
| U04-A04 | Photo thumbnail / view | Full-screen image, caption, equipment/work context, and whether it is selected for the report. |
| U04-A05 | Zoom in / zoom out / fit | Accessible alternatives to pinch; view state only. |
| U04-A06 | Previous / next | Only in a multi-photo collection; disabled at its ends. |
| U04-A07 | Caption | Optional 500-character **customer-visible if selected** caption. Ordinary form Save or draft autosave applies. |
| U04-A08 | Replace / remove | Replace applies to logo/equipment photo; remove applies to editable attachments. Confirm removal from a working draft. Immutable historical references are retained; replacing an equipment photo does not change past reports. |
| U04-A09 | Close / cancel intake | Return to caller. Existing attached files stay; an unaccepted intake is abandoned. |

Photos are internal until explicitly selected for a report. Picking an image does not mean it is already durably copied. Interrupted imports show “Photo not attached” and can be retried; already-saved work remains usable.

### U05 — File output, external viewing, and sharing

**Entry:** S29, S34–S37. Android's selected-document and sharing facilities are used, not unrestricted filesystem access. A provider may be local, removable, or cloud-backed and can be unavailable. [P09–P10]

| ID | Control | Result / exception |
|---|---|---|
| U05-A01 | Save a copy / choose destination | Open the system save-document picker with a suggested filename. Cancel changes nothing. File creation/writing is tracked separately from preparing the local source. |
| U05-A02 | Share | Offer the final file using temporary read access. Returning from the Sharesheet is not delivery evidence. No service/follow-up state changes. |
| U05-A03 | Open in another app | Optional handler for a ready PDF; failure keeps in-app viewing available. |
| U05-A04 | Retry / choose another destination | Reuse the prepared source; no repeated service finalization or data import. |
| U05-A05 | Cancel operation | Stop before completion where safe; remove partial output when the provider permits. Otherwise warn that an incomplete external file may remain. Never claim a failed write is a backup. |

Outputs use readable unique filenames. Existing files are not silently overwritten. Exporting/sharing cannot revoke copies afterward. ServiceLoop does not infer remote upload completion, recipient access, or third-party encryption from a provider's local write acknowledgement.

### U06 — Select equipment and service work

**Entry:** S16 and S18. One visit site is shown. Active equipment there is offered for live work; search covers name, make/model, serial, and app/internal references. Expand a machine to see active plans and **Add one-off service**. Selections are staged until Apply.

| ID | Control | Result / exception |
|---|---|---|
| U06-A01 | Search / clear | Restrict visible machines; selected hidden items remain listed in a selection summary. |
| U06-A02 | Select plan | Select that plan's current obligation once. An obligation already reserved by another booked/in-progress visit is disabled with an Open visit link. |
| U06-A03 | Add one-off service | Required service name; optional template through U02. Adds a separate work item without recurrence. Same-machine one-offs can have distinct names. |
| U06-A04 | Remove selected work | Before fieldwork: remove selection. After work starts: unused rows remove directly; rows with answers/photos require a loss-count confirmation. Recommend “Not performed/partial” instead when observations must be retained. |
| U06-A05 | Add equipment | Live work only; S11 prefilled with this site, then return. The equipment record remains even if the visit form is cancelled. |
| U06-A06 | Include inactive / equipment at another site | Historical-entry mode only. Another-site equipment requires explicit confirmation that it was serviced at the selected site on that date. This records historical context, not a current move; those items cannot advance a current obligation. |
| U06-A07 | Apply selection | Require at least one work item. Working visits capture the current template when a new item is added. Existing answers are not replaced. |
| U06-A08 | Cancel / Back | Restore the pre-sheet work selection. |
| U06-A09 | Open reserving visit | Leave chooser for S17; no duplicate obligation reservation is created. |

### U07 — Save/storage/unreadable-data recovery

**Entry:** any failed read/write; prominent on working drafts and data operations. Shows what was last durably saved, what failed, and whether the existing dataset is still usable. “Storage full” is distinct from “Cannot open this file” and “Data could not be read.”

| ID | Control | Result / exception |
|---|---|---|
| U07-A01 | Retry | Retry only the failed operation; preserve unsaved input in memory while this screen remains alive. |
| U07-A02 | Open device storage settings | External Android handoff; return and retry. No ServiceLoop data is deleted automatically. |
| U07-A03 | Copy unsaved text | Explicitly copy visible unsaved textual input for manual rescue, with a privacy warning. Not a backup of photos or structured records. |
| U07-A04 | Leave without unsaved changes | Explicit loss warning; available only when the existing saved state can still be used. |
| U07-A05 | Data and recovery / restore | S33/S35. If the dataset cannot open, show a limited recovery entry rather than creating a new empty dataset over it. |
| U07-A06 | Help | S38. Support exports never attach business data automatically. |

Import/restore/finalization failure keeps the last committed dataset. If corruption or hardware failure makes that impossible, the app states that it cannot establish a usable dataset and offers a validated backup restore; it does not claim guaranteed repair.

### U08 — Search, filters, and sort

**Entry:** S03–S05, S07, S09, S10, S23, S26 where specified. Case-insensitive substring search; no special query language. Show selected filters and result count. Empty dataset and “No matches” have different messages. Lists retain their own filter/sort choices; Home totals never inherit a list's hidden filters.

| ID | Control | Result / exception |
|---|---|---|
| U08-A01 | Search / clear | Update matching rows; no business-data changes. |
| U08-A02 | Filters | Open the screen's enumerated options. Apply changes list state; Cancel retains prior filters. |
| U08-A03 | Sort | Apply one of that screen's enumerated orders. Stable identity breaks ties. |
| U08-A04 | Clear filters | Reset that list to its documented default, not other lists. |
| U08-A05 | Retry loading | Re-read existing records after an error; never treat read failure as a genuine empty list. |

## S01 — Welcome and first useful action

**Purpose/entry/Back:** first launch with no business records; after an explicit full-data erase. Returning from setup returns here until a route is completed. Back exits the app.

**Visible:** proposition; “Your work is stored on this device”; “Uninstalling/clearing app data or losing the device can lose records unless you keep a backup”; distinction between backup and CSV; no account fields. No runtime permissions requested here. Uninstall/storage behavior is a platform constraint, not a cloud-recovery promise. [P06]

| ID | Control | Result / cancellation |
|---|---|---|
| S01-A01 | Add first customer | S06; business profile can wait. Cancel returns here. |
| S01-A02 | Import customer/equipment list | S37; only validated import makes records. |
| S01-A03 | Restore backup | S35; inspection alone changes nothing. |
| S01-A04 | Business details | S31, optional now. |
| S01-A05 | Start empty | S02 with zero-state guidance. Marks welcome seen, not setup complete. |
| S01-A06 | How storage and recovery work | S38 storage topic, then return. |

If an existing dataset fails to load, do not show this as “first launch”; show U07.

## S02 — Home

**Purpose/entry/Back:** default operational overview and main tab. No financial metrics, asset-value dashboard, or customization builder.

**Visible:** today's date in the business timezone; business display name when supplied; five summaries: **Overdue service plans**, **Due today/soon**, **Booked visits today and next 7 days**, **Unfinished visits**, **Open follow-ups due today/overdue**. Unfinished count excludes finalized records but includes historical-entry drafts. Counts are records of the named type, not machine counts. A machine with two overdue plans contributes two obligations.

Below them, show attention rows only when relevant: past bookings not started, report generation/file failures, saved correction drafts, and backup status/staleness. Empty state says no records yet, not “All equipment is serviced.”

| ID | Control | Result |
|---|---|---|
| S02-A01 | Overdue count | S03 Due: overdue, all customers, soonest due first. |
| S02-A02 | Due today/soon count | S03 Due: today through lead-window end, no overdue; default window 30 days. |
| S02-A03 | Booked visits count | S03 Visits: booked today through next 7 days. |
| S02-A04 | Unfinished count | S03 Visits: in progress, newest activity first. |
| S02-A05 | Follow-ups count | S03 Follow-ups: open, due through today. |
| S02-A06 | New visit | S16 with Book / Start now / Enter past service modes. |
| S02-A07 | Add customer | S06; prominent only while empty, otherwise available on Customers. |
| S02-A08 | Settings | S30. |
| S02-A09 | Backup status / reminder | S33. “Never backed up” is distinct from “Last verified [date].” |
| S02-A10 | Past bookings attention | S03 Visits: booked before today. Booking is not auto-cancelled or completed. |
| S02-A11 | Report attention | S26 filter: finalized records with missing/failed report output. |
| S02-A12 | Correction draft attention | S26 filter: saved correction drafts; row opens S28. |
| S02-A13 | Work / Customers navigation | S03 / S04; preserve each destination's last list state. |

Zero-count cards remain readable and open their empty filtered list. Read failure replaces affected totals with “Unavailable — Retry,” not zero; retry uses U08-A05.

## S03 — Work lists

**Purpose/entry/Back:** main Work tab or a specific Home/context/notification filter. Tabs: **Due**, **Visits**, **Follow-ups**. Context chips identify customer/site filters and can be cleared. Root Back exits; contextual entry returns to caller.

| Tab | Rows | Filters / sort, fully enumerated |
|---|---|---|
| Due | Equipment + reference, plan name, customer/site, due date/date label, booked/in-progress link badge, last manually recorded contact where present. | State: all outstanding, overdue, today, due soon, later. Customer; site scoped to customer. Sort: due ascending (default), due descending, customer then equipment. Only active plans in active contexts qualify. |
| Visits | Reference, customer/site, appointment when booked, actual date for drafts, machine/work-item counts, status, last saved activity. | Status: booked, in progress, cancelled, all current (booked + in progress, default). Date: all, today, next 7 days, past, custom inclusive range. Customer/site. Sort: scheduled date ascending (default for bookings), most recently changed. Finalized history is S26, not duplicated as an editable visit list. |
| Follow-ups | Type badge, title, customer/site/equipment context, due date, open/resolved/cancelled label, linked visit if any. | Type: both/contact/corrective. Status: open (default)/resolved/cancelled/all. Due range: all/overdue/through today/next 7 days/custom. Customer/site. Sort: due ascending (default), newest first. |

Search uses the row's names/references and follow-up title; date controls filter their tab's relevant date. Booked visits without a time use the end of that day only for sorting, labelled **Date only**, not an invented appointment time.

| ID | Control | Result |
|---|---|---|
| S03-A01 | Due / Visits / Follow-ups | Switch list without changing records. |
| S03-A02 | Search, Filters, Sort, Clear | U08 with the options above. |
| S03-A03 | Due row | S12; appointment badge separately opens S17. |
| S03-A04 | Visit row | S17; an in-progress row also exposes Resume → S18. |
| S03-A05 | Follow-up row | S21. |
| S03-A06 | New visit | S16, carrying an active customer/site filter where unambiguous. |
| S03-A07 | New follow-up | S22; type must be chosen. |
| S03-A08 | History | S26; preserve explicit customer/site context, not operational status filters. |
| S03-A09 | Home / Customers | S02 / S04. |

No bulk “mark serviced” or notification-snooze action exists here. Empty Due explains that equipment without a plan has no scheduled obligation and links to Equipment; no-results offers Clear filters. Cancelled rows remain available as history but cannot be started.

## S04 — Customer register

**Purpose/entry/Back:** Customers primary destination. Switch between **Customers** and **Equipment** without adding another bottom-navigation item.

**Visible:** customer name, primary contact, active sites/equipment counts, number of outstanding overdue plans. Search name/contact/phone/email; filters Active (default), Archived, All; sort name ascending (default), recently added. U08 applies.

| ID | Control | Result |
|---|---|---|
| S04-A01 | Add customer | S06 create. |
| S04-A02 | Customer row | S05. |
| S04-A03 | Equipment switch | S09 global register. |
| S04-A04 | Search / filter / sort / clear | U08. |
| S04-A05 | Import existing list | Empty dataset only → S37; absent once initial-import eligibility is lost. |
| S04-A06 | Home / Work | S02 / S03. |

Empty state offers Add customer/Import. Archived matches are visibly labelled; no-results is not a suggestion to duplicate a hidden archived customer.

## S05 — Customer detail

**Purpose/entry/Back:** S04, contextual links, or search; Back returns to caller.

**Visible:** name/contact fields; internal customer notes; active and archived site rows; equipment and due-work counts; latest service and contact entries; open follow-ups. Default site labelled. Customer notes are never report content.

| ID | Control | Availability / result |
|---|---|---|
| S05-A01 | Edit customer | Active → S06. Archived profile is read-only until restored. |
| S05-A02 | Contact / record contact | Active → S14 / S15. Disabled for archived customer with explanation. |
| S05-A03 | Site row | S07, including read-only archived sites. |
| S05-A04 | Add site | Active → S08. |
| S05-A05 | Add equipment | One active site → S11 preselected; several → U02 site chooser; none → create/restore a site first. |
| S05-A06 | Equipment / due work / history / follow-ups rows | S09 / S03 Due / S26 / S03 Follow-ups, scoped to this customer. Counts use the explicit labels and scope. |
| S05-A07 | Book/start visit | Active → choose an active site if needed, then S16. |
| S05-A08 | Add contact follow-up | S22 with customer selected. |
| S05-A09 | Show archived sites | Toggle only this site list; does not reactivate them. |
| S05-A10 | Archive customer | U03 cascade preview under R06; blocked by an in-progress visit/correction affecting this customer. |
| S05-A11 | Restore customer | Archived → U03; does not resume plans, recreate bookings, or reopen follow-ups. |
| S05-A12 | Delete unused customer | Only if no equipment, plans, visits, contact entries, follow-ups, or retained history exists anywhere below it. Deletes its empty sites together, with explicit counts. Otherwise absent; Archive is the alternative. |

An archived customer's historical service/report rows remain accessible. Old visits belong to their historical customer even when a machine has subsequently moved to a different owner.

## S06 — Customer editor

**Purpose/entry/Back:** create from S01/S04/U02, edit from S05. U01 explicit-save editor.

**Inputs:** Customer name required. Contact name, phone, email, internal customer notes optional. On **creation only**, a compact **Main service site** section defaults site name to “Main site”; address optional. “Site has different contact details” defaults off; when on, site contact name/phone/email become optional independent fields. Site access notes optional and explicitly internal. The app does not request access to the phone's address book.

On edit, site/address fields are absent and replaced by the actual linked site row; customer fields cannot accidentally overwrite site details. Similar name or matching phone/email produces a possible-duplicate warning, not an automatic merge or absolute prohibition.

| ID | Control | Result / failure |
|---|---|---|
| S06-A01 | Save customer | Validate; create customer + initial site together, or update customer fields only. Return new selection to a chooser; otherwise S05. |
| S06-A02 | Different site contact switch | Reveal/disable the site override fields in the creation form; no live change before Save. |
| S06-A03 | View possible duplicate | Open candidate after U01 safeguards; creates nothing. |
| S06-A04 | Save as separate customer | Explicitly acknowledge warning and save distinct identity. |
| S06-A05 | Cancel / Back | U01; no partially created customer/site. |
| S06-A06 | Edit site row | Existing customer only → S08 after resolving unsaved customer changes. |

Blank contact fields keep the record usable; matching call/email buttons later remain disabled. Missing address blocks navigation handoff, not customer creation.

## S07 — Site detail

**Purpose/entry/Back:** S05, equipment/visit links, or contextual selection. Shows actual owning customer link, site name/address, inherited or overriding contact with its source, **internal access notes**, active equipment, due work, visits, follow-ups, and history.

| ID | Control | Availability / result |
|---|---|---|
| S07-A01 | Customer link | S05. |
| S07-A02 | Edit site | Active context → S08. |
| S07-A03 | Contact / navigate / record contact | Active → S14 / navigation option there / S15. Missing address/contact disables only the affected handoff. |
| S07-A04 | Add equipment / equipment row | S11 / S10. |
| S07-A05 | Book/start visit | Active → S16 preselected site. |
| S07-A06 | Due / visits / follow-ups / history | Contextual S03 or S26; zero-count destinations stay navigable. |
| S07-A07 | Make default site | Active site → confirm; changes future preselection only. Existing equipment/visits do not move. |
| S07-A08 | Show retired equipment | Local list toggle. |
| S07-A09 | Archive site | U03/R06; requires another default if this is the customer's default and other active sites exist. Blocks unfinished fieldwork/corrections. |
| S07-A10 | Restore site | Parent must be active; no automatic restart of paused plans/cancelled bookings. |
| S07-A11 | Delete unused site | Only no equipment/history/visits/contacts/follow-ups and not the customer's sole site. Choose another default first if necessary. Otherwise absent. |

An active customer may temporarily have no active sites after archival; Add equipment/Book then asks for a new or restored site. It never silently adds a replacement “Main site.”

## S08 — Site editor

**Purpose/entry/Back:** S05/S07/U02; U01 applies.

**Inputs:** owning customer required and active; site name required, default “Main site” for the first site and blank thereafter; address optional; **Use customer contact** on by default. Off reveals optional site contact name/phone/email. Site access notes internal, optional. **Default site** defaults on only if no other active default exists. Editing a nonempty address affects future visits/reports only, not finalized snapshots.

| ID | Control | Result / failure |
|---|---|---|
| S08-A01 | Customer selector | U02. Editable for new sites, or an existing site with no equipment, historical events, visits, contacts, or follow-ups. Otherwise read-only: create a new site and move equipment instead. |
| S08-A02 | Use customer contact | Toggle inheritance; blank overrides do not silently fall back per field. The preview shows exactly which contact will be used. |
| S08-A03 | Default site switch | Stage new preselection choice; one default among active sites. |
| S08-A04 | Save site | Validate, warn for same-customer matching site name/address, require explicit Save separately if duplicate intended, then commit and return. |
| S08-A05 | View matching site | Open duplicate candidate after U01; no merge. |
| S08-A06 | Cancel / Back | U01. |

Reassigning an unused site changes its customer and inherited contact; confirmation displays both. The source customer must retain at least one site, and a replacement active default must be chosen first when applicable. Referenced sites cannot change customer through this editor, even if their equipment was later removed.

## S09 — Equipment register

**Purpose/entry/Back:** Equipment switch in Customers; scoped links from S05/S07. Shows machine name/type, make/model, app reference, serial/internal ID when present, customer/site, active/retired status, count of active plans and nearest due date.

**Search:** machine name/type/make/model/serial/internal/app reference, customer/site. **Filters:** active (default), retired/inactive, all; customer; site; plans: any/has active plans/no active plan; due: any/overdue/today-or-soon. **Sort:** name (default), nearest due first with no-plan machines last, recently added. U08 applies.

| ID | Control | Result |
|---|---|---|
| S09-A01 | Add equipment | S11; inherit explicit site or choose customer/site. |
| S09-A02 | Equipment row | S10. |
| S09-A03 | Search / filters / sort / clear | U08 with the exact options above. |
| S09-A04 | Customers switch | S04. |
| S09-A05 | Import list | Only initially empty → S37. |
| S09-A06 | Home / Work | S02 / S03. |

Machines without a service plan show **No recurring service configured**, not “Nothing due.” Retired machines never contribute to operational due totals.

## S10 — Equipment detail

**Purpose/entry/Back:** register, site, selected work, or history context. Shows identity fields, current site/customer links, photo, **internal equipment notes**, status, plan rows, last recorded service, last recorded findings with dates, unresolved corrective follow-ups, and location-change history. A past finding is labelled historical; no inferred “safe” badge.

| ID | Control | Availability / result |
|---|---|---|
| S10-A01 | Edit equipment | Active context → S11. |
| S10-A02 | Photo | U04 view-only; edit/replace belongs to S11. |
| S10-A03 | Current customer/site | S05/S07. |
| S10-A04 | Add service plan | Active → S13. |
| S10-A05 | Plan row | S12, including paused/ended rows in the Plans section. |
| S10-A06 | Book service / start one-off work | S16/U06 with this machine; no implicit completion. |
| S10-A07 | Add corrective follow-up | S22 with machine/context fixed initially. |
| S10-A08 | History / existing follow-up | S26 equipment scope / S21. |
| S10-A09 | Move equipment | U03 move: active destination, reason, dependency review; apply R06. |
| S10-A10 | Retire equipment | U03; pauses active plans, cancels applicable future work/follow-ups only after explicit dependency review. Unfinished fieldwork/correction blocks it. |
| S10-A11 | Return to active use | Retired, active site/customer → U03. Existing plans remain paused until individually resumed. |
| S10-A12 | Delete unused equipment | Only no plans, work/visit references, contact/follow-up references, or historical move events. Deletes its unreferenced identification photo after confirmation; otherwise absent. |
| S10-A13 | Enter past service | S16 historical mode; retired equipment permitted, but cannot silently reactivate a plan. |

**Move sheet specifics:** old and new customer/site are visible. In-progress work blocks movement. A booked visit containing this machine must first have the machine removed or be cancelled; the app does not move a multi-machine appointment. Same-customer moves carry all open equipment-linked follow-ups, both contact and corrective, to the new site with a location-change note, and keep due dates. Contact follow-ups linked only to the customer/site stay where they were. Cross-customer moves require open equipment-linked follow-ups to be resolved/cancelled first and pause recurring plans pending review by the new owner. All past visit identities/reports remain unchanged. New-customer history does not inherit the old customer's visits merely because the machine moved.

## S11 — Equipment editor

**Purpose/entry/Back:** S09/S10/S07/U06; U01 applies.

**Inputs:** Equipment name required. Type optional free text with suggestions from existing values, not a configurable taxonomy. Make, model, serial number, user internal ID, internal notes optional. Site required when creating; default from entry context or sole active site. One optional identification photo through U04. App equipment reference is generated, read-only, and always exists.

| ID | Control | Result / failure |
|---|---|---|
| S11-A01 | Customer/site chooser | Create only; U02 permits creating customer/site. On edit, current site is read-only with Move equipment → S10-A09. |
| S11-A02 | Add / replace / remove photo | U04; form save controls adoption. |
| S11-A03 | Save equipment | Require name/site. Nonblank internal ID must be unique across active and inactive machines. Exact normalized serial match warns but permits distinct machines after confirmation; serials are not assumed universally unique. |
| S11-A04 | View duplicate candidate | S10 after U01 handling. |
| S11-A05 | These are different machines | Available for serial/name warnings, never duplicate internal IDs; continue save. |
| S11-A06 | Cancel / Back | U01; no half-created machine or silently accepted photo. |

Missing serial/model is valid. The app reference and equipment name provide a usable identity; report fields say “Not recorded” or omit optional values rather than inventing identifiers.

## S12 — Service-plan detail

**Purpose/entry/Back:** Due row or Equipment Plans. Shows plan name, equipment/customer/site, state, recurrence, current due/date status, latest applied completion, optional known-last-service baseline, template link, current booking/reservation, and dated plan-change/fulfillment events.

| ID | Control | Availability / result |
|---|---|---|
| S12-A01 | Edit plan | Active/paused → S13. Ended is read-only. |
| S12-A02 | Equipment / customer / site / template link | S10/S05/S07/S24. Archived template remains viewable. |
| S12-A03 | Contact customer / log contact | Active context → S14/S15, carrying this plan as optional context. |
| S12-A04 | Book / perform service | Active with no reservation → S16. Existing reservation shows Open visit instead → S17. |
| S12-A05 | Change next due date | Active/paused → U03 old/new date + required reason. Does not record service. Must remain later than the latest applied completion, when one exists. |
| S12-A06 | Pause plan | Active → U03. Retain due date; remove from operational due/reminder eligibility. Existing booking remains with a paused-plan warning. |
| S12-A07 | Resume plan | Paused + active equipment/site/customer → U03. Default keep the original due date, even if overdue; a new date requires an explicit reason. |
| S12-A08 | End plan | Active/paused → U03 reason. End without claiming service; remove its unstarted selections from booked visits after listing affected visits. Empty bookings become cancelled with the reason shown. In-progress selections remain record-only and are flagged. |
| S12-A09 | History | S26 scoped to equipment and this plan. |
| S12-A10 | Delete unused plan | Only never selected in a saved visit and no recorded service/contact/follow-up references. Otherwise absent; End plan is the alternative. |

An ended plan has no Resume action. A paused plan's preserved overdue date is explanatory history, not an active obligation count.

## S13 — Service-plan editor

**Purpose/entry/Back:** S10/S12; U01 applies.

**Inputs:** equipment fixed; plan name required; **Every [positive whole number] [days/weeks/months/years]**, initially 6 months as an editable convenience, not a trade recommendation; **Next due date** required, initially unselected; **Known last service date** optional at creation, not later than today; optional brief source note for that baseline; **Inspection template** optional, active choices only. Blank known-last date creates no claimed past service. Selecting one offers a visible proposed next date, never silently confirms it.

An interval may be 1–999 units provided the resulting date is representable; impossible dates are rejected before save. Next due may be past/overdue, but must be later than a supplied last-service baseline. There are no runtime counters, fixed-anniversary modes, or “repeat appointment” fields.

| ID | Control | Result / failure |
|---|---|---|
| S13-A01 | Template chooser / None | U02; may create a template through S24. |
| S13-A02 | Use calculated next date | Copy the displayed baseline-plus-interval proposal into Next due; still requires Save. |
| S13-A03 | Save plan | Create active plan/current obligation, or update name/interval/template. Same-equipment matching name warns and requires explicit distinct-service acknowledgement. |
| S13-A04 | Keep current due / recalculate next due | When editing interval: default **Keep current due**. Recalculate is offered only with an actual/baseline date; displays old/new and requires reason. Otherwise use S12-A05. |
| S13-A05 | Cancel / Back | U01. |

Once actual service history exists, its latest completion is not editable through a “last service” field. Use a historical entry or correction instead. Changing the template does not replace a checklist already captured in an in-progress visit.

## S14 — Contact and directions

**Purpose/entry/Back:** contextual Contact actions from customer, site, plan, visit, or follow-up. A sheet shows the exact recipient and whether details come from the customer or site. It contains no automatically imported communication history.

**Inputs:** optional editable message draft, prefilled only with a neutral service-context sentence and no access/internal notes. Email subject optional. Recipient edits affect this handoff only unless separately saved in the customer/site editor. No automatic report attachment; sharing a report starts in S29.

| ID | Control | Availability / result / failure |
|---|---|---|
| S14-A01 | Call | Phone present → open dialer with number; technician starts call. Missing handler shows the number with Copy. No call permission or call-log reading. |
| S14-A02 | Text message | Phone present → open SMS composer; no automatic sending. Unsupported messaging app/network is not treated as successful contact. |
| S14-A03 | Email | Email present → email composer with reviewed subject/body. No handler → Copy details; not “Sent.” |
| S14-A04 | Directions | Site address present → map/navigation handoff with address, not live location. Missing/offline navigation remains the external app's responsibility. |
| S14-A05 | Copy number / email / address / message | Explicitly copy the chosen value; show “Copied,” not “Contacted.” |
| S14-A06 | Record contact outcome | S15, contextualized but without an assumed successful outcome. This remains available without a working external app. |
| S14-A07 | Use customer contact / use site contact | Offered when different valid choices exist; changes handoff recipient only. |
| S14-A08 | Edit contact details | Appropriate S06/S08, preserving the current caller. |
| S14-A09 | Close / Back | No contact log, appointment, or follow-up is changed. |

After an external handoff, display **“Returned to ServiceLoop — record the outcome if needed.”** The app does not guess whether the call connected, the email was sent, or the journey occurred. These are ordinary user-confirmed intents, not communication automation. [P08]

## S15 — Manual contact record: create, view, correct

**Purpose/entry/Back:** S14, direct Record contact actions, S21 contact follow-up, or S26 history. Explicit Save under U01.

**Inputs:** customer required; optional site/equipment/plan/report-version context inherited from caller; **Channel** required: phone, text message, email, in person, other; **Occurred at** required, defaults now in business timezone and cannot be future; **Outcome** required, no default: spoke/contact made, no answer, message left, message sent by technician, customer declined, other. Optional internal note; Other requires explanatory note. These labels are technician assertions, not delivery receipts.

When opened from an open contact follow-up, an optional unchecked **Resolve this contact follow-up** control appears, with a required resolution note if selected. Recording a failed attempt normally leaves it open. A separate optional **Next contact date** creates a contact follow-up; no date means no new follow-up. When the originating follow-up remains open, this changes that follow-up's date after an explicit labelled choice rather than creating a duplicate.

| ID | Control | Result / failure |
|---|---|---|
| S15-A01 | Save contact record | Commit log and any explicitly selected follow-up action together. Return to caller with “Recorded manually.” No service dates change. |
| S15-A02 | Context selectors | U02 compatible choices; equipment implies its relevant site/customer. A report link is selectable only within this customer's records. |
| S15-A03 | Resolve originating follow-up | Stage resolution, never inferred from chosen channel. |
| S15-A04 | Set next contact date | Required title defaults “Contact about [context]”; either reschedule the existing open task or create a new task, visibly distinguished. |
| S15-A05 | Edit entry | View mode only → editable form. Saving marks entry corrected with edit time and preserves the previous entered values in contact history. Does not retroactively undo a follow-up resolution. |
| S15-A06 | Mark entered in error | View mode → U03 reason; retain the entry with an error label. Any follow-up changes must be explicitly reviewed separately. |
| S15-A07 | Open linked customer/site/equipment/follow-up/report | Corresponding S05/S07/S10/S21/S29. |
| S15-A08 | Cancel / Back | U01 for edits; view simply returns. |

There is no permanent delete of a saved contact entry. Archived-context entries are viewable; historical correction is allowed, but no new future follow-up can target an inactive context.

## S16 — New visit / booking editor

**Purpose/entry/Back:** Home, Work, customer/site/equipment/plan, or corrective follow-up. Also edits an existing booked visit. U01 applies until a visit is created or booking changes are saved.

**Modes:** **Book a visit**, **Start now**, **Enter past service**. Changing mode preserves compatible form inputs and explains fields that no longer apply. Book mode is the default from Book actions; Start now from working actions; historical mode from History.

**Inputs:** customer/site required; one or more work items through U06; optional internal booking note; booked date required in Book mode; optional time, with **Date only** default; optional duration in minutes only for timed bookings, blank means unknown. Business timezone is shown. Historical mode requires actual service date not later than today and defaults scheduling effects to **Record only**. Live Start now proposes today's service date. No customer-visible promise is created by internal booking notes.

| ID | Control | Result / failure |
|---|---|---|
| S16-A01 | Mode selector | New visits only; does not save anything. Existing booking uses dedicated Start in S17. |
| S16-A02 | Customer/site selectors | U02, with Add customer/site allowed for new visits. Changing site clears selected work after confirmation; existing saved booking site is fixed. Wrong-site booking must be cancelled and recreated. |
| S16-A03 | Select equipment/services | U06; an originating plan is preselected if unreserved. An originating corrective follow-up suggests a one-off item linked to it. |
| S16-A04 | Appointment date / time / duration | Book mode. Past dates warn “This booking is already in the past”; not silently converted into performed work. Time conflicts show an advisory list; technician may book anyway. |
| S16-A05 | Save booking | Require valid site/date/work. Reserve selected obligations and create/update booked visit; return S17. No checklist is yet frozen and no service is recorded. |
| S16-A06 | Start visit | Start-now mode → commit in-progress visit, capture selected templates, open S18. No appointment required. |
| S16-A07 | Create historical draft | Historical mode → in-progress draft clearly labelled “Past service entry,” open S18. Inactive historical contexts permitted through U02/U06, but cannot create future work there. |
| S16-A08 | Cancel / Back | U01. A previously saved booking remains as before. |

Timed appointment validation rejects nonexistent local daylight-saving times and asks for an actual valid time. Repeated clock times show the two offsets for explicit selection. Date-only visits receive no invented minute-level reminder. No calendar permission or external calendar event is involved.

## S17 — Visit summary

**Purpose/entry/Back:** Work visit row, plan booking link, or newly saved booking. Shows visit reference, customer/site/address, booked date/time/zone, selected machines/services, notes, originating follow-up, and status.

A past booking is labelled **Past booking — outcome not recorded**. A paused/ended plan, changed equipment placement, or changed selection is visibly flagged before Start. If the record was finalized while this destination was stale, route to S27 rather than offering Start again.

| ID | Control | Availability / result |
|---|---|---|
| S17-A01 | Start visit | Booked → confirm actual service date; review stale/invalid selections; capture current templates and plan references, then S18. Starts work, does not complete it. |
| S17-A02 | Resume visit | In progress → S18 at last working position. |
| S17-A03 | Edit / reschedule booking | Booked → S16; due dates and contact history unchanged. |
| S17-A04 | Cancel booking | Booked → U03 required reason. Release obligation reservations; preserve cancelled appointment details/history; remove appointment reminders. |
| S17-A05 | Book again | Cancelled → new S16 form copying site/work where still valid. New visit ID; never silently reinstates the old booking. |
| S17-A06 | Contact / directions / record contact | S14/S15; no automatic appointment status change. |
| S17-A07 | Customer/site/equipment/plan links | Corresponding detail screens. |
| S17-A08 | View service record | Finalized → S27. |
| S17-A09 | View originating follow-up | S21 when linked. |

Starting earlier or later than the booking does not rewrite the original appointment; scheduled and actual dates are both preserved internally. No automatic “no-show” classification exists.

## S18 — Working visit overview

**Purpose/entry/Back:** Start/resume from S16/S17. U01 durable-working contract, U07 failures. Back leaves the saved working visit; it does not finalize or cancel it.

**Visible:** visit reference/status; actual service date; customer/site and identification snapshot captured at start; last durable-save time; equipment groups with work-item names, required-check progress, issue counts, and provisional outcome. Separate **Customer-visible visit summary** and **Internal visit notes** fields, both optional. An unbooked visit says so; a historical entry has a persistent banner.

**Inputs:** narrative fields; actual service date (not future); report-photo selection and staged follow-ups are also durable draft data. Work outcomes are reviewed per item, not set by a blanket “complete visit” button.

| ID | Control | Result / failure |
|---|---|---|
| S18-A01 | Work-item row | S19 for that machine/service. |
| S18-A02 | Add/remove selected work | U06. Any removed draft answers/photos are explicitly counted; recurrence untouched. |
| S18-A03 | Service date | Update draft only; later finalization recalculates proposals and requires review. Different from start day prompts a reminder about the one-date visit boundary. |
| S18-A04 | Add corrective follow-up | S22 staged mode, requiring an equipment context. Not yet an active task or notification. |
| S18-A05 | Staged follow-up row | S22 edit; Remove staged follow-up is available before finalization only. |
| S18-A06 | Customer/site/plan details | Open read-only/context detail after pending autosave. Returning never imports newer values into snapshots silently. |
| S18-A07 | Contact / directions | S14 after pending save. Actual contact logging creates its own explicitly saved contact record. |
| S18-A08 | Review and finish | S20; no service/date change yet. |
| S18-A09 | Leave visit | U01-A04; remains in Unfinished. |
| S18-A10 | Discard working visit | U03 severe-loss confirmation listing work items/photos/staged follow-ups. Delete unfinalized fieldwork; retain cancelled visit shell and original booking details with “Working draft discarded.” Release reservations; keep original service obligations due. |

A visit with some useful recorded work should normally be finalized as partial, not discarded. Independent contact entries or pre-existing follow-ups are not deleted with the working draft. A task resolution staged inside the draft is abandoned when the draft is discarded.

## S19 — Service work and inspection

**Purpose/entry/Back:** work item in S18 or S20; Back returns to the visit/review after autosave. One item means one machine plus one named service; several plans on that machine remain separate items.

**Visible:** equipment identifiers, service/plan name, original obligation due date if linked, template name and captured revision, checklist progress, findings, work performed, parts, photos, internal item notes, and linked corrective task. No global “Mark all OK.”

### Inputs and item behavior

| Input | Required/default/validation and meaning |
|---|---|
| Status checklist item | Starts **Not checked**. Choices: OK, Issue found, Not applicable, Not checked. “Issue found” requires a finding description. Required “Not applicable” needs a reason. Not checked never becomes OK automatically. |
| Short-text item | Blank default; up to 500 characters. A required blank can be explicitly marked Not checked with a reason, but remains outstanding for fulfillment. |
| Numeric reading | Blank default; finite decimal and the captured unit. Zero/negative values are allowed unless physically interpreted by the technician; no automatic threshold or diagnosis. Required missing reading has the same Not checked behavior. |
| Optional checklist item | May remain Not checked/blank; shown honestly on report. Does not prevent required-check completion. |
| Work performed | Customer-visible. Required when choosing Completed or Partially completed; may be concise. Not performed instead requires an explanation. |
| Findings | Customer-visible observation text; optionally linked to one checklist item. Each is a separate row with optional proposed corrective action. No severity scoring or “safe to operate” inference. |
| Parts used | Repeated description + positive decimal quantity, default 1, optional unit default “pcs.” No prices, inventory deduction, stock codes, or purchasing. |
| Photos | U04; caption explicitly customer-visible when included. Not included in report by default. |
| Internal item notes | Optional; never printed or copied into customer-visible fields automatically. |
| Provisional outcome | Unreviewed initially; Completed / Partially completed / Not performed. This is a draft choice; final scheduling effects are reviewed in S20. |

**Inspection completion rule:** all required items must have a value or justified Not applicable to qualify as required-checks-complete. Issue found counts as performed inspection, not as an OK result. Required Not checked blocks fulfilling this templated service obligation, but does **not** block preserving/finalizing the visit as partial. Unchecked reasons are required when finalizing incomplete required work. Optional blanks remain visibly unchecked.

| ID | Control | Result / failure |
|---|---|---|
| S19-A01 | Checklist result/value controls | Autosave each change. Switching an Issue found result does not silently delete its finding; offer Keep finding or Remove draft finding with confirmation. |
| S19-A02 | Not checked / not applicable reason | Show required reason fields under the affected item. No numeric value is invented. |
| S19-A03 | Add/edit finding | Inline editor with observation text required; Save adds/updates the draft finding. Cancel leaves prior finding untouched. |
| S19-A04 | Remove finding | Confirm before removal; a related staged follow-up must be removed or kept with a visible independent explanation. Existing live follow-ups are never silently deleted. |
| S19-A05 | Add/edit part | Inline description/quantity/unit form; Save validates positive quantity and updates draft; Cancel does not add a row. |
| S19-A06 | Remove part | Confirm removal from this draft only. |
| S19-A07 | Add / view / remove photo | U04, linked to this work item. |
| S19-A08 | Create/link corrective follow-up | New → S22 staged; existing → U02 scoped to this equipment's open corrective tasks. A matching open task is offered before a duplicate is created. |
| S19-A09 | Plan to resolve linked corrective follow-up | Unchecked by default. Stage resolution date/note for S20, only after work is actually completed; does not resolve immediately. |
| S19-A10 | Provisional outcome | Set one draft outcome; no next-date advancement. |
| S19-A11 | Use another template | Only while this item's inspection has no entered answers/findings/parts/photos/work text. Otherwise absent; add a separate one-off inspection rather than replacing recorded work. |
| S19-A12 | Back to visit / review | Flush pending draft saves under U01; U07 on failure. |

**Embedded sheets with stable identifiers:**

| Surface / entry | Inputs | Controls and result |
|---|---|---|
| **S19-D01 Finding editor**, from S19-A03 | Required observation; optional linked checklist item; optional customer-visible proposed action. | **S19-D01-A01 Save finding:** validate and durably add/update the enclosing work draft. **S19-D01-A02 Cancel/Back:** U01, preserve the prior finding. |
| **S19-D02 Part editor**, from S19-A05 | Required description; quantity defaults 1 and must be positive; optional unit defaults pcs. | **S19-D02-A01 Save part:** validate and durably add/update the work draft. **S19-D02-A02 Cancel/Back:** U01, preserve the prior part. |

Inline finding/part editors are explicit-save mini-forms using U01; after their Save succeeds, the enclosing working item is durably updated. Template labels/units/required flags are not editable here. A template change elsewhere does not alter this copy.

## S20 — Review completion and next dates

**Purpose/entry/Back:** S18 Review and finish. This is the only ordinary workflow that finalizes service and advances plans. It is still a durable draft; Back returns without finalizing.

**Visible/inputs:** actual service date; issuer completeness; per-machine work table; required-check exceptions; findings and follow-up decisions; report preview selections; before/after plan dates; internal-only versus customer-visible sections clearly separated.

For every work item, require an explicit final outcome. **Completed** requires work-performed text and all required checks answered/justified N/A. **Partial** and **Not performed** require a reason and leave their obligation outstanding. A findings-only inspection can be Completed while creating corrective work. A visit with no work actually performed can be finalized with honest outcomes; cancelling a mere booking remains the simpler alternative when no field observations need retaining.

A completed recurring item has a second choice: **Fulfill this current obligation** or **Record work only**. Live current-cycle work proposes Fulfill after the technician selects Completed; historical entries default Record only. Every affected row must be reviewed. Paused/ended/inactive or stale incompatible obligations cannot be silently advanced.

| ID | Control | Result / failure |
|---|---|---|
| S20-A01 | Outcome row | Set/confirm completed, partial, not performed; unreviewed rows block Finalize. Open S19 to fill missing work. |
| S20-A02 | Fulfill / record only | Eligible completed plan item only. Show which current obligation will be closed. A conflict explains why Record only is necessary or offers review of the current plan. |
| S20-A03 | Proposed next date / override | Default actual date + confirmed current interval. Manual override must be later than the actual service date and requires reason. May still be overdue relative to today for backdated work. |
| S20-A04 | Review changed plan | When interval/due/state changed since selection, show old/live values; explicit re-review uses current terms for future scheduling, without altering captured inspection questions. |
| S20-A05 | Finding follow-up decision | For each unresolved issue: create staged corrective task, link an existing task, or **No follow-up tracked** with explanation. No automatic diagnosis/repair claim. |
| S20-A06 | Confirm staged task changes | Review new task titles/dates/context and any resolution of existing corrective tasks. Incomplete work cannot automatically resolve them. |
| S20-A07 | Report photographs | Check individual saved photos; none selected initially. Optional captions; display selected count. Internal photo attachments remain stored even when omitted. |
| S20-A08 | Report details | Review captured customer/site/equipment/issuer details. Correct a historical spelling/address here without changing live master records. Explicit **Use current details** shows differences before copying; never automatic. |
| S20-A09 | Business details needed | S31 if issuer/technician fields missing, then explicitly accept those values into this visit snapshot. Fieldwork stays saved. |
| S20-A10 | Preview customer report | S29 draft preview with DRAFT mark; no Save/share actions. |
| S20-A11 | Finalize visit | Require all validation and per-plan review, saved attachments, and no pending saves. Commit record revision + eligible obligation changes + task changes + finalized visit state together. Disable repeated presses. Then S27; PDF generation is a separate step. |
| S20-A12 | Back to working visit | S18; retain reviewed draft choices, but invalidate affected review confirmations if source inputs change. |

**Crucial failure boundary:** failure before the finalization commit leaves the visit in progress and all obligations unchanged. Failure producing the PDF **after** a successful commit leaves the visit finalized with “Report not ready — Retry.” Retrying the PDF does not complete anything again.

If a linked follow-up was resolved, cancelled, or edited after the draft was prepared, show its live state and require re-review of the staged task action; finalization must not silently overwrite that newer state.

Multiple completed plans on the same machine advance independently. A partial plan on that same machine remains due. The report states every selected work item's actual outcome; it does not conceal the uncompleted rows.

## S21 — Follow-up detail

**Purpose/entry/Back:** Work, customer/equipment, original visit, or notification. Shows immutable type, title, due date, status, customer/site/equipment links, optional originating finding/record, internal notes, dated updates, and linked planned/working visit. Corrective reports may include a separately captured public recommendation, not these internal notes.

| ID | Control | Availability / result |
|---|---|---|
| S21-A01 | Edit / reschedule | Open → S22. Due-date change affects this task only. |
| S21-A02 | Contact / record contact | Active context → S14/S15. Applies to both task types when contact is necessary. |
| S21-A03 | Book corrective visit / start corrective work | Open corrective task → S16 with linked one-off item; if already booked, Open visit is offered instead. Booking does not resolve the task. |
| S21-A04 | Resolve follow-up | Open → explicit resolution date, not future, and required note. Contact task can resolve without service. Corrective task can be resolved manually, labelled “Resolved manually — no service record linked,” or by a completed visit. |
| S21-A05 | Cancel follow-up | Open → U03 required reason; not shown as completed work. |
| S21-A06 | Reopen | Resolved/cancelled + active context → reason and new required due date. Preserve previous resolution/cancellation; no historical report changes. |
| S21-A07 | Customer/site/equipment/origin/visit links | Corresponding detail or S27; historical origin remains identifiable. |
| S21-A08 | Back | Return; no status change. |

Resolving/cancelling a corrective task linked to a booked visit warns that the booking remains; review it separately. No permanent delete for saved follow-ups; use Cancel. Archived-context tasks stay readable and cannot reopen until the context is active.

## S22 — Follow-up editor, including staged mode

**Purpose/entry/Back:** S03/S05/S10/S21 or S18/S19/S20. U01 explicit-save. Staged mode is prominently labelled **“Will become active when this visit is finalized.”**

**Inputs:** type required when creating, contact/corrective; fixed after first Save. Title required. Due date required; may already be overdue, with a warning. Customer required. Contact follow-up: site/equipment optional and coherently scoped. Corrective follow-up: equipment required; site/customer come from equipment unless historical context is explicitly displayed. Internal note optional. Optional **Customer-visible recommended action** only for corrective tasks staged from a visit; this is report content, unlike internal task notes. Originating finding/visit is a read-only link.

| ID | Control | Result / failure |
|---|---|---|
| S22-A01 | Type selector | New only; switching to corrective requires equipment and clears incompatible context after explanation. |
| S22-A02 | Context selectors | U02 compatible active records; caller may lock origin context. A saved task can change equipment only before any visit association and with explicit reason; otherwise cancel/create the correct task. |
| S22-A03 | Save follow-up / Save planned follow-up | Normal mode creates/updates live task. Staged mode updates visit draft only. No due date or required title → remain on form with errors. |
| S22-A04 | Use existing open task | When matching equipment has a likely duplicate, choose it instead of creating a new task. A staged link does not overwrite existing live details until explicitly reviewed. |
| S22-A05 | Remove planned follow-up | Staged only; confirm and remove from draft. Existing live tasks use Cancel in S21. |
| S22-A06 | Cancel / Back | U01; no partial live task. |

For a new corrective task derived from a finding, title is suggested from the observation but must be reviewed. Due date is never invented from the machine's service interval. An unfinished service obligation is not automatically duplicated as a corrective task; only genuinely separate corrective work warrants both.

## S23 — Inspection-template library

**Purpose/entry/Back:** Settings or a template chooser. Shows template name, item count, last changed date, active/archived status, and number of plans using it. Search name; filter Active (default), Archived, All; sort name (default), recently changed. U08 applies.

| ID | Control | Result |
|---|---|---|
| S23-A01 | Create template | S24 new. |
| S23-A02 | Template row | S24 view. In chooser mode, separate Use template returns active selection. |
| S23-A03 | Search / filter / sort / clear | U08. |
| S23-A04 | Back | Caller; cancelling a chooser changes no assigned template. |

Empty state offers Create template and explains that plans/one-offs can also operate without one. There is no downloaded specialist/template marketplace.

## S24 — Template view/editor

**Purpose/entry/Back:** library, plan link, or chooser. View shows name, optional internal description, ordered item labels/type/unit/required flag, revision date, and usage links. Editing uses U01. At least one item is required to save an active template.

**Inputs:** template name required; optional internal description. Item content is handled in S25. Duplicate names warn but do not imply identical templates.

| ID | Control | Availability / result |
|---|---|---|
| S24-A01 | Edit | Active → explicit-save editor. Used templates remain editable; saving creates a new template revision for future captures. |
| S24-A02 | Add item / item row | S25 new/edit within this template form. Child Save stages the item in the parent template form; this is not an independent live record. |
| S24-A03 | Move item up / down | Reorder staged items; disabled at boundaries. Drag may supplement these buttons but is not the only method. |
| S24-A04 | Remove item | Confirm removal from the template being edited; existing working/finalized copies unaffected. |
| S24-A05 | Save template | Commit the complete ordered template revision. Existing captured inspections remain unchanged. |
| S24-A06 | Duplicate | Create an unsaved editable copy named “[name] copy”; Save creates a new template identity. Available from archived view too. |
| S24-A07 | Archive | U03 shows linked-plan count. Existing plans retain the reference, but new visits cannot silently capture an archived template; require choosing an active replacement or explicitly starting without a checklist, with a recorded reason shown in the report. Existing working copies stay intact. |
| S24-A08 | Restore template | Archived → active; does not alter plans' current working copies. |
| S24-A09 | Delete unused template | Only no plan references or captured working/finalized inspections. Otherwise Archive. |
| S24-A10 | Used by plans | U02 navigation-only list with exact plan/equipment labels; selecting a row opens S12, including inactive referenced plans. No new management subsystem. |
| S24-A11 | Use template | Chooser caller only, active saved template → return selection. |
| S24-A12 | Cancel edits / Back | U01; a viewed template simply returns. |

The template description is internal guidance, not automatically a report preamble. Archival does not silently remove a checklist from a booked service; its Start review must expose that decision.

## S25 — Template-item editor

**Purpose/entry/Back:** S24 item/new action. Explicit-save mini-form; Save stages changes in the parent template; the template's own Save publishes them.

**Inputs:** question/label required, 500 characters; type required: status result, short text, numeric reading; defaults to status result. **Required to fulfill this service** defaults on. Numeric unit required, maximum 40 characters; technician may enter a dimensionless label such as “count.” Optional short instruction, internal while performing work; report contains the question/result/unit, not internal guidance. No preset answers, formulas, limits, conditional visibility, scoring, or pass/fail calculations.

| ID | Control | Result |
|---|---|---|
| S25-A01 | Type selector | Adjust relevant fields; changing away from numeric removes the staged unit only after confirmation if populated. |
| S25-A02 | Required switch | Controls whether unanswered/Not checked blocks fulfillment; does not prevent recording incomplete work. |
| S25-A03 | Save item | Validate and return staged item to S24; does not mutate captured inspections. |
| S25-A04 | Cancel / Back | U01; parent form remains. |

Duplicate labels warn because two indistinguishable report rows are confusing, but may be intentionally saved with explicit confirmation.

## S26 — History and record attention

**Purpose/entry/Back:** Work History or scoped customer/site/equipment/plan links. Read-oriented chronological list. Shows service visits/revisions, cancellations, manual contact entries, plan changes, equipment moves, follow-up state changes, and optionally saved correction drafts. Event dates distinguish actual occurrence from entry/correction time.

**Filters:** record type All/Service/Contact/Plan changes/Moves/Follow-up changes/Cancelled visits; date range All/custom; current versus superseded/voided service revisions; customer/site/equipment; **Report needs attention**; **Correction drafts**. Default newest actual event first, with entry time shown; optional sort newest recorded first. Search names, references, and service/follow-up titles, not arbitrary PDF contents. U08 applies.

| ID | Control | Result |
|---|---|---|
| S26-A01 | Service row | S27 appropriate revision; default current, marked if superseded/voided. |
| S26-A02 | Contact row | S15 view. |
| S26-A03 | Plan/move/follow-up/cancellation event | Expand event with old/new values, recorded reason, actor “local technician,” and links to S12/S10/S21/S17. No editable event fields. |
| S26-A04 | Correction-draft row | S28 resume. |
| S26-A05 | Enter past service | S16 historical mode, inheriting unambiguous context. |
| S26-A06 | Search / filters / sort / clear | U08. |
| S26-A07 | Back | Caller. |

Customer/site histories filter by the visit/event's historical relationship, not a machine's present owner. Equipment history can show its earlier customers internally; creating a new report never automatically attaches those older customer reports.

## S27 — Finalized service record

**Purpose/entry/Back:** finalization success, historical visit, or current visit status. Read-only view of one revision. Shows reference/revision/unique revision token, service date, entry/finalization time, historical issuer/customer/site/equipment identity, every work/checklist result, findings, parts, selected and unselected photos, internal notes in a clearly private section, and exact scheduling/task effects applied at the time.

Current linked plan/follow-up states appear in a separate **Now** section; they are not represented as the report's historical content. A superseded/voided banner links the current revision. No Edit button directly changes this page.

| ID | Control | Result |
|---|---|---|
| S27-A01 | Preview report | S29 for this revision; prepare PDF if none exists. Failure affects report status, not recorded work. |
| S27-A02 | Report versions | S29 version chooser; identifies the service-record revision behind each file. |
| S27-A03 | Correct record | Latest non-void revision → S28; existing correction draft resumes rather than creating a second concurrent draft. |
| S27-A04 | Void mistaken record | Latest revision → S28 void mode, required reason and schedule-impact review. |
| S27-A05 | Revision selector / latest revision | Select previous/current revision, always visibly labelled. |
| S27-A06 | Plan / follow-up / equipment / customer/site links | Current contextual detail without changing this historical snapshot. |
| S27-A07 | Photo | U04 read-only, with Selected for report or Internal attachment label. |
| S27-A08 | New visit at this site | Active context → S16 fresh selection; does not copy old answers or imply repeat completion. |
| S27-A09 | Back | Caller. |

There is no delete-finalized-record action. An entirely unwanted local dataset can be erased through S33, but that does not recall exported files. Internal records are not claimed to be tamper-proof, independently timestamped, or compliance-grade audit evidence.

## S28 — Correction / void review

**Purpose/entry/Back:** S27; saved draft also reachable from Home/History. Durable-working draft under U01. Original revision remains current until an explicit correction commit.

**Inputs:** correction reason required; corrected actual service date not future; corrected work outcomes/answers/findings/work/parts/notes; historical displayed names/addresses/identifiers; report-photo selection and new photos. Original question labels, item types, and units remain the captured inspection definition; a mistaken definition can be explained in an addendum, not silently swapped for today's template. Customer/site/equipment/plan **identities** cannot be relinked by typing a different name. Wrong-record identity requires void + correctly linked new entry.

Changes are reviewed as old → new. Voiding requires only reason and scheduling/task impact review; it creates a void revision, not a physical deletion.

| ID | Control | Result / failure |
|---|---|---|
| S28-A01 | Edit permitted record content | Draft only. Uses S19 controls for answer/work/part/photo editing; original revision is unchanged. |
| S28-A02 | Use current identification details | Optional explicit diff/accept; never automatically imports a changed customer address into historical content. |
| S28-A03 | Review schedule effects | Per plan: default **Leave current schedule unchanged**. A guarded update is offered only under R05 when this record still owns the latest schedule effect and no intervening mutation exists. Show exact rollback/replacement dates. |
| S28-A04 | Review follow-up effects | Existing live tasks are not automatically deleted/reopened because history was edited. Show links for deliberate task review. New corrective tasks, if explicitly staged, are committed with the correction only when the equipment still has the original active customer/site context. Otherwise, correct the history and create any current-context task separately; do not carry a former customer's private context forward automatically. |
| S28-A05 | Preview revised report | S29 draft mode; old PDFs remain untouched. |
| S28-A06 | Save correction / confirm void | Validate reason, saved draft, and schedule guard. Commit a new record revision and only the explicitly approved current-state effects together. Mark prior revision superseded; then S27. PDF generation remains separate. |
| S28-A07 | Leave draft | Preserve draft; previous finalized revision is still current. |
| S28-A08 | Discard correction draft | Confirm; delete draft-only changes/attachments, preserve original record and schedule. |
| S28-A09 | Open plan/follow-up for separate adjustment | S12/S21; returning invalidates a stale effect review. |

A void does not promise to “undo everything” after later work has occurred. When a safe rollback is unavailable, keep current dates, mark the historical record void, and explicitly flag the unresolved scheduling review. Voided reports are not shared as current service reports. Prior original files remain viewable with warnings; a new void notice identifies the original reference/reason and does not pretend earlier copies have disappeared.

## S29 — Customer report and PDF versions

**Purpose/entry/Back:** S20 draft preview, S27 finalized record, S28 revised preview, or report-linked contact entry. Built-in viewing is part of CORE; installation of another PDF reader is not required. Native PDF generation and rendering have supported Android routes. [P11–P12]

### Fixed report content, in order

1. Business logo when supplied, business name, technician, optional public address/phone/email; report reference, unique record revision token, PDF version, service date, and generation time.
2. Historical customer and service-site name/address. Customer contact fields are omitted from the report by default; only the reviewed identity block is used.
3. Optional customer-visible visit summary.
4. One equipment section per visited machine: name, make/model, serial/internal/app reference; one subsection per selected service, showing actual outcome, performed checks including Issue/Not applicable/Not checked, findings, work, and parts quantities. Missing identifiers are not invented.
5. Customer-visible recommended corrective actions captured at finalization, including “No follow-up tracked” explanations where an unresolved reported issue has no tracked action. Internal contact tasks are never printed. A follow-up due date is labelled a suggested action date, not a booked appointment or guarantee.
6. Next service date **per fulfilled plan**, clearly distinguished from an unfulfilled obligation's still-outstanding date. Record-only work says it did not change the recurring schedule. Historical next-date effects remain historical, not a current live schedule lookup.
7. Only explicitly selected photographs, grouped with their equipment/service and captions; automatic page breaks, repeating context headers, and page numbers. Long content continues onto subsequent pages rather than truncating.
8. Fixed footer identifying this as a technician's service record, not a regulatory certificate. Corrections/void notices identify the superseded reference and reason. No customer signature, invoice, tax claim, or payment request.

**Always excluded:** site access notes, internal customer/equipment/visit/item/task notes, contact history, unselected photos, hidden template instructions, and unrelated earlier customer records. No “include all notes” switch exists.

| ID | Control | Availability / result / failure |
|---|---|---|
| S29-A01 | Preview pages / page selector | Built-in view of draft/current selected file. Page N of M; fit/zoom and accessible previous/next controls. |
| S29-A02 | Read report text | Structured accessible text reflecting the same frozen public content, not internal notes. Supports screen readers even where raster page preview is inadequate. |
| S29-A03 | Edit before finalization | Draft → S20/S28. Draft has DRAFT marking; no external Save/share. |
| S29-A04 | Create report / retry generation / generate replacement version | Finalized record only. Generate from its frozen snapshot, store successful file and version metadata. Replacing an unavailable file creates a new identifiable version and retains the old file-unavailable entry. Failure retains record and prior files. Retry never advances plans. |
| S29-A05 | Save PDF copy | Ready final PDF → U05; local source remains. “Copy saved” means file write succeeded, not customer delivery. |
| S29-A06 | Share PDF | Ready final non-void current report → U05. Superseded versions require explicit warning acknowledgement; a void notice is shareable as a notice, not a valid completed-service report. |
| S29-A07 | Open in another app | U05 optional handoff; unavailable external viewer does not block in-app reading. |
| S29-A08 | Version selector | Choose fixed record revision/PDF version; show generation date, token, current/superseded/void status. |
| S29-A09 | Record manual report handoff | S15 with this report version linked; outcome entered by technician, never inferred from Sharesheet. |
| S29-A10 | Back | Caller; no record changes. |

A generated PDF is preserved as bytes, not recreated whenever viewed. A later logo/address/template change cannot rewrite it. Existing external files cannot be recalled. A lost/corrupt stored PDF is labelled unavailable; offer backup recovery and reading the structured record. A newly generated replacement is a **new, separately identified PDF version**, never silently substituted as the original. The original file's unavailability remains recorded, and a complete historical backup cannot claim to contain missing original bytes.

## S30 — Settings index

**Purpose/entry/Back:** Home Settings; Back returns to Home/caller. This is a fixed index, not a customization dashboard.

| ID | Row / visible summary | Destination |
|---|---|---|
| S30-A01 | Business and technician — ready/incomplete | S31. |
| S30-A02 | Inspection templates — active count | S23. |
| S30-A03 | Reminders — off/on/blocked by Android | S32. |
| S30-A04 | Data and recovery — last verified backup/date or never | S33. |
| S30-A05 | Help and privacy | S38. |
| S30-A06 | Back | Caller. |

No Sync, Team, Billing, Integrations, invoice numbering, dashboard widgets, custom fields, or appearance/theme editor exists in CORE. The app follows system light/dark appearance and accessibility settings; it does not add redundant font-size sliders.

## S31 — Business and technician profile

**Purpose/entry/Back:** Settings, optional first use, or completion's missing-issuer link. U01 explicit-save.

**Inputs:** business/display name required to save a ready profile; technician name required, allowed to be the same name; optional public business address, phone, email, and logo. No tax-registration field, bank information, or customer account. **Business timezone** required, initially the device's current zone, displayed by name and selectable from known timezone names. This is not a chosen launch country.

| ID | Control | Result / failure |
|---|---|---|
| S31-A01 | Add / replace / remove logo | U04; no report changes until used in a future capture/correction. |
| S31-A02 | Business timezone | Searchable timezone chooser; explains date/notification consequences before Save. Existing timed bookings retain their original instant and timezone; display an equivalent business-zone time when different. Date-only service obligations remain dates. |
| S31-A03 | Save profile | Validate and save; return to caller. Existing visits/reports keep captured issuer details. An incomplete profile can be skipped at first use, but finalizing a visit requires an identified issuer/technician. |
| S31-A04 | Cancel / Back | U01; first-use caller may continue without profile. |

No telephone/email reachability verification is performed. Blank optional public fields are omitted from reports. No external contact/account is created.

## S32 — Reminders and Android notification status

**Purpose/entry/Back:** Settings or a notifications-unavailable banner. Saved settings are immediate, with “Saved” feedback; Back does not undo a switch. Enabling reminders is contextual consent to request the relevant Android notification permission, not a request for exact-alarm access. Android permission/channel choices can block notifications independently. [P01, P17]

**Visible:** desired app state and actual availability separately; business timezone; plain-language timing limitation; status of the two named Android channels **Work summary** and **Appointment heads-up**. Their sound/vibration controls belong to Android settings. Default master reminders **off** until chosen by the technician. Daily summary is preconfigured but inactive while master is off.

| ID | Control / default | Behavior |
|---|---|---|
| S32-A01 | Reminders on/off — off | On shows rationale, then requests Android notification permission where needed. Denial preserves the preferred setting but displays **On in app — blocked by Android**; no repeated nagging. Off cancels pending reminder requests and visible app reminder notifications, not business work. |
| S32-A02 | Daily work summary — on | Includes active overdue/due-soon obligations, open follow-ups due through today, booked visits today/tomorrow, unfinished visits, and stale-backup attention. One summary, not one notification per machine. |
| S32-A03 | Summary time — around 08:00 | User-selected local business time. Label “Approximate; Android may delay this.” Changing it affects future summaries only. |
| S32-A04 | Due-soon window — 30 days | Choices 0, 7, 14, 30, 60, 90 days. Controls Home's due-soon range and summary eligibility, never stored plan dates. Zero means due today only. |
| S32-A05 | Appointment heads-up — off | Optional, only timed booked visits. Inexact one-off notification before appointment; no precise arrival promise. |
| S32-A06 | Appointment lead — 1 hour | Choices 1 hour, 2 hours, 1 day. Visible when heads-up enabled. Date-only visits remain daily-summary items. |
| S32-A07 | Show customer/site names in notifications — off | Off uses generic text/counts. On allows customer/site and appointment time, never access notes or findings. System lock-screen privacy may further hide content. |
| S32-A08 | Open Android notification settings | App or affected channel settings. On return, refresh actual availability; do not override user's channel sound/vibration choices. |
| S32-A09 | Test notification | Only when available; post generic test, label **Test requested**, not “You heard it.” No due/visit changes. |
| S32-A10 | How reminders work | S38 reminder topic. |
| S32-A11 | Back | Caller; already-saved preferences remain. |
| S32-A12 | Tap summary notification | Open S02; its counts are recomputed rather than trusted from a stale notification. |
| S32-A13 | Tap appointment notification | Open current S17, or its cancelled/finalized state if changed. |
| S32-A14 | Dismiss notification | Hide that notification only. No service/task/booking changes. Remaining work may reappear in the next day's summary. |

**No in-app service/appointment snooze is offered in CORE.** Reschedule a task/appointment in its actual editor, or switch reminder preferences. Android's own notification snooze, where available, is outside the business workflow and never revises a due date.

Daily summaries use persistent, deferrable work. Appointment heads-ups use inexact scheduling with validation of the current booking. Late processing after an appointment's start suppresses that obsolete heads-up; the past booking remains visible in Work. No catch-up storm, exact-alarm permission, persistent foreground service, battery-optimization exemption demand, or “works after force-stop” claim. [P02–P04]

## S33 — Data and recovery

**Purpose/entry/Back:** Settings/Home backup attention; limited recovery entry when records cannot load. Shows dataset/business identity, record counts, estimated space for records/photos/reports, available device storage where obtainable, last completed backup snapshot time, last **verified destination copy**, and last restore/import information.

“Saved on this device,” “Backup written,” and “Backup verified by reading it back” are different labels. A known filename/destination is not proof that the user has retained the file or that a cloud provider uploaded it remotely.

| ID | Control | Result |
|---|---|---|
| S33-A01 | Create full backup | S34. |
| S33-A02 | Inspect / restore backup | S35. |
| S33-A03 | Export CSV records | S36; explicitly not restorable and includes internal information. |
| S33-A04 | Import initial CSV | S37. Disabled with explanation unless business records/history are empty; profile/templates may already exist. |
| S33-A05 | Backup reminder — every 7 days | Choices off, 1 day, 7 days, 30 days; saved immediately. With any business data and no verified backup, reminder is due immediately. Uses in-app attention and optionally the daily summary, not automatic uploads. |
| S33-A06 | Verify an existing backup | S35 inspect-only; choose/read/validate file without replacing data. A backup of another dataset does not satisfy this dataset's current backup reminder. |
| S33-A07 | Delete all local ServiceLoop data | Strong confirmation lists all data categories and last verified backup; Back up first → S34, Cancel, or type **ERASE** and confirm. Creates a new empty dataset only on success. No exported/backed-up/shared copies are deleted. |
| S33-A08 | Remind me about backup in 7 days | Postpone reminder only; retain truthful old last-backup status. No service dates change. |
| S33-A09 | Storage/recovery help | S38. |
| S33-A10 | Back | Caller. |

There is no “clear all attachments” shortcut: those files may be professional history. Unreferenced intake/temporary preview files can be cleaned automatically, but retained record attachments/PDF versions are not expendable cache. In a corrupt-dataset recovery state, only operations that can safely read available data are offered; destructive reset remains explicit, never the default repair.

## S34 — Create a complete backup

**Purpose/entry/Back:** S33 or pre-restore safety backup. User-initiated operation page; Back asks whether to cancel a running preparation/write. No background completion promise.

**Visible:** included categories and counts; snapshot date/time; expected size and temporary-space requirement; readable destination filename such as `ServiceLoop_[dataset]_[timestamp].slbackup`; warning **“Contains customer data, internal notes, photos, and reports. Not password-encrypted by ServiceLoop.”**

**Contents:** customer/site/equipment records including inactive ones and location history; plans/current obligations/change history; bookings/cancellations; saved fieldwork and correction drafts; templates and captured inspections; all finalized revisions and generated PDF bytes; all stored referenced photos/logo versions; manual contacts; follow-ups; business profile; reminder preferences; data-operation metadata; a compatibility manifest, file inventory, and integrity checks. Device permissions, transient unsaved ordinary forms, granted cloud accounts, and provider access rights are not restorable data.

| ID | Control | Result / failure |
|---|---|---|
| S34-A01 | Prepare backup | Finish pending saves, obtain a consistent snapshot, and prepare/validate the package in app storage. Briefly block edits while taking the snapshot, not for the whole external write. Later edits are explicitly newer than that backup. |
| S34-A02 | Choose destination and write | U05 system picker. Choose a location outside the app's private files; recommend a genuinely off-device copy without asserting that one exists. |
| S34-A03 | Verify written copy | Reopen the selected output and check the complete package. Success records snapshot time and verification time/destination. If read-back is unavailable, label Written, not verified; do not replace the last verified-backup marker. |
| S34-A04 | Retry / change destination | Retain prepared snapshot while available; a rebuilt snapshot has a new capture time. No live records are changed. |
| S34-A05 | Cancel / Back | Stop safely; keep existing dataset and older backup information. Partial external file cleanup is best effort with a visible warning when incomplete files remain. |
| S34-A06 | Done | Return S33 or the pre-restore caller. |

Insufficient free space, unreadable attachments, package validation failure, permission loss, missing provider, and interrupted writes have distinct messages. Missing historically referenced files prevent claiming a **complete** backup; list them and offer readable export/known-good-backup recovery. A record whose PDF was never successfully generated can be backed up with its honest pending-report state; that is not the same as losing a previously generated file.

A prepared package left on this same device is not an off-device backup. OS automatic backup is not the recovery contract for this photo-bearing dataset. CORE deliberately does not rely on it, and business files should be excluded from ordinary partial automatic backups where supported; OEM/device-transfer behavior still requires testing. [P13]

## S35 — Inspect and restore a backup

**Purpose/entry/Back:** first use or S33; also Verify existing backup. Opens only ServiceLoop backup packages through the system document picker. Inspection never changes the current dataset.

**Preview:** package format/version, source business/dataset, capture time, record/attachment/PDF counts, file integrity, required space, and compatibility result. Show current-versus-backup counts and **“Everything saved after this backup is absent from the restored dataset.”** Different-business backup gets an additional warning. Reminder permissions cannot be restored from a file.

| ID | Control | Result / failure |
|---|---|---|
| S35-A01 | Choose backup | System picker; cancel leaves current data intact. Encrypted/unknown/corrupt/incomplete files are rejected, not partially imported. |
| S35-A02 | Inspect / verify | Stage locally and validate full contents, references, file lengths/checks, and compatibility. Unknown/newer format: explain need for a compatible app version; do not guess a conversion. Supported older formats are upgraded only in staging. |
| S35-A03 | Save a safety backup of current data | S34; recommend before replacement. User may explicitly proceed without one after acknowledging the loss risk. |
| S35-A04 | Replace this device's dataset | Final confirmation requires checking “Replace, not merge” and typing **REPLACE** while the source business/dataset is displayed. Fully validate/stage before an atomic switch. Do not delete the live dataset before staging succeeds. |
| S35-A05 | Cancel / Back | Before commit, discard staging, preserve live data. During the brief final switch, Cancel is disabled with a clear status. |
| S35-A06 | Verify only / Done | Inspection-only caller returns without restoring; an older verified snapshot remains labelled with its actual age. |
| S35-A07 | Review restored data | Successful restore → S02 with restored date banner; saved unfinished visits reappear. |
| S35-A08 | Review reminder settings | After restore → S32; restored preferences shown, device reminders held off until explicit review, then current system permission checked. |

Validation also rejects unsafe archive paths, unexpected executable payloads, and excessive expanded size before installation of the snapshot. Integrity checks detect corruption, not who created a backup; the chooser explains that only trusted ServiceLoop backups should be restored.

**Replacement policy:** no merging customers, no choosing a few visits to restore, no simultaneous-device reconciliation. Existing exported files elsewhere remain untouched. Current data is recoverable afterward only from a separately retained pre-restore backup; there is no indefinite hidden Undo archive.

Interrupted validation leaves the original dataset. An interruption at commit recovers either the complete pre-restore or complete restored dataset, never an exposed mixture; if that cannot be established, enter U07 rather than pretend restore succeeded. Stale notifications are cancelled, restored identifiers are honored, and current due states are recalculated without auto-fulfilling or advancing anything. A restored older backup may coexist with newer externally issued reports; the restore banner and unique revision identifiers make that limitation visible. The technician is instructed to continue editing on this device, not both devices. Backup freshness uses the snapshot capture time: verifying an ancient file today does not make its contents current. Show the newest verified snapshot by capture time separately from the last verification event; after restore, identify the inspected source snapshot explicitly.

## S36 — Portable CSV export

**Purpose/entry/Back:** S33. One fixed export: **All durable business records, including archived records and internal notes**. No misleading “customer report” label. No partial-export configuration builder.

**Visible:** category/row counts, snapshot time, estimated size; warning **“This is not a backup. Images and PDF files are not included.”** A ZIP packages readable UTF-8 CSV files plus a field guide. Each file includes stable IDs and human-readable context, rather than making the recipient interpret only opaque relationships.

**Files:** business profile; customers; sites; equipment/location history; plans/obligation events; visits and work outcomes; inspection definitions/answers; findings/parts; follow-ups/state changes; contact entries; service-record revisions; attachment/report manifests. Working drafts are explicitly marked as drafts. Exported manifests identify file names and versions but do not supply those bytes. Dates use ISO notation with timezone information where applicable. Spreadsheet-formula-like text is exported as literal text with documented escaping; backup retains the exact stored data.

| ID | Control | Result / failure |
|---|---|---|
| S36-A01 | Prepare export | Create a consistent read-only snapshot; no live-record changes. |
| S36-A02 | Save export | U05 selected destination. |
| S36-A03 | Share export | U05 with private-data warning and explicit confirmation; not automatically directed to any customer. |
| S36-A04 | Export contents / field guide | Show the fixed file/field description inside the app, not an external-web dependency. |
| S36-A05 | Retry / cancel / Back | U05; failed output never updates full-backup status. |

This export is not accepted by the initial importer as a full dataset. The importer accepts only its fixed inventory template; restore accepts only complete backup packages.

## S37 — Initial customer/equipment CSV import

**Purpose/entry/Back:** first use or S33, **only when customers, sites, equipment, plans, visits, contacts, follow-ups, and business history are all absent**. Existing profile/templates are allowed and preserved. This is a one-time bootstrap workflow, not a master-data synchronization system.

### Fixed template and validation

One UTF-8 CSV, optional BOM, comma-separated, quoted values allowed. All named headers appear exactly once; column order may vary. Unknown/missing/duplicated headers are errors. Proposed bounded intake: **10,000 data rows and 25 MiB** per import. Exceeding the limit is reported before any live changes; no silent truncation.

| Column group | Exact headers and rules |
|---|---|
| Customer | `customer_key`, `customer_name`, `contact_name`, `phone`, `email`, `customer_notes`. Key/name required on every row. Notes are internal. |
| Site | `site_key`, `site_name`, `site_address`, `site_contact_name`, `site_phone`, `site_email`, `site_access_notes`. Key/name required on every row; address optional; access notes internal. If all site contact fields are blank, use customer-contact inheritance. Otherwise use the explicit site override. |
| Equipment | `equipment_key`, `equipment_name`, `equipment_type`, `make`, `model`, `serial_number`, `internal_id`, `equipment_notes`. Key/name required together when equipment is supplied; all equipment fields may be blank to import a site/customer without machines. |

Source keys are required explicit grouping identifiers, trimmed and case-sensitive, 1–80 characters using letters, digits, hyphen, underscore, or period. A site key belongs to exactly one customer. Equipment keys are unique across the file. Repeated customer/site keys are expected for multiple machines but must have consistent repeated details; conflicting values are errors, not “last row wins.” The app assigns its own permanent record identities and retains the import's source-key mapping in the import result/backup.

Phone numbers, serials, and identifiers are text, preserving leading zeroes. Field limits match U01. Invalid email syntax, absent required fields, conflicting parents, duplicate equipment keys, or duplicate nonblank internal IDs are blocking errors. Apparent duplicates under different keys—matching customer contact/name or normalized machine serial—are review warnings. No fuzzy merging and no automatic choice of a winner. Repeated site-only rows with identical keys may be coalesced as the same declared site, not duplicate equipment.

Plans, due dates, past services, photos, PDFs, and templates are **not imported** by this CSV. The success screen explicitly states that equipment needs service-plan setup before it will appear as due work.

| ID | Control | Result / failure |
|---|---|---|
| S37-A01 | Get blank template / example | Save the fixed CSV and a short field guide/example through U05. Sample rows are plainly fictional and not automatically imported. |
| S37-A02 | Choose CSV | System document picker. Cancel leaves empty dataset unchanged. |
| S37-A03 | Validate and preview | Parse without creating records. Show row numbers, prospective unique customer/site/equipment counts, field errors, warning groups, and a browsable sample. |
| S37-A04 | View row / duplicate group | Show original values and prospective relationship; no inline spreadsheet editor. User corrects the file externally and reselects it. |
| S37-A05 | Confirm these are separate records | Per possible-duplicate group acknowledgement. Does not override hard key/internal-ID conflicts. |
| S37-A06 | Save validation results | U05 CSV of row/error/warning details; contains private imported values and is labelled accordingly. |
| S37-A07 | Choose corrected file / revalidate | Discard only staged parsing and acknowledgements; live dataset still unchanged. |
| S37-A08 | Import all valid data | Enabled only with zero blocking errors and acknowledged warnings. Recheck dataset is still empty, then commit the whole validated set in one transaction. No skip-invalid-row mode. |
| S37-A09 | View imported equipment / customers | Success → S09 “No active plan” filter / S04. Show counts and original source-key-to-app-reference result. |
| S37-A10 | Save import result | U05 mapping/count report for later reconciliation. |
| S37-A11 | Cancel / Back | Discard staging only. After successful commit, Back does not undo import; reimport is disabled. |

Interruption before commit leaves no imported business records. Interruption after commit shows the existing success result and does not import twice. Storage/validation failure keeps the empty dataset and original file unchanged. To start again after a successful mistaken import, explicitly erase the dataset or restore a pre-import backup; there is no disguised bulk merge or automatic duplicate cleanup.

## S38 — Help, privacy, and app information

**Purpose/entry/Back:** Settings and contextual help links; essential topics bundled for offline access. Shows installed app version and support/attribution information, without exposing developer tooling to everyday work.

| ID | Control / topic | Content or result |
|---|---|---|
| S38-A01 | First service workflow | Customer → site → equipment → plan → work → report → next obligation. Links to the relevant existing destinations, without creating sample business records. |
| S38-A02 | Due dates, bookings, and partial work | Explain R01–R04 with one example; no “booked means done” language. |
| S38-A03 | Saving and interruptions | Explicit Save versus saved working draft; last-save boundary; U07 recovery. |
| S38-A04 | Photos and report privacy | Optimized copies, selected report photos, internal field exclusions, and inability to retract external files. |
| S38-A05 | Backups, exports, and device replacement | Explain S34–S37; loss after uninstall/clear-data/device loss; unencrypted files; no automatic cloud recovery. |
| S38-A06 | Notifications not appearing | Permission/channel checks, approximate timing, force-stop limitations; links S32. No promise that disabling battery safeguards fixes every device. |
| S38-A07 | Corrections and ownership changes | Fixed history, explicit revisions, guarded dates, current-versus-historical customer context. |
| S38-A08 | Privacy policy | Offline policy text plus optional public web link. Explain local processing, user-selected external sharing/providers, retention/deletion, no mandatory analytics/ads, and support contact. Online page requires a browser/network; bundled text remains readable. |
| S38-A09 | Contact support / copy support details | Optional email composer, no automatic attachments; user reviews message. Copy fallback if no handler. |
| S38-A10 | Copy diagnostic summary | App/OS version, operation error code, approximate available space; no customer names, identifiers, notes, database, or photos. User explicitly chooses what to send. |
| S38-A11 | App version / third-party notices | Read-only installed version and bundled license notices. No self-updater, account, or unverified “latest version” badge. |
| S38-A12 | Back | Caller. |

**Accessibility throughout:** labelled navigation/icons; descriptive photo alternatives using machine/caption; status words rather than color alone; large touch targets; readable contrast; system text scaling without clipped essential actions; scrollable forms; logical screen-reader focus and announced validation/save failures; explicit reorder/zoom alternatives to gestures. PDF pages also have the structured public-text view S29-A02. These are implementation/test requirements, not an accessibility-certification claim. [P16]

# E. Automatic behavior and lifecycle map

| ID | Trigger | Visible outcome and constraint |
|---|---|---|
| E01 | Open app/list, return to foreground, business-zone midnight while active | Recompute overdue/today/soon labels from saved dates and current business date. No background job is required for business truth. Unreadable records produce unavailable/error, not zero. |
| E02 | Successful business mutation | Refresh affected counts, context lists, and reminder eligibility. Edits to names update live lists; finalized identity snapshots/PDFs remain fixed. |
| E03 | Working input changes | Persist small work changes promptly; show Saving/Saved/error. Do not wait until Back/onStop. Ordinary unsaved editor fields remain subject to U01's narrower recovery promise. |
| E04 | Camera/picker return or interruption | Check actual output, copy/optimize, then attach. A pending photo never displays as saved until durable. Missing/unreadable result offers reselect; other work survives. |
| E05 | Process recreation, reboot, interrupted field session | Recover last durably saved in-progress/correction drafts and their attachments. Reopen through Unfinished/Correction attention. Uncommitted input can be lost; never invent a completion timestamp. |
| E06 | Start visit/add work item | Capture current inspection definition, identity details, and plan reference. A later template edit/archive cannot rewrite captured work. Starting with an archived template prompts replacement or explicit no-checklist confirmation with a report-visible reason. |
| E07 | Finalization commit | Persist one revision and its approved plan/task changes together. Repeated taps/retries cannot consume the same obligation twice. Schedule report generation separately; report failure leaves finalized service intact. |
| E08 | Daily summary becomes eligible | Recompute current eligible records. At most one alerting summary per business day; update an existing summary silently as appropriate. Empty work means no “all clear” spam. If delayed, no replay of all missed days. |
| E09 | Appointment heads-up processing | Check booking status/revision and planned time before notifying. Rescheduled/cancelled/started visits invalidate old requests. Suppress heads-up after its appointment starts; preserve the record in Work. |
| E10 | Notification permission/channel change, app foreground return | Refresh actual capability. Denial does not change app records or fabricate sent reminders. Notification updates remain subject to OS scheduling and user settings. |
| E11 | Device clock/timezone change or daylight-saving transition | Calendar service dates remain dates; timed bookings keep stored instants/zones. Reconcile scheduling against business timezone. Do not advance obligations because the clock moved. A summary's nonexistent clock time runs at the next valid time; repeated time alerts once that day. |
| E12 | Device restart/app update | Re-establish supported reminder work after normal lifecycle recovery/unlock, using current preferences and records. No business database in pre-unlock notification storage is required. No guaranteed delivery while force-stopped or before unlock. [P03–P04] |
| E13 | Backup age exceeds configured threshold / first durable business data | Show in-app backup reminder; include in daily summary only if enabled. Deferring this prompt changes no verified-backup timestamp. There is no automatic provider login/upload. |
| E14 | Restore completion, failed staging, or temporary-file cleanup | Successful restore reconciles counts/drafts, suppresses stale reminder identities, and requests reminder review. Failed staging leaves current data. Cleanup removes only proven unreferenced temporary/intake files, never referenced historical attachments or PDF versions. |

**Not automatic:** starting/finishing a visit, reporting a successful call, selecting OK for unchecked items, declaring equipment safe, resolving corrective work, applying a historical completion to the live schedule, including photos in a report, reactivating archived work, sending messages, confirming customer receipt, or synchronizing another device.

# F. Cross-screen rules and critical journeys

## F1. Consolidated business rules

### R01 — Service truth is separate from arrangements

A plan's due date records an obligation. Booking/rescheduling/cancelling a visit, making contact, creating/resolving a contact follow-up, opening a report, sharing a file, and dismissing a notification do not change it. Only S20's approved fulfillment, a guarded correction, or an explicit reasoned due-date edit changes the live schedule. Ending/pausing the plan changes eligibility, not claimed service performance.

### R02 — Valid transitions and independent meanings

| Object | Allowed transition | Effect |
|---|---|---|
| Visit | Booked → in progress | Capture working copies; reserve the same selected obligation, no completion. |
| Visit | In progress → finalized | Preserve reviewed outcomes; advance only specifically fulfilled eligible plans. |
| Visit | Booked → cancelled / working draft discarded → cancelled shell | Release reservations; service remains outstanding. |
| Finalized record | Current → superseded by correction / void revision | Preserve earlier revisions; apply only approved guarded current-state changes. |
| Work item | Unreviewed → completed/partial/not performed before finalization | Completed still requires an explicit fulfill-versus-record-only choice. |
| Inspection | Required items answered, including justified N/A | “Required checks complete,” possibly with issues. Not an automatic machine-condition conclusion. |
| Plan | Active ↔ paused; active/paused → ended | Pause keeps date; resume reviews it; ended has no restart. |
| Follow-up | Open → resolved/cancelled; resolved/cancelled → reopened | Keep dated explanation. Never implicitly change service-plan dates. |

### R03 — Recurrence and initial setup

**Recommended policy:** next date = actual completion date + positive interval, then technician confirmation. Days/weeks use calendar-date addition; months/years clamp to the last valid day of the destination month. This is completion-based, not a fixed annual anniversary.

Examples: 31 January 2027 + 1 month = 28 February 2027; 31 January 2028 + 1 month = 29 February 2028; 29 February 2028 + 1 year = 28 February 2029. If the subsequent service is actually done on 28 February and repeats monthly, the next proposal is 28 March, not 31 March.

Initial next due is explicit. Optional known-last-service information is labelled a historical baseline, not an invented visit/checklist/PDF. A late plan retains a **single outstanding obligation** until performed, deliberately rescheduled, paused, or ended; the app does not fabricate several missed performed services. Interval changes keep the current due date unless explicitly recalculated with review. A manual override needs a reason and does not convert the plan into anniversary mode.

### R04 — Partial work, findings, and follow-ups

Each machine/service row is reviewed independently. A completed inspection that discovers a fault may fulfill its inspection plan while opening corrective work. Maintenance not actually performed remains partial/not performed and its plan remains outstanding. An open corrective task is not evidence of a still-unfulfilled inspection; neither is a fulfilled inspection evidence of a repaired machine.

Every required unchecked item is retained with a reason when finalizing incomplete work. For a wholly Not performed work item, its single item-level explanation explicitly covers all unchecked questions; the technician need not repeat the same explanation on every row. Partial work may likewise use one clearly labelled common reason for its unchecked items, with individual reasons where different. Finalization is allowed with honest partial/not-performed outcomes; required work cannot be marked fulfilled while required checks are unchecked. Every unresolved issue gets a tracked corrective task, a link to an existing task, or an explicit “No follow-up tracked” explanation. This records the technician's decision, not automated safety advice.

### R05 — Backdating, duplicate advancement, and corrections

**Reservation rule:** one saved nonterminal visit can reserve a plan's current obligation. Another visit must use that visit, free the reservation, or record a genuinely separate one-off item with no recurrence effect. Reserving an obligation does not suppress its overdue status.

**Historical-entry rule:** default Record only. Explicit Apply to current obligation is offered only when the plan/context is active, the selected obligation is still current, and the service date is not earlier than the latest applied completion. Equal-date suspected duplicate service requires an explicit review; same already-consumed obligation is never reusable. Incompatible historical work remains recorded without live-date changes.

**Finalization rule:** one visit revision consumes an obligation once. A retry/opening/report generation cannot do so again. The app records the pre/post due date, interval used, actual service date, affected obligation identity, and the revision that applied the change.

**Correction rule:** leaving the current schedule unchanged is the default. A safe replace/rollback is offered only if this record's effect is still the latest relevant plan mutation: no later fulfillment, manual date/interval/state change, or dependent current-cycle reservation/working visit. When that guard holds, correction replaces the original effect or reopens its original obligation; it does not advance from the already-advanced date. A historical record-only entry may be applied once to a currently eligible obligation only through an equally explicit guarded choice.

If there has been intervening work, preserve corrected history but leave today's schedule alone and require separate reasoned review in S12. The same rule applies to voiding. Restoring an old backup does not replay completion events. Unique revision identifiers prevent restored version counters from obscuring which externally issued document is which.

### R06 — Lifecycle and relationship changes

| Operation | Required handling |
|---|---|
| Archive customer/site | Block unfinished visits/correction drafts in scope. Preview affected active plans/bookings/open follow-ups. On confirmation, archive the parent, pause its active plans with a parent-archived reason, cancel its booked visits and open follow-ups with that reason. Child own statuses are retained; they are effectively inactive through the parent. |
| Restore customer/site | Clear that parent's archive state only. Still-archived children remain archived. Paused plans need individual Resume; cancelled bookings/tasks are not silently re-created. |
| Retire equipment | Block working/correction drafts. Pause active plans. Remove its unstarted work from booked visits; keep other machines' selections, cancelling the booking only if it becomes empty. Cancel open equipment-bound follow-ups after explicit preview. Parent/site contact tasks remain. |
| Return equipment to active use | Parent context must be active; plans remain paused for review. |
| Move within customer | Block working/correction drafts and require resolving booked selections first. Move current location; preserve due dates; carry all open equipment-linked contact/corrective tasks with a location-change event. Keep customer/site-only contact follow-ups in their original context. |
| Transfer to another customer | Same blockers; resolve/cancel old equipment-linked open follow-ups first. Pause plans for ownership review. Past visit ownership/contact/report content never moves to the new customer. |
| Change customer on a site | Only if wholly unreferenced. Otherwise create correct site and explicitly move equipment; no relinking historical visits. |
| Pause/end a plan | Pause keeps date and flags an existing booking. End releases unstarted work with preview; in-progress captured work becomes record-only. History remains. |
| Edit/archive a template | New versions affect future captures only. Archived template cannot silently supply or remove a new inspection; Start requires a choice. |
| Delete | Only the explicitly documented unused-record actions; parent-child restrictions apply. Saved visits/contacts/tasks/history are not individually hard-deleted through a generic command. |

Archival is intentionally reversible for master-record availability, **not** a bulk undo of work cancellations. The consequence sheet must say this before confirmation. These rules favor visible operational decisions over hidden cascades.

### R07 — Snapshots, reports, and historical ownership

Inspection content and working identity are captured when fieldwork starts or a new item is added. Finalization freezes the reviewed public and internal content, selections, service effects, and identity snapshot. User-approved corrections create new revisions. Stored PDFs remain fixed files; updates to master contact data, templates, photos, or profile do not regenerate them on access.

Customer/site histories use the historical visit relationships. Equipment history spans its legitimate prior locations internally. A historical entry can explicitly identify earlier equipment/site context without moving the current machine. Incorrect underlying identity is corrected by void + new record, not by merely renaming a snapshot to disguise a different machine.

### R08 — Durable local state and failure boundaries

Explicitly saved records, saved working/correction drafts, successfully copied attachments, and finalized PDFs are durable **on the device**. Unsaved ordinary forms and not-yet-committed keystrokes have no equivalent guarantee. Local durability is not recovery from uninstall, clear-data, device loss, corruption, or failed hardware.

Finalization's business changes are committed together. PDF generation is a separate recoverable operation. Backup/import/restore operate on coherent snapshots/staging. No operation reports success before it has reached its stated persistence boundary. Pending large operations can require the app to remain open; the UI never guarantees completion after force-stop.

### R09 — Import, export, and restore are different operations

Initial CSV creates customers/sites/equipment in an empty business dataset only. Repeated explicit grouping keys are resolved by the fixed template rules; conflicting data blocks the entire import. Apparent duplicates require acknowledgement; no silent merge/skip-last-row behavior.

CSV export is readable data, with internal notes and file manifests but without attachment bytes. A complete backup includes the durable dataset and attachments/PDFs. Restore validates then **replaces**, not merges. Compatibility, missing files, temporary storage, and rollback risks are displayed before replacement. An older restored dataset cannot know about later external copies or work absent from its snapshot.

### R10 — External handoffs and privacy

Opening the dialer/composer/map/viewer/Sharesheet does not prove that a communication, journey, save-to-cloud, or customer receipt occurred. Use honest requested/opened/written/verified/manually-recorded labels. Saved contact assertions are explicit human entries.

Internal fields and unselected photos have no path into a standard customer report. Backup/CSV packages are different: they contain internal information and carry a privacy warning. The app cannot revoke or update files already delivered to another app/provider/person. No broad address-book, call-log, SMS-reading, location, or all-files permission is needed by this baseline.

### R11 — Reminder truth and time handling

The business timezone is explicit and stable, initially from the device. Service obligations/follow-up dates are calendar dates. Timed appointments retain their chosen timezone/instant; moving the device to another zone does not silently shift them. Settings show a changed business-zone equivalent where needed.

Reminders are approximate assistance with one daily summary and optional timed appointment notices. There is no business-state mutation from notification delivery/dismissal. Notifications may be denied, delayed, blocked, or suppressed after force-stop. Reopening reconciles records and preferences rather than claiming to have delivered missed notices. Backdated next dates may legitimately remain overdue; never fast-forward repeatedly until they look current.

### R12 — Scope, safety, and honest commercial boundaries

No automatic equipment safety assessment, certification, legally certified signature, payment handling, account creation, server-side processing, or multi-user synchronization. Generic service findings and customer reports do not make the app suitable for regulated certification work. Pilot acceptance must assess that distinction before selecting a trade.

No paywall or trial is implied. Existing records, reporting, backup, and exports have no dependency on a commercial layer in this map. A later licensing proposal must define offline operation/restoration and protection of access to existing records before it can enter the specification.

## F2. Critical end-to-end journeys

### J01 — First customer, one machine, two plans

S01-A01 → S06-A01 creates **Northside Gym / Main site** together. S05-A05 → S11-A03 creates **Treadmill 4**, with or without a serial number. S10-A04 → S13-A03 creates **Quarterly inspection**, next due 5 December 2026, interval 3 months. Repeat for **Annual belt service**, next due 5 September 2027, interval 1 year.

The machine has two independent obligations. The December inspection first enters the 30-day due-soon list on 5 November 2026; the annual service stays later. No service history was invented by adding plans. The technician can work before completing the optional profile, but supplies report issuer/technician before finalization.

### J02 — Due work, customer contact, postponement, completion

A plan due 1 September 2026 appears overdue. S02-A01 → S03-A03 → S12-A03 opens the dialer. Returning creates no contact proof. S15-A01 manually records a successful call and, if needed, a contact follow-up. S12-A04 books 7 September through S16-A05. S17-A03 postpones to 10 September.

The obligation remains due **1 September** throughout. On 10 September, S17-A01 → S19 records the work. S20-A02 explicitly fulfills it, and S20-A03 confirms 10 December 2026 for a three-month interval. S20-A11 finalizes once; S29-A06 opens sharing without inventing receipt. Cancelling the share leaves a finalized service and a ready, unshared PDF.

### J03 — Multiple machines, partial maintenance, discovered fault

One visit has Machine A quarterly inspection, Machine A annual maintenance, and Machine B inspection. Machine A inspection finds a worn guard but completes every required inspection check; its inspection obligation can be fulfilled. Its maintenance is not finished because a part is unavailable; that obligation remains due. Machine B inspection is completed.

S19-A08 stages a corrective task for the guard. S20 reviews all three outcomes, creates the task with a chosen due date, and advances only the two completed inspection plans. The report includes the unresolved fault, uncompleted maintenance, two future inspection dates, and the still-outstanding maintenance date. The technician later performs a linked corrective one-off visit and explicitly resolves the task; doing so does not advance the unfinished annual maintenance unless that work is separately selected and fulfilled.

### J04 — Interrupted visit with photos

During S19, a camera return shows Copying, then Saved on this device. A phone call/normal interruption does not discard saved answers. If the app process restarts, S02-A04 → S03-A04 → S17-A02 reopens the last durable draft.

If termination occurred before the photo copy committed, the app says Photo not attached; it does not display a dangling thumbnail as proof. If storage becomes full while entering text, U07 shows the last save time, Retry, storage settings, and optional Copy unsaved text. Previously saved work survives; the latest uncommitted text is not guaranteed. The technician can finalize honest partial work rather than falsely completing every selected service.

### J05 — Template/address changed after finalization

An inspection/report finalized on 10 September captures the actual checklist and address. Editing the template through S24-A05 or site address through S08-A04 on 20 September changes future records, not that report. S27/S29 still show the original revision and file.

If the original address was genuinely wrong, S27-A03 → S28 explicitly corrects the historical displayed address with a reason. Save creates a new revision and new report version. Prior externally shared PDF remains out of the app's control; S29-A09 can manually record sending the corrected version, without claiming the old one was recalled.

### J06 — Backdated service and correction without double advancement

A technician enters work performed on 31 January 2027 using S16 historical mode. It defaults Record only. If the outstanding plan is still eligible and no later completion exists, they explicitly apply it once; a one-month interval proposes 28 February 2027. On 5 March, that date is still overdue—correctly, not advanced repeatedly to a future month.

If the actual date was 30 January, S28 can replace the latest uncontested schedule effect after explicit review. If a later service already occurred, or someone deliberately changed the next due date, correction preserves accurate history but leaves the current schedule unchanged. Reopening, retrying a PDF, or saving the correction cannot count the same obligation as newly fulfilled again. Voiding a wrong-machine entry uses the same guarded impact review, then a correctly linked new historical entry.

### J07 — Notifications denied, external app absent, share cancelled

S32-A01 requests notifications; denial leaves Home/Work fully usable with an accurate blocked status. No exact-alarm or battery-exemption escalation follows. A device without an email handler makes S14-A03 unavailable; S14-A05 copies the address/message, and S15 still permits a truthful manual contact record.

Built-in S29 preview works without an external PDF reader. Cancelling S29-A06's Sharesheet neither resolves a follow-up nor sets Sent. The technician's saved visit/report remains available for another attempt.

### J08 — Initial migration and complete recovery

An empty dataset imports a CSV through S37. Two rows share a customer/site key but disagree on its address: validation blocks the whole import and produces row-specific feedback. The user fixes the external file and revalidates. A repeated serial warning is either corrected or acknowledged as distinct equipment. S37-A08 creates all validated records once; imported machines have no recurring plans until explicitly configured.

After configuring plans and recording visits/photos/PDFs, S34 creates and verifies a full backup. On another device, S35 inspection confirms format/files/space; it warns that any existing local dataset will be replaced. A safety backup is offered. Successful restore brings back actual records, attachments, and unfinished drafts; reminders await device-specific review. A truncated backup, newer unsupported format, unavailable provider, or insufficient staging space stops before replacement. CSV export cannot substitute for that missing full backup.

### J09 — Ownership transfer without history leakage

Machine A moves from Northside Gym to Riverside Gym. S10-A09 first exposes its booked visit and open equipment-linked corrective task. The technician cancels/removes the old booked selection and resolves/cancels the task explicitly. The move pauses recurring plans for the new-owner decision, while the app's machine identity and prior location history remain.

Northside's service report continues to name Northside and stays in its history. Riverside's customer history starts with work actually recorded for Riverside; no old private customer visit is silently imported into its next report. The technician resumes appropriate plans with reviewed dates, rather than treating the move as service completion.

# G. Brief feasibility and dependency notes

**Verification date: 5 September 2026.** The middle column summarizes verified official platform capabilities/constraints. The chosen behavior/fallback is this map's recommendation. These checks establish plausible implementation routes, not tested implementation, a Play approval, or a guarantee on every device. No ServiceLoop SDK, UI framework, library release, or toolchain is locked here.

| User-visible capability | Supported route and verified limitation | Proposed fallback / scope constraint |
|---|---|---|
| User-controlled notifications | Android notification permission is required for ordinary notifications on versions that enforce it; channel controls remain with the user. [P01, P17] | Contextual request; actual blocked status; Home/Work remain usable. Two functional channels, no attempt to override system choices. |
| Daily work summary | Persistent background work supports deferrable recurring tasks, not exact wall-clock execution. [P03] | “Around [time]”; deduplicate by business date; no backlog of missed alerts. |
| Appointment heads-up | Inexact alarms are supported; battery restrictions can delay delivery. Precise alarms are a different, more restricted capability. [P02] | Conservative lead choices, current-booking recheck, no exact permission, no obsolete late alert. |
| Restart / force-stop | Force-stopped packages remain stopped until qualifying user interaction; pending intents can be cancelled by the platform. [P04] | Reconcile on reopening; no post-force-stop or pre-unlock delivery promise. |
| Saved fieldwork | Long-lived application data belongs in persistent local storage; UI state holders alone are not durable storage. [P05] | Write progress as it changes, show actual save status, distinguish unsaved forms from durable drafts. |
| Photo intake | Photo Picker limits access to selected media. Delegated camera capture is available without declaring ServiceLoop's own camera permission. [P07, P19] | Copy before accepting; handle camera denial/unavailability in the external app; choose existing photo or continue without one. |
| Persistent attachments | App-private file storage is available without broad storage permission; app-specific files are removed with uninstall. [P06] | Private persistent copies, not cache or temporary source links; explicit full backup for recovery. |
| Read/save backup, CSV, PDF | Storage Access Framework grants access to user-selected documents/providers; provider access and availability are not universal. [P09] | Manual picker, local destination fallback, partial-file warning, no all-files access or assumed cloud upload. |
| File sharing | FileProvider/content URIs and temporary grants support controlled file handoff. [P10] | Share final file only; explicit user action; no delivery/receipt status inferred. |
| Offline PDF creation | Android PdfDocument supports generating PDF pages from native content. [P11] | Fixed layout and pagination; failed generation can retry independently of service completion. |
| In-app PDF viewing | PdfRenderer supports rendering locally accessible PDFs, with input/file requirements. [P12] | Preview generated local files; provide matching accessible text; optional external viewer only. Arbitrary third-party PDF import is not a CORE feature. |
| Calls/messages/maps | Android supports user-directed intents; a compatible handler may be absent. [P08] | Dialer/composer rather than automatic call/send; copy visible details; manually record outcomes. |
| Contact selection versus access | The baseline requires no address-book ingestion; Play documents minimum-scope alternatives to broad personal-data permissions. [P15] | Manual phone/email entry in CORE. No READ_CONTACTS, SMS/call-log ingestion, or automatic contact sync. |
| Recovery versus OS backup | Standard Auto Backup documents a 25 MB per-user allowance; transfer/configuration behavior has additional platform/OEM conditions. [P13] | Do not rely on automatic OS backup for photo history. Complete manual package, verified copy, replacement restore. No automatic off-device guarantee. |
| Privacy / Play declarations | Play requires a privacy policy and accurate data-use disclosures; minimum-scope access is preferred where sufficient. [P14–P15, P18] | Bundled policy plus required public policy page; audit actual SDK/data behavior before submission. “Offline” does not eliminate disclosure work. |
| Accessible controls | Android guidance covers target sizing, labels, scalable text, and non-gesture alternatives. [P16] | Implement/test the S38 contract; no claim of certified accessibility or fully tagged accessible PDF output. |

**Permission boundary:** ordinary notifications when requested; selected file/photo access; temporary file sharing. No exact alarms, location tracking, accessibility-service automation, broad storage/media access, broad contacts access, SMS or call-log access, or mandatory battery exemption. Delegated capture does not require ServiceLoop to become a camera app. Backup/export/import run as explicit user operations; a foreground-service subsystem is not hidden in this promise.

**Maintenance-sensitive requirements:** snapshot integrity, restoration across supported backup versions, interrupted writes, report pagination/long text, attachment references, notification invalidation, and accessible large-text forms need implementation tests. Platform APIs do not supply the application's business transaction rules; R01–R12 must be implemented and verified by the product. Current store submission requirements must also be rechecked when an actual build is submitted.

## Official source register

These are the sources freshly checked for this map, distinct from the historical competitor links in the supplied research context. Linked titles make this document portable.

| ID | Official source |
|---|---|
| P01 | Android Developers — [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). |
| P02 | Android Developers — [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms). |
| P03 | Android Developers — [Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work). |
| P04 | Android Developers — [Android 15 behavior changes: package stopped state](https://developer.android.com/about/versions/15/behavior-changes-all). The cited stopped-state limitation is relevant background, not a selected app target version. |
| P05 | Android Developers — [Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states). |
| P06 | Android Developers — [Access app-specific files](https://developer.android.com/training/data-storage/app-specific). |
| P07 | Android Developers — [Photo picker](https://developer.android.com/training/data-storage/shared/photo-picker). |
| P08 | Android Developers — [Common intents](https://developer.android.com/guide/components/intents-common). |
| P09 | Android Developers — [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files). |
| P10 | Android Developers — [Sharing a file](https://developer.android.com/training/secure-file-sharing/share-file). |
| P11 | Android Developers — [PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument). |
| P12 | Android Developers — [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer). |
| P13 | Android Developers — [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup). |
| P14 | Google Play Console Help — [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). |
| P15 | Google Play Console Help — [Restricted permissions and minimum-scope alternatives](https://support.google.com/googleplay/android-developer/answer/16935362?hl=en). |
| P16 | Android Developers — [Make apps more accessible](https://developer.android.com/guide/topics/ui/accessibility/apps). |
| P17 | Android Developers — [Create and manage notification channels](https://developer.android.com/develop/ui/compose/notifications/channels). |
| P18 | Google Play Console Help — [Provide information for the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en). |
| P19 | Android Developers — [Minimize permission requests](https://developer.android.com/privacy-and-security/minimize-permission-requests), including delegated photo capture. |

No billing capability is asserted or verified because no commercial flow is included. The optional-extension map below identifies additional validation work rather than treating future dependencies as already approved.

# H. Separate extension map and exclusions

## H1. LATER — explicitly absent from CORE

These IDs describe possible future controls. They are **not hidden or disabled menu items in the first product**. A later extension requires its own review before these surfaces become part of the app.

| Extension | Future entry point and principal actions | New dependency/workflow and reason to defer |
|---|---|---|
| **L01 — Runtime/hour/cycle servicing** | Equipment detail → Meter readings. **L01-A01 Add reading:** value, unit, actual read date. **L01-A02 Correct reading:** reasoned correction. Plan editor → **L01-A03 Set usage interval/next threshold**. Completion → **L01-A04 Confirm service meter value and next threshold**. | Requires stale-reading labels, monotonicity/rollover/replaced-meter handling, correction semantics, and a second kind of due state. Nothing updates itself without a user-entered reading. Date-based CORE must not assume these readings exist. |
| **L02 — Combined date and usage rules** | Plan editor → **L02-A01 Choose whichever comes first** and both thresholds. Review → **L02-A02 Explain which threshold triggered service**; completion → **L02-A03 Confirm both next thresholds**. | Depends on L01 and decisions about missing/stale readings and reset rules. Do not quietly make this an extra field on a date-only plan. |
| **L03 — QR/barcode identification** | Equipment → **L03-A01 Assign code**; register → **L03-A02 Scan to find equipment**; unknown result → **L03-A03 Link scanned value after confirmation**; equipment → **L03-A04 Remove/reassign code**. | Needs a scanner route/library/camera validation, duplicate-label rules, unknown-code handling, and label workflow. Codes identify local records, not a manufacturer database. Manual search remains complete without it. |
| **L04 — Customer acknowledgement/signature** | Completion/report → **L04-A01 Request acknowledgement**, **L04-A02 Enter signer's name/role**, **L04-A03 Draw/clear/accept mark**, **L04-A04 Record declined/not available**. | Requires consent wording, display/privacy design, correction/void treatment, and clarity about what is acknowledged. A drawn mark is not a legally certified electronic-signature service. No regulatory claim follows from adding it. |
| **L05 — User-confirmed calendar export** | Booked visit → **L05-A01 Open calendar event draft**; later → **L05-A02 Open existing export reference when available** or **L05-A03 Export another draft with duplication warning**. | User-confirmed insert intents have a platform route [P08], but handler, timezone, duplicate, reschedule, and cancellation behavior require design. Export is not two-way sync; ServiceLoop cannot assume the event was saved or updated. |
| **L06 — Additional report layouts / specialist fields** | Settings → **L06-A01 Select a supported layout**; explicit trade setup → **L06-A02 Configure bounded specialist fields**; report → **L06-A03 Preview chosen layout**. | More captured field definitions, localization, pagination, retained layout versions, and trade validation. Defer a general-purpose form builder and any certification claim; the fixed report remains useful independently. |
| **L07 — Optional off-device backup automation** | Data → **L07-A01 Connect/select a provider**, **L07-A02 Choose backup cadence/network limits**, **L07-A03 Back up now**, **L07-A04 Review failures/retention**, **L07-A05 Disconnect**. Restore remains S35-style replacement. | Needs a supported provider/API path, authentication/access renewal, quota and network handling, verification/retention, and security review. Background limits still apply. Copying backups between devices is not simultaneous editing or synchronization. |

**New optional recommendation for later review:** password-encrypted backup packages would reduce reliance on destination protection, but need a vetted implementation route, password-loss/no-server-recovery messaging, version compatibility, and restore testing. They are **not** silently promised by the unencrypted CORE backup. The owner can instead promote encryption into CORE through an explicit scope revision before implementation.

## H2. Commercial layer — OPEN, not a designed subsystem

No price, trial, unlock, purchase button, or paid limit appears in this map. Before adding one, review purchase initiation, cancellation/pending results, entitlement verification, offline use, restoration on another device/account, refunds/revocation, and continued reading/reporting/export/backup access to existing records. That decision needs current official Play billing/policy checks at that time. It must not make routine record recovery depend on buying the same data back.

## H3. OUT — product boundary, not a backlog

Quoting, invoices, payments, accounting, payroll, sales pipelines, stock purchasing, technician dispatch, team permissions, simultaneous editing, live location, route optimization, customer portals, automated messaging campaigns, full communication-history ingestion, sensor integrations, predictive maintenance, proprietary manufacturer databases, and regulatory-certification claims are outside this product.

Cloud synchronization and multi-technician collaboration require a separate product/architecture decision. They are not L07 backup automation, not a Settings switch, and not something CORE is secretly awaiting.

# I. Coverage audit and review decisions

## I1. Traceability to every proposed CORE area

| Prompt area | Screens / representative actions | Governing rules / lifecycle |
|---|---|---|
| A. First use/business profile | S01; S31-A01–A04; S20-A09 | U01; R08, R10; E03. |
| B. Home/navigation | S02; S03; S04/S09; U08 | R01, R11; E01–E02. Counts have named destinations; attention includes unfinished work, reports, corrections, and backups. |
| C. Customers/sites | S04–S08; U02/U03 | R06–R07; E02. Default-site path, inheritance, limited reassignment, archive and unused-only deletion resolved. |
| D. Equipment | S09–S11; S10-A09–A13 | R06–R07; U04. Missing IDs, duplicates, movement/ownership, retirement and history covered. |
| E. Service plans | S12–S13; S20-A02–A04 | R01–R05; E01/E07. Independent plans, baseline dates, recurrence, pause/resume/end and overrides covered. |
| F. Contact/booking | S14–S17; U06 | R01–R02, R10–R11. Manual contact proof, booking/reschedule/cancel, plan reservations and system handoff failure covered. |
| G. Inspection templates | S23–S25; S19; U06 | R04, R06–R07; E06. Types, required behavior, duplicate/edit/reorder/archive and historical copies covered. |
| H. Working visit | S18–S19; U04/U06/U07 | R02, R04, R08; E03–E05. Multi-equipment, one-offs, text parts, photos, interruptions and loss-aware removal covered. |
| I. Completion/next service | S20; S27–S28 | R01–R05; E07. Per-item fulfillment, record-only history, partial work, fault/follow-up separation and retry boundaries covered. |
| J. Follow-ups | S21–S22; S15-A03–A04; S19-A08–A09 | R02, R04, R06. Distinct contact/corrective tasks, manual/visit resolution, rescheduling, cancelling/reopening and staged creation covered. |
| K. History/reports | S26–S29; U04/U05 | R05, R07, R10; E06–E07. Fixed revisions/PDFs, corrections/voids, report selection, original file loss and external-copy limits covered. |
| L. Reminders | S32; notification actions S32-A12–A14 | R01, R11; E08–E12. Leads, summary versus individual, denied permission, dismissal, no in-app snooze and no precise guarantee covered. |
| M. Ownership/recovery | S33–S37; U05/U07 | R08–R10; E13–E14. Complete backup, verified status, unencrypted-file warning, replacement restore, CSV export and bounded initial import covered. |
| N. Settings/privacy/help | S30–S38; U01–U08 | R08, R10–R12; Section G. Exact settings, accessibility, permission minimization, private report fields, external sharing and offline help covered. |

## I2. Structural and contradiction audit

| Check | Result of this design review |
|---|---|
| Reachability | Every S01–S38 destination has a declared caller/Back route. U surfaces have declared callers. No screen requires LATER functionality to reach it. |
| Mutation outcomes | Ordinary editors have Save/Cancel/error behavior. Working/correction screens distinguish durable draft saving from finalization. Nested independent records versus staged template items are explicitly different. |
| Date consistency | Booking/contact/notification actions never fulfill service. Partial and record-only work keep obligations unchanged. Manual date edits and correction effects are separately named/reasoned. |
| Duplicate completion | One current obligation reservation plus finalization identity checks prevents repeat consumption. PDF retry/restore does not replay fulfillment. |
| Historical stability | Captured templates/identities and stored PDF bytes are retained. Corrections/voids are revisions, not ordinary edits. Current context is separated from historical report content. |
| Relationship integrity | Archive/retire/move actions display and handle affected work; in-progress work blocks destructive relationship changes. Historic customer ownership does not follow equipment to its new customer. |
| Recovery honesty | “Saved locally,” “exported,” “backup written,” and “verified backup” are distinct. Restore replaces after staging; missing originals and unsupported formats are not quietly accepted as complete. |
| Permission/failure paths | Notification denial, external-handler absence, cancelled capture/share, revoked provider access, full storage and unreadable files all have visible fallbacks without inventing success. |
| Scope dependency | No CORE action needs runtime meters, QR labels, signatures, calendar export, cloud automation, licensing, sync, or team infrastructure. |
| Unverified claims | No market score, revenue projection, legal-certification claim, universal device guarantee, or fixed development estimate is promoted from historical research into this specification. |

This is a **conceptual consistency review**, not executed software tests. The referenced implementation acceptance requirements still need actual testing, especially all-or-nothing finalization/restore, corrupted or missing attachments, large reports, backdated corrections after later work, timezone changes, and large-text/screen-reader operation.

## I3. Consequential owner decisions

| Decision | Recommended default for the next review |
|---|---|
| **1. Accept this single-device boundary?** | Yes for the first pilot: one active device/dataset, explicit replacement restore, no shared editing. If the target trade requires concurrent users, reopen the product boundary rather than adding a hidden Sync dependency. |
| **2. Is one site and one actual service date per visit sufficient?** | Yes initially. Finalize partial work and create a later visit for another day. Do not adopt multi-day/per-line dating until observed work genuinely demands it. |
| **3. Confirm fulfillment and recurrence rules?** | Completion-date-plus-interval; explicit per-plan fulfillment; required unchecked items prevent fulfillment, not preservation of partial work. Keep anniversary/usage rules out unless real target obligations require them. |
| **4. What recovery/confidentiality promise is acceptable?** | Manual full backups, read-back verification, and no automatic cloud promise. Treat unencrypted packages as a prominent limitation. Decide before implementation whether the pilot's access/customer data requires password-encrypted backup to be promoted into CORE. |
| **5. Are initial-only CSV import and one fixed report sufficient?** | Yes for the pilot. Validate one real migration file and customer report in an accessible trade. Do not turn import into merging or reporting into a form/certification platform to cover hypothetical needs. |
| **6. Which trade, languages, and commercial model come first?** | Choose one reachable pilot trade and only its needed initial languages; leave name/pricing/licensing uncommitted until the complete workflow is tested. Nothing in this map approves a subscription, trial, or unlock price. |

**Proposal ready for review. No feature, platform version, commercial term, or implementation milestone is frozen by this document.**
