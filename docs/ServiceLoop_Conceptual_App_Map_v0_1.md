# ServiceLoop — Complete Conceptual App Map

**Version:** 0.1 · **Prepared:** 5 September 2026  
**Status:** PROPOSAL READY FOR PRODUCT REVIEW — not an approved specification or implementation authorization.  
**Platform verification:** Official Android and Google Play documentation checked on 5 September 2026; see Section G.  
**Basis:** `ServiceLoop_Functional_App_Map_Prompt_v1_1.md` [B1] and the complete `ServiceLoop_Research_and_Product_Context.md`, including Appendix A [B2].

## Reading guide

Sections A–C establish the boundary, navigation, and vocabulary. Section D is the button-level map. Sections E–F define automatic behavior, cross-screen rules, and worked journeys. Section G contains the limited platform checks. Sections H–I separate extensions and exclusions, audit coverage, and identify owner decisions.

`Snn` identifies a destination; `Dnn` a reusable dialog/sheet; `Snn-Ann` or `Dnn-Ann` an action; `Rnn` a cross-screen rule; `Enn` an automatic behavior; `Jnn` a journey; `Pnn` a feasibility note. An editor or sheet is not necessarily a separate implementation: these identifiers distinguish user-visible responsibilities, not programming components.

---

# A. Product boundary and design assumptions

## A1. The product being mapped

> **An offline service book for an independent technician: know which customer equipment needs attention, record the work, and leave the customer with a professional report.**

**PROPOSED BASELINE.** One business, one technician, one authoritative dataset on one device. Customers, sites, equipment, date-based service plans, contact notes, appointments, fieldwork, corrective follow-ups, historical records, reports, and deliberate data recovery are CORE. No ServiceLoop account or application backend is required for these functions. A network may be necessary for a selected external communication app or file destination.

**USER-STATED.** Felix wants to explore this direction and review the complete bounded workflow; he has explicitly raised scope creep. The supplied context does not establish that he runs a servicing business, has interviewed technicians, or has approved this specification. [B2]

**REPORTED EVIDENCE.** The earlier research and competing assessments remain historical background. Their scores, prices, willingness-to-pay judgments, and development estimates are not adopted here. This document neither reruns the market study nor claims validated demand. [B2]

**ANALYST JUDGMENT.** The hard part of this product is not calculating a date. It is keeping partial work, historical evidence, current obligations, and recovery consistent. The proposal preserves those safeguards rather than adding broad field-service features.

## A2. Coherent defaults used throughout

| Topic | Proposed default and reason |
|---|---|
| Navigation | Three primary destinations: **Home**, **Work**, **Customers**. Equipment is a view within Customers; templates and data operations are reachable from Settings. |
| Physical scope of a visit | Exactly one site and one actual service date; several machines and several services are allowed. Multi-day work is closed as partial and continued in another visit. |
| Recurrence | Actual completion date plus a positive interval in days, weeks, months, or years. The next date is shown and confirmed; no fixed-anniversary or usage-counter engine. |
| Obligation counting | One current obligation per active plan. Missed intervals do not manufacture a stack of identical overdue jobs. |
| Appointments | At most one Booked or Working visit claims a particular current obligation. Other machines, plans, and one-off work remain independently selectable. |
| One-off work | A named service line on equipment, without a recurring plan. No invented recurrence is necessary. |
| Fieldwork | Multiple unfinished visits may exist. Each autosaves independently; there is no running labor timer or exclusive “on shift” state. |
| Reports | One fixed, multi-equipment layout. A final record is independent of PDF generation and sharing. Internal/access notes are never selected implicitly. |
| Time | A business time zone, initially the device zone, is stored explicitly. Dates mean business-local dates; appointments display their zone. No location permission. |
| General forms | Explicit Save applies changes. A temporary recovery buffer protects unsubmitted input when possible; it does not apply changes to live records. |
| Fieldwork/correction drafts | Durable autosave with visible Saving/Saved/Not saved states. Back leaves a saved draft rather than cancelling already-saved work. |
| Reminders | Daily work summary, with optional approximate appointment alerts. Notifications never change business dates or outcomes. |
| Import | One fixed, create-only CSV format for customers, sites, and equipment. No plans, historical visits, photographs, arbitrary column mapping, or overwriting existing data. |
| Restore | Replace the dataset after verification and explicit confirmation. Never merge datasets. |
| Commercial layer | No trial, price, purchase screen, or paywall in this map. Licensing remains OPEN; CORE does not assume a purchase mechanism. |
| Presentation | Follow system appearance and text scaling. English labels here are design language, not an approved launch-language decision. A4 is the proposed fixed report page format. |

## A3. Explicit additional CORE recommendations

These are supporting recommendations, not features already approved in the attachments:

**N1 — Portable passphrase-protected complete backups.** Backups include private customer and access information. Protection should travel with the file rather than depend on a key available only on the original phone. This adds passphrase entry, verification, and an unavoidable forgotten-passphrase warning. See S34–S35/P10.

**N2 — Visible record correction and voiding with schedule reconciliation.** A mistaken final record must be correctable without pretending it never existed or advancing a plan twice. See S26/R06.

**N3 — A clearly labelled incomplete recovery copy when source attachments are already missing.** A damaged photo must not prevent preservation of every remaining record. This is not counted as a complete backup, and restoring it requires a separate warning. It reuses the backup/restore workflow. See S34–S35.

The business-zone choice, stable record references, and conservative dependency checks below are design resolutions inside the requested scope, not additional product modules.

---

# B. Complete navigation map

`→` means navigation; `[action]` changes state or opens a handoff. Back normally returns to the actual entry point with filters and scroll position preserved. Notification/deep-link entry constructs the relevant Work or Customers parent rather than leaving a dead-end screen.

```text
First launch / no initialized dataset
└─ S01 Welcome
   ├─ [Start empty] → S02 Home
   ├─ [Import existing directory] → S37 CSV import
   └─ [Restore backup] → S35 Restore

Persistent primary navigation
├─ S02 Home
│  ├─ Overdue / Due soon → S03 Work · Due services, matching filter
│  ├─ Booked visits → S03 Work · Visits, next-seven-days filter
│  ├─ Unfinished visits → S03 Work · Visits, Working filter
│  ├─ Follow-ups → S03 Work · Follow-ups, due/overdue filter
│  ├─ Recovery / backup warning → D01 / S33
│  ├─ [Search] → S04 Search
│  └─ [New visit] → S18 Visit setup
├─ S03 Work
│  ├─ Due services → S13 Plan → S18 / D05 / S29
│  ├─ Visits → S19 Booked/cancelled visit / S20 Working visit / S25 Final record
│  └─ Follow-ups → S27 Follow-up → S28 Edit / D07 Close
└─ S05 Customers
   ├─ Customers view → S06 Customer
   │  ├─ [Edit] → S07 Customer editor
   │  ├─ Sites → S08 Site → S09 Site editor
   │  ├─ Equipment → S10 Equipment
   │  └─ History → S40 Scoped history
   └─ Equipment view → S10 Equipment
      ├─ [Add/Edit] → S11 Equipment editor
      ├─ [Move] → S12 Move equipment
      ├─ Plans → S13 Plan → S14 Plan editor
      └─ History → S40

Work execution
S18 Visit setup
├─ [Book] → S19 Visit detail
├─ [Start now] → S20 Working visit
└─ [Record past visit] → S20, historical-entry mode
S19 → [Start] S20 / [Edit booking] S18 / [Cancel booking] D06
S20 → S21 Machine within visit
       ├─ Service line → S22 Work item and checklist
       ├─ Finding → S23 Finding editor
       ├─ Parts → D09 Part entry
       └─ Photographs → D04 Photo workspace
S20 → [Review completion] S24
S24 → [Draft report preview] S39 / [Finalize] S25
S25 → S39 Report preview and versions / S26 Correction or voiding
S26 → [Commit revision] S25 / [Leave draft] previous destination

Supporting destinations
S04 Search → matching S06 / S08 / S10 / S13 / S19 / S20 / S25 / S27
S27 Follow-up → S28 Editor / S18 linked visit / S29 contact note
S29 Contact note → optional linked S28 follow-up
S40 History → S19 / S25 / S26 / S27 / S29 / read-only change entry

Settings gear on primary destinations → S30 Settings
├─ S31 Business and report identity
├─ S32 Reminders
├─ S15 Inspection templates → S16 Template → S17 Editor → D08 Item editor
├─ S33 Data and recovery
│  ├─ S34 Create/verify backup
│  ├─ S35 Restore/inspect backup
│  ├─ S36 CSV export
│  ├─ S37 CSV import
│  └─ D12 Erase this device's dataset
└─ S38 Privacy, help, and app information

Reusable significant sheets/dialogs
D01 Unsaved changes / recovered input
D02 Customer-site-equipment-plan/template/follow-up selector
D03 Lifecycle/deletion dependency check
D04 Photo workspace and attachment recovery
D05 Contact / message / navigation handoff
D06 Reasoned date/state change
D07 Resolve / cancel / reopen follow-up
D08 Template item editor
D09 Part entry editor
D10 Notification explanation / permission handoff
D11 File operation progress / cancellation / failure
D12 Destructive local reset
D13 Possible duplicate review

Explicit system handoffs (not ServiceLoop destinations)
Photo picker / camera app / selected contact-data picker
Dialer / SMS composer / email composer / map application
Document open/create picker / Android Sharesheet / optional external PDF viewer
Android notification settings / browser for public privacy policy
```

All sheets return to their caller; opening an external application does not complete a ServiceLoop task. No drawer, team selector, account menu, hidden long-press-only menu, or additional dashboard is implied.

---

# C. Conceptual information model and vocabulary

## C1. Records and relationships

| Record | Meaning and boundaries |
|---|---|
| Business profile | Technician and business identity for new reports; not a customer, account, or billing entity. |
| Customer | The customer relationship. Contains a display name, optional main contact details, and internal notes. Owns sites. |
| Site | A named physical service location belonging to one customer. Holds its address, optional contact override, and private access notes. One active site can be the customer's default. |
| Equipment | A particular machine at one current site. Name is sufficient to create it; missing serial numbers are permitted. A stable app reference distinguishes otherwise similar machines. |
| Service plan | A named recurring service on one machine, its interval, current next-due date, and optional reusable template. Several plans may serve the same machine. |
| Current obligation | The outstanding occurrence of a plan. It has a due date and may be linked to a booking or working visit. It is not a separate object the technician must create manually. |
| Template and revision | Reusable ordered inspection questions. Saving an edit creates a new reusable revision; existing working checklists and records retain their captured content. |
| Visit | One attendance or recorded attempt at one site, covering one or more work items. Booking status is independent of service outcomes. |
| Work item | One named service on one machine within a visit. It references either one service plan or no plan for one-off work. It holds its own performed-work note, checklist, and outcome. |
| Machine observation | The technician's observation at a visit: Not assessed, No issue observed, or Issue observed. It is not an automated diagnosis or a certification of safety. |
| Finding | A description of an observation/issue, with a disposition and optional photograph links. An issue can be resolved during inspection or remain open for corrective work. |
| Part entry | A textual description, quantity, and unit recorded against equipment at a visit. There is no stock balance, price, purchase order, or invoice. |
| Contact note | Technician-entered record of a communication attempt or outcome, including its actual time and context. Opening another app does not create it. |
| Follow-up | A dated contact task or corrective-work task. It has a real customer/site/equipment context, optional source finding/visit, and its own open/closed state. |
| Final service record | A fixed version of a visit's identity, actual date, item outcomes, observations, checklist content, notes, parts, and report selections. It exists even before a PDF is made. |
| Report version | A particular saved PDF generated from a specified record revision. Copies already shared are outside the app's control. |
| Change entry | A dated, reasoned move, manual schedule change, cancellation, correction, or lifecycle change. It explains the local record; it is not a compliance-grade audit trail. |
| Full backup / recovery copy | A restorable snapshot of saved local data and files. A recovery copy explicitly lists already-missing source files; it is incomplete. |
| CSV export | Readable structured information, not a substitute for the complete backup or a promise of round-trip import. |

Customer → many sites → many equipment items → many plans. A visit belongs to one customer/site snapshot and includes many work items grouped by equipment. A work item references at most one plan. Each current obligation is consumed at most once; one visit can consume several different obligations.

Every main record has a stable reference visible on its detail screen and included in exports. Customer/site/equipment CSV references are unique within their record type, generated for manual entries and retained from validated imports. Names are not identities. Search can find references; references are not manually renumbered.

## C2. Status vocabulary

| Area | Exact terms and meaning |
|---|---|
| Customer/site | **Active / Archived**. Archived is retained and searchable when included, but unavailable for new current work. |
| Equipment | **In service / Retired**. Retired means outside the current servicing workflow, not deleted or proven unusable. |
| Plan | **Active / Paused / Ended**. Paused retains its due date but produces no current reminder; Ended has no future active obligation. |
| Active obligation | **Overdue** if due date is before today; **Due today** on today; **Due soon** tomorrow through the selected horizon; **Upcoming** beyond it. Booked is an additional badge, not a replacement due state. |
| Visit | **Booked / Working / Finalized / Cancelled**. A final record can additionally be **Voided**. Past booked dates are labelled **Appointment passed — outcome not recorded**, never auto-completed or auto-cancelled. |
| Work outcome | **Performed / Partly performed / Not performed**. A separate **Fulfills current obligation** decision controls recurrence; Performed alone does not. |
| Checklist | **Not started / In progress / Reviewed**. Reviewed requires the explicit review action and all required responses accounted for. It does not mean every response was OK. |
| Status answer | **OK / Issue found / Not applicable / Not checked**. Not checked is the initial state; blanks are never interpreted as OK. |
| Follow-up | **Open / Resolved / Cancelled**; a due/overdue badge is computed separately. |
| Saving | **Saving… / Saved on this device [time] / Not saved — action needed**. “Saved” never means backed up or uploaded. |
| Report | **Not generated / Ready / Generation failed / File missing**, plus record revision, PDF version, and superseded/voided indicators. There is no automatic “Delivered” status. |

---

# D. Screen-by-screen functional map

## D0. Shared interaction contract

These rules apply to every destination below. Each screen cites the relevant shared patterns rather than silently relying on unspecified “standard” behavior.

**U1 — Navigation and list states.** A labelled Back/Up control returns to the actual caller; system Back first dismisses the keyboard or open sheet, then follows the same route. Primary navigation exists on S02/S03/S05, not inside an unfinished editor. Switching primary destinations preserves their current session filters/scroll; a fresh launch starts at Home. Each list provides a visible empty state with its relevant Add action, a no-results state with Clear filters, and Retry on a failed read. A failed read is not displayed as an empty database. Detail screens show archived/read-only state explicitly. A deleted or unavailable deep-link target offers its parent list; it never opens an unrelated record.

**U2 — Input and explicit Save.** Required fields carry the word Required. Names/titles are trimmed, nonempty, and at most 120 characters; short answers at most 500; notes at most 4,000; identifiers at most 120; units at most 32. These are proposed guardrails, not paywalls. Phone strings allow international punctuation; email validation checks basic structure without claiming deliverability. Optional blank fields display Not supplied when material and otherwise disappear from summaries. Numbers must be finite; signed readings are allowed, positive part quantities are required. Dates are selected or typed unambiguously; an actual service date cannot be in the future. No field is silently truncated. Save validates, applies all related changes together, shows Saved, and returns as specified. Double-tapping Save cannot create duplicates. Failure retains the editor and its input; Retry is offered. An unchanged editor closes without a discard question.

**U3 — Draft protection.** Simple editors use explicit Save and a temporary, recoverable input buffer; the buffer is not a live customer, schedule, or template revision. Back/Cancel on changed input opens D01. Working visits and correction drafts instead autosave durable progress and show its state. Leaving them does not undo saved changes. Text saves after a brief input pause and on focus/navigation handoff; discrete answers save when selected. A failed write visibly stops the Saved promise. Critical transitions and external photo launches wait for a successful checkpoint or explicitly offer return to editing. No autosave promises to preserve keystrokes that were never successfully written before abrupt process/device failure. See E03–E04.

**U4 — Confirmation and mutation.** Destructive/reasoned actions use D03/D06/D07/D12. Their final button names the action, not just OK. Cancel/Back before confirmation leaves business data unchanged. A multi-record mutation either completes with its linked effects or leaves the previous state intact. A failed operation never displays success; a repeated completion/retry recognizes an already-committed result.

**U5 — Selection and contextual scope.** D02 shows full customer → site → equipment context. One-site visit selections cannot cross sites. Ineligible records remain visible with an explanation when that helps recovery; they cannot be silently selected. New child editors return the newly saved item to the selector; cancelling a child creation adds nothing. Creating a child does not automatically save an unrelated parent editor.

**U6 — Accessibility and truthful labels.** Controls have text or accessible names; color is supplementary. Large text may wrap cards and expand rows, never hide status, totals, Save, or error text. Touch targets, keyboard focus, TalkBack order, and labelled reorder alternatives are part of CORE. Photos have contextual descriptions/captions. Important actions never require dragging, swiping, pinching, or long pressing. S39 has a structured text alternative to a graphical PDF preview. Platform route: P11.

### D01 — Unsaved changes and recovered input

**Entry/back:** U3 editors, app recovery, or a failed-save departure. Displays the editor/record name, last successful buffer/save time, and whether changes have been applied.

| ID | Control | Availability and result | Cancellation/failure |
|---|---|---|---|
| D01-A01 | Keep editing / Resume recovered input | Return to the same editor and recovered values. On launch, reopen its recorded context. | No business mutation; missing parent produces a safe parent-list explanation. |
| D01-A02 | Discard changes | Discard only the uncommitted buffer and return to caller. Existing saved entity is unchanged. | Confirm shows the relevant entity; failed cleanup does not apply the draft. |
| D01-A03 | Retry saving | Failed durable Working/correction save; retry, then allow departure after acknowledgement. | Remain on failed state if storage is still unavailable. |
| D01-A04 | Leave without unsaved changes | Only after a save failure; explicitly warn that changes after the displayed checkpoint may be lost. Saved work remains. | Never labelled Cancel visit. |

There is no general-purpose drafts organizer. Recovery is offered on restart or when reopening the affected editor; Home can show a recovery banner. Deliberately exiting a simple editor means saving or discarding it, not accumulating hidden half-created customers.

### D02 — Context selector

**Entry/back:** Entity links in creation, booking, follow-up, template attachment, and corrective-linking flows. Title states what is being chosen. It shows names, references, parents, state, and useful identifiers. Corrective-task linking during a Working/correction draft may also show same-machine proposals from that same draft, explicitly labelled Proposed — not yet live; these are never exposed as live tasks in global Work lists.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D02-A01 | Search / Clear search | Filter the eligible choices by name/reference/identifier; no data mutation. | No matches offers Clear search, not automatic creation. |
| D02-A02 | Select row / selection checkbox | Stage single or multiple selections in the caller's context. | Ineligible row explains its blocker. |
| D02-A03 | Use selection | Return staged choices; required count and one-site checks apply. | Disabled until valid; caller still controls its Save. |
| D02-A04 | Add customer / Add site / Add equipment / Add template | Open the corresponding editor S07/S09/S11/S17 when that type is allowed. | Cancelling returns without a new selection. |
| D02-A05 | Show historical/inactive | Only historical-entry and history-linking modes; current booking never gains eligibility from this switch. | View-only until a permitted historical selection is made. |
| D02-A06 | Cancel | Discard staged selections. | Leaves caller's previous selection unchanged. |

### D03 — Lifecycle or deletion dependency check

**Entry/back:** Archive, restore, retire, return-to-service, move, or permitted Delete actions. Shows the named entity, exact consequences, blocking references, and the R07 policy that applies.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D03-A01 | Open blocking item | Open its visit, plan, site, equipment, or follow-up; return to recheck dependencies afterward. | Does not resolve it automatically. |
| D03-A02 | Confirm named action | Available only after dependencies and required reason are satisfied; apply the action and return to parent/detail. | No silent cascading cancellations or deletions. Failed write leaves all related records unchanged. |
| D03-A03 | Cancel | Return unchanged. | Always available before commit. |

The modal lists what will be preserved, not only what disappears from the active list. When Delete is ineligible, the detail menu has no functioning delete action; its explanation points to archive/retirement instead.

### D04 — Photo workspace and attachment recovery

**Entry/back:** Business logo, equipment profile, S21/S23 fieldwork, S24 report selection, or read-only history. Shows a full-size preview, context, capture/import time, caption, saved state, and report inclusion where relevant. Profile/logo modes hold one image; visit modes hold a collection.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D04-A01 | Take photo | Checkpoint caller; open external camera with a prepared destination. On return, validate and copy the actual image before adding it. | Cancel adds nothing. Missing camera/security failure offers Choose photo or Return. An empty “success” result is not accepted. |
| D04-A02 | Choose photo | Open system photo picker, with document-picker fallback. Only deliberately selected images are read and copied into local app storage. | Cancel changes nothing; unavailable cloud image offers Retry/Choose another. |
| D04-A03 | View / Previous / Next / Zoom in / Zoom out / Fit | Inspect the saved copy and legibility; explicit controls accompany gestures. | Missing image displays File missing, not an empty successful preview. |
| D04-A04 | Caption | Optional, up to 500 characters; labelled Customer-visible only when selected for report. Save semantics follow the caller. | A failed caption save remains visible as Not saved. |
| D04-A05 | Include in customer report | Visit photos only; default Off. Stages inclusion in S24 or correction draft. | Hidden for non-visit images and read-only versions. |
| D04-A06 | Remove / Replace | Editable contexts only; confirm removal of the app's copy/reference. Existing frozen versions retain any referenced image. | Never deletes the source from the phone's gallery or changes an issued PDF. |
| D04-A07 | Use / Done | Explicit-form mode returns selected image to its form; autosaved visit mode closes after checkpoint. | No report or customer/profile mutation before the caller's required Save. |
| D04-A08 | Locate missing original | Only for a missing local attachment: select a file; accept as the original only if its stored integrity identity matches. | Mismatch does not silently substitute historical evidence; use a record correction for a replacement. |

**Proposed limits:** one profile image, one logo, up to 20 visit photographs per machine and 100 per visit. Service photographs are stored as optimized, orientation-corrected copies, with longest edge at most 2,560 pixels and no upscaling; originals outside ServiceLoop are untouched. GPS/other source metadata is not copied into report images. Show this before first intake, and require a legibility preview. No video, annotation editor, background gallery scanning, or forensic-original claim. Limits are visible before capture/selection; failed or over-limit files do not displace existing photographs. P03–P05 support the route.

### D05 — Contact, message, and navigation handoff

**Entry/back:** Customer/site/plan/visit/follow-up contact action. Shows the chosen recipient, available site override versus customer fallback, and the destination data. Does not expose internal notes in message bodies.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D05-A01 | Recipient: Site contact / Customer contact | Choose available saved details; defaults to nonblank site details, with the actual recipient displayed. | Missing details lead to the appropriate editor, never an invented number. |
| D05-A02 | Open dialer | Open selected number; technician initiates the call externally. | No automatic contact record or Connected status. |
| D05-A03 | Compose text message | Open SMS composer with recipient and editable, minimal service/booking text. | No automatic sending or delivery assertion. |
| D05-A04 | Compose email | Open email composer with recipient, subject, and minimal editable body. | No mail account/handler offers Copy details. |
| D05-A05 | Open map | Send the visible site address to a suitable map app. | No route computation or claim that the address was geocoded correctly; missing address offers Edit site. |
| D05-A06 | Copy phone / email / address / prepared message | Copy only the selected visible content after a deliberate tap; show Copied. | Explain clipboard is outside the private record boundary. |
| D05-A07 | Record contact outcome | Open S29; technician supplies what actually happened. | Returning from another app merely offers this action, never presses it automatically. |
| D05-A08 | Close | Return to context. | No changed obligation, booking, or follow-up. |

Unavailable external apps produce an explanation and copy/manual alternatives. There is no WhatsApp-specific guarantee, automatic message ingestion, call-log access, or location permission. P06.

### D06 — Reasoned date or state change

**Entry/back:** Plan pause/resume/end, manual rescheduling, and booking cancellation. Displays old and proposed values and affected reminders. Inputs are a required reason and the relevant date/state fields explicitly named by the originating action. A historical change opens a read-only variant with recorded before/after values, reason, timestamp, and Close only; no mutation controls.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D06-A01 | Confirm named change | Validate and commit exactly the shown change plus its history entry. | Current obligation fulfillment is never inferred. Blockers open D03. |
| D06-A02 | Keep current value | On resume/date suggestions, retain the old due date explicitly, even when overdue. | No invisible recalculation. |
| D06-A03 | Cancel | Return without applying changes. | Reason text is not recorded as an event. |
| D06-A04 | Close | Read-only historical variant only; return to history/detail. | Does not replay or undo the recorded change. |

### D07 — Follow-up closure or reopening

**Entry/back:** S27 or selected closure in S24/S26. Displays the source context, current state, and whether any current visit is linked. From standalone S27, confirmation applies the change immediately. From S24/S26, the same controls only stage that explicitly labelled change; no task state changes before the parent record/revision commits.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D07-A01 | Resolve | Required outcome note and resolved date, default today and not future. Optional linked final visit is shown. Mark Resolved; remove future task reminders. | Does not fulfill a service plan or alter an old report. |
| D07-A02 | Cancel follow-up | Required reason; mark Cancelled and stop task reminders. | Cancelling a task does not mean a fault was repaired. |
| D07-A03 | Reopen | Required reason and new follow-up date; preserve previous closure entries. | Active context required; otherwise explain restore/move prerequisites. |
| D07-A04 | Back | Return unchanged or leave the parent completion choice unstaged. | No premature closure from a draft visit. |

### D08 — Template item editor

**Entry/back:** S17 Add item/Edit item. Fields: Label (required); Type (Status/Text/Number, default Status); Required response (On by default); Unit (required only for Number); Guidance (optional, 500 characters, visible during work and in structured history but not customer PDF).

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D08-A01 | Type | Show the appropriate unit field; changing type clears only incompatible staged configuration after confirmation. | Historical responses are unaffected. |
| D08-A02 | Required response | Determines whether a reviewed checklist requires a value or explicit not-applicable disposition. | Does not force a fabricated reading or prevent saving partial work. |
| D08-A03 | Save item | Validate and return the item to the template editor's draft. | Parent template is not published until S17 Save. |
| D08-A04 | Cancel | Discard item edits. | Parent's last staged item remains. |

### D09 — Part entry

**Entry/back:** S21 part row/Add part. Fields: Description (required); Quantity (required, default 1, positive decimal, up to three fractional places); Unit (required, default “piece”); Note (optional, customer-visible, 500 characters). No price or catalog.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D09-A01 | Save part | Add/update the named machine's part record in the Working/correction draft. | Failure retains inputs; no inventory transaction exists. |
| D09-A02 | Remove part | Existing draft row only; confirm then remove. | Frozen records require S26. |
| D09-A03 | Cancel | Discard this sheet's uncommitted edits. | Previously saved part remains. |

### D10 — Notification explanation and permission

**Entry/back:** First deliberate enable action or S32. Explains approximate delivery, no business-state effect, and useful operation without notifications.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D10-A01 | Enable notifications | Request the ordinary notification permission where required, then display actual result. | Denial/dismissal leaves all records and due lists usable. |
| D10-A02 | Open Android notification settings | Open app/channel settings when a normal request is unavailable or a channel is blocked. | Recheck actual state on return; do not presume permission was granted. |
| D10-A03 | Not now | Close without enabling delivery. | No repeated forced permission prompt. |

### D11 — File operation progress and failure

**Entry/back:** Photo import, PDF generation, backup, restore staging, CSV import/export. Shows operation, destination/provider when known, phase, and counted progress where measurable. No invented completion estimate.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D11-A01 | Cancel operation | Before final commit: stop and clean temporary files when possible. Display any partial external file that could not be removed. | Source records and previous good output remain intact. |
| D11-A02 | Retry | Retry the failed phase or safely restart the same operation; never replay committed business effects. | Storage/provider failure remains visible. |
| D11-A03 | Choose another file/destination | Reopen the appropriate picker without changing the business record. | Picker cancellation returns to operation result. |
| D11-A04 | Done / Return to record | Available after terminal success/failure with truthful status. | Never labels a partial external write as a verified backup. |

CORE large operations are user-initiated. They do not promise completion while the app is force-stopped or a provider is offline. An interrupted operation is recoverable/restartable, not invisibly declared successful. During a short final dataset switch, Cancel is disabled with the explanation “Finishing replacement”; interruption recovery still selects one complete dataset. R09/P07–P09.

### D12 — Erase this device's dataset

**Entry/back:** S33 only. Shows customer/equipment/visit/photo/report counts, unfinished work, last verified backup, and explicit warning that exported/shared copies are not erased.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D12-A01 | Make backup first | Open S34; return without having erased anything. | Backup failure never continues automatically into erasure. |
| D12-A02 | Erase local data | Require typing ERASE and checking loss acknowledgement. Remove business dataset, drafts, private attachments/reports, settings, and scheduled notifications; return to S01. | Failure remains in a restricted recovery state, not a misleading empty successful reset. |
| D12-A03 | Cancel | Return unchanged. | No staged reset. |

No account-deletion claim is attached to this local action. Files in chosen document providers and recipients' applications remain outside its reach.

### D13 — Possible duplicate review

**Entry/back:** Customer/equipment Save or CSV preview. Shows candidates and the matching fields; a matching name alone is never proof of identity.

| ID | Control | Result | Cancellation/failure |
|---|---|---|---|
| D13-A01 | Open existing | View candidate; the proposed new record remains uncommitted. | Returning does not merge anything. |
| D13-A02 | Create separate record | Acknowledge a possible duplicate and continue with a new stable reference. | A duplicate CSV reference is a hard conflict, not overridable here. |
| D13-A03 | Go back and edit / Cancel new record | Correct input or abandon creation. | No duplicate is created. |
| D13-A04 | Skip imported row/group | CSV mode only: explicitly omit selected equipment row or whole customer/site branch; recompute valid preview. | No partial hierarchy or dangling child is committed. |


## D1. First use, daily work, and finding records

### S01 — Welcome

**Purpose/entry/back:** New installation or successful local reset. Back exits. Restoring and importing can be chosen without entering a business profile first.

**Visible content:** Proposition; “Your records are stored on this device”; distinction between Save and Backup; no-account statement; warning that loss/uninstall/clearing data can require a backup to recover. No notification or contact permission prompt appears on entry. Platform limits are explained in P01/P04/P08.

| ID | Control | Available when | Result/change | Cancel/error behavior |
|---|---|---|---|---|
| S01-A01 | Start empty | Always | Initialize an empty dataset and business zone from the device; open S02. | Failed initialization shows Retry, not an empty working app. |
| S01-A02 | Import existing directory | Always | S37; initialize only as part of a successful import. | Cancel returns here without sample data. |
| S01-A03 | Restore backup | Always | S35 empty-dataset mode. | Failed/cancelled restore leaves Welcome available. |
| S01-A04 | How local storage works | Always | S38 at local-storage help. | Back returns here. |

No sample customers, mandatory tutorial completion, sign-in, payment, or multi-step business wizard. The first useful action can be Add customer.

### S02 — Home

**Purpose/entry/back:** Default working entry and primary Home destination. Back exits; no business state changes. Uses U1/U6.

**Visible content:** Today's business-local date; zone notice only when device zone differs; small Settings/Search controls; five work summaries with clearly named units. Each shows up to three relevant rows and a View all action; counts are recomputed from saved records.

| ID | Control | Destination and exact count/filter | Result/error |
|---|---|---|---|
| S02-A01 | Overdue services / View all | S03 Due services, due date before today. Count = active plan obligations, including those already booked. | Selecting an individual row opens S13. Zero says No overdue services. |
| S02-A02 | Due soon / View all | S03 Due services, today through the configured horizon. Count includes Due today, unlike the vocabulary's separate Due soon badge. | Card subtitle states the date range so the counting convention is visible. |
| S02-A03 | Booked visits / View all | S03 Visits, Booked, today through next seven calendar days inclusive. Count = visits, not machines. | Passed appointments get a separate warning link using the same action to Past booked filter. |
| S02-A04 | Unfinished visits / View all | S03 Visits, Working, any date. Count = all unfinished visits, including historical-entry drafts. | Row opens S20; a correction-draft subrow opens S26 and is labelled separately. |
| S02-A05 | Follow-ups / View all | S03 Follow-ups, Open with due date today or earlier. Separate Contact/Corrective subtotals, total counts tasks. | Row opens S27. “All open” link within card includes future tasks. |
| S02-A06 | New visit | S18, Start now mode with no assumed customer. | Cancel creates nothing. |
| S02-A07 | Add first customer | Empty directory | S07. | Removed once a customer exists; no permanent duplicate navigation section. |
| S02-A08 | Search | S04. | Returns to Home. |
| S02-A09 | Settings | S30. | Returns to Home. |
| S02-A10 | Backup/recovery notice | S33 for missing/stale backup; D01 for recoverable form; S35 result for restored incomplete data. | Notices state what needs attention, not “Everything secure.” |
| S02-A11 | Enable/check reminders | D10/S32 when delivery is disabled or blocked. | Dismiss explanation for this session; work remains accessible. |
| S02-A12 | Work / Customers | S03 / S05 primary navigation. | U1 session state retained. |

Summary counts are not additive: the same obligation can be overdue and booked; a working visit can also have an open corrective task. No revenue, performance, utilization, or invented completion percentage appears. Read failure leaves a visible error instead of zero counts.

### S03 — Work lists

**Purpose/entry/back:** Primary work hub or a precisely filtered Home/detail link. Back returns to its caller or exits at primary root. Tabs: Due services, Visits, Follow-ups. Uses U1, D02, D05, and D13 where invoked.

**Due-service rows:** Plan name; equipment/name/identifier; customer/site; due date and relative state; Booked/Working badge and linked date. Paused/Ended plans appear only through explicit All plans/state filtering and are not counted as due obligations.

**Visit rows:** Customer/site; booked or actual service date; time/zone if booked; machine/item counts; Booked/Working/Finalized/Cancelled; partial-results or Void label where relevant. Draft rows show last saved time. A passed booking is not marked missed without the technician's action.

**Follow-up rows:** Contact/Corrective type; title; customer/site/equipment scope; due date; state; related finding/visit and booked badge when present.

A template-usage link from S16 adds an inherited Template scope chip to the Due tab, labelled with the chosen template. S03-A03 includes Clear template scope when that chip is present; the normal filter menu is not a second template-management screen.

| ID | Control | Available when | Result/change | Cancel/error behavior |
|---|---|---|---|---|
| S03-A01 | Due services / Visits / Follow-ups | Always | Switch list and retain each tab's session filter/scroll. | No business mutation. |
| S03-A02 | Search / Clear | Every tab | Local search in displayed entity/context names, references, identifiers. | No results offers Clear search/filters. |
| S03-A03 | Due filters | Due tab | State: Active/Paused/Ended/All; range: Overdue, Today, Today through horizon, Upcoming, All dates, Custom from/to; booking: Any/Unbooked/Booked/Working; optional customer/site via D02. Default Active, overdue through horizon, Any booking. | Apply validates date range. Cancel retains old filters. Clear restores defaults. |
| S03-A04 | Due sort | Due tab | Due date oldest first (default), Customer then site, Equipment name. Stable reference breaks ties. | View-only. |
| S03-A05 | Visit filters | Visits tab | State: Booked/Working/Finalized/Cancelled/All; date: Next seven days, Past booked, All dates, Custom range; customer/site. Default Booked+Working, all dates, grouped Working then upcoming then passed bookings. Voided included only with Finalized/All and visibly marked. | Clear restores default; no forced cancellation of past bookings. |
| S03-A06 | Visit sort | Visits tab | Date ascending, Date descending, Last edited. Default upcoming booked ascending and Working by most recently edited. | Labels identify which date is used: appointment for booked, actual for final, chosen service date for working. |
| S03-A07 | Follow-up filters | Follow-up tab | Type: Both/Contact/Corrective; state: Open/Resolved/Cancelled/All; dates: Due and overdue/Next seven days/All/Custom; optional customer/site/equipment. Default Open, All dates. | Clear restores default. |
| S03-A08 | Follow-up sort | Follow-up tab | Due date oldest first (default), Customer/site, Recently updated. | View-only. |
| S03-A09 | Open row / linked booking badge | Any tab | Plan → S13; booked/cancelled visit → S19; working → S20; final → S25; follow-up → S27. Booking badge opens the actual linked visit. | Unavailable target uses U1; never reassigns by name. |
| S03-A10 | Select services | Active due rows | Select one or more obligations at one site. Shows selected count and Clear selection. | Another-site selection explains the boundary; current selection is preserved. Claimed obligations offer Open existing visit rather than a second claim. |
| S03-A11 | Book selected / Start selected | Valid due selection | S18 with context and work staged. Booking/start is committed only there. | Returning cancelled leaves due dates untouched. |
| S03-A12 | Contact selected site | Valid same-site selection | D05 with the selected work as reference; no contact record yet. | U4 external-handoff rule. |
| S03-A13 | New visit / New follow-up | Corresponding tab | S18 / S28. | No blank entity before Save/Start. |
| S03-A14 | Home / Customers / Settings | Root controls | S02 / S05 / S30. | U1. |

No bulk Mark complete, swipe-to-fulfill, arbitrary service postponement, or calendar grid. The due-date filter and booking-date filter are deliberately separate.

### S04 — Search

**Purpose/entry/back:** Search icon on Home and Customers; return to exact caller. It is a lookup screen, not an analytics or saved-query subsystem.

**Inputs/content:** Search text; entity chips All/Customers/Sites/Equipment/Plans/Visits/Follow-ups; Include archived/retired/ended switch Off. Matching fields are names, stable references, make/model, serial/internal identifiers, site address, and follow-up titles. It does not OCR photographs, search PDF bytes, or ingest communication history. Results are grouped by entity with parent context and status.

| ID | Control | Result | Cancel/error behavior |
|---|---|---|---|
| S04-A01 | Search text / Clear | Filter locally after input; an empty query shows instructions, not every historical record. | Read failure offers Retry. |
| S04-A02 | Entity chips / Include inactive | Change result scope; exact matches before partial matches, then name/reference. | View-only. |
| S04-A03 | Result row | Open its S06/S08/S10/S13/S19/S20/S25/S27 destination. | U1 for unavailable target. |
| S04-A04 | Back | Restore prior destination. | Query retained in current session only. |

### S05 — Customers and equipment directory

**Purpose/entry/back:** Third primary destination. Two views, Customers and Equipment. Sites remain within their customer and global search. Uses U1/U6.

**Content:** Customer rows show name, contact summary, active-site and in-service equipment counts. Equipment rows show profile thumbnail or generic icon, name/identifier, customer/site, and nearest active due date; No active plan is explicit. Zero plans is not a fabricated due date. Returning from a completed import may add a visible This import batch scope chip; S05-A03 offers Clear import scope. It filters the records created by that confirmed import, not an undocumented persistent saved query.

| ID | Control | Result | Cancel/error behavior |
|---|---|---|---|
| S05-A01 | Customers / Equipment | Switch view. | Session state retained. |
| S05-A02 | Search / Clear | Customers: name/reference/contact; Equipment: name/reference/type/make/model/serial/internal ID/customer/site. | No results provides Clear. |
| S05-A03 | Filters | Customers: Active/Archived/All. Equipment: In service/Retired/All; customer/site; plan state Any/Has active plan/No active plan. | Default active/in service; changing a filter never reactivates a record. |
| S05-A04 | Sort | Customers: Name (default), Recently added. Equipment: Name (default), Next due earliest, Recently added. Missing due dates sort after dated records. | Stable reference breaks ties. |
| S05-A05 | Customer / equipment row | S06 / S10. | U1. |
| S05-A06 | Add customer / Add equipment | S07 / S11. Equipment creation first selects or creates a customer/site. | Cancel creates nothing. |
| S05-A07 | Global search / Settings | S04 / S30. | Return here. |
| S05-A08 | Home / Work | S02 / S03. | U1. |

## D2. Customer, site, equipment, and plan records

### S06 — Customer detail

**Purpose/entry/back:** S05/search/relationship link. Shows the current customer, not a historical report snapshot. Back to caller.

**Content:** Name and reference; optional main contact; internal notes labelled Private — not on reports; default site; site list with equipment counts; due services, visits, follow-up and history counts scoped to this customer. Archived banner hides new-work actions.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S06-A01 | Edit customer | Active → S07; archived must first be restored. | U2/U3. |
| S06-A02 | Contact | D05 for current main contact; missing details point to Edit. | No inferred interaction. |
| S06-A03 | Site row / Add site | S08 / S09 with customer fixed. Add only while active. | New site Save returns here. |
| S06-A04 | Equipment / Due services / Visits / Follow-ups | S05 or S03 with the named customer filter and visible scope chip. | Counts use each list's stated entities; clearing scope is explicit. |
| S06-A05 | History | S40, customer scope. | Historical site/equipment identity retained. |
| S06-A06 | New visit / Record contact / Add follow-up | S18 / S29 / S28. Default site preselected when available. | Disabled for archived customers; viewing remains available. |
| S06-A07 | Archive / Restore customer | D03 under R07; reason required for archive. | No automatic archiving of children or cancellation of work. |
| S06-A08 | Delete unused customer | Only when no history/dependencies; D03 lists any empty default site also removed. | Otherwise absent; archive is the safe alternative. |
| S06-A09 | Add first equipment | Active customer with no equipment; S11 through the selected/default site or D02 when a site must be chosen. | No equipment is created until S11 Save. |

**Empty state:** No machines yet offers Add equipment through the selected/default site. No sites offers Add site. There is no customer merge, sales stage, debt balance, or billing screen.

### S07 — Customer editor

**Purpose/entry/back:** New customer from S01/S05/D02, or edit S06. Uses U2/U3/D01; Back returns to actual caller after deciding unsaved edits.

**Fields:** Customer name Required; contact name, phone, email Optional; internal notes Optional. For creation only, a compact First site group has Site name default Main site, address Optional, and uses customer contact. A site name is required; address can be genuinely unknown. The first site becomes default. Edit does not duplicate site fields: it links to S09.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S07-A01 | Save customer | Validate; creation saves customer and first site together; return S06 or D02 with new customer selected. Edit updates current details only. | Similar normalized name/contact opens D13; write failure keeps input. |
| S07-A02 | Choose phone / Choose email from contacts | Open a selected-data picker for that field; copy the chosen value into this form only. No address-book sync. | Cancellation/unsupported handler leaves value unchanged; manual typing remains. P06. |
| S07-A03 | Edit site details | Existing customer only → S09 after saving/discarding customer edits. | Never changes both records implicitly. |
| S07-A04 | Cancel / Back | D01 for changed input. | Existing customer remains unchanged; a cancelled new form creates no customer/site. |

References are generated and read-only after creation. Contact edits affect future contact actions; existing working snapshots and finalized reports do not silently refresh.

### S08 — Site detail

**Purpose/entry/back:** Customer, search, or a visit context. Shows current site with its owning customer link and Archived banner when applicable.

**Content:** Site name/reference/address; whether contact is inherited or site-specific; private access notes; Default site indicator; equipment; counts/links for due work, booked/working visits, follow-ups, and history. No map is embedded or automatically loaded.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S08-A01 | Customer breadcrumb | S06. | Does not change ownership. |
| S08-A02 | Edit site | Active → S09. | U2/U3. |
| S08-A03 | Contact / Open map | D05. | Missing contact/address offers Edit or Copy available information. |
| S08-A04 | Make default site | Active site of active customer; switch the default and show Saved. | Previous default remains an ordinary active site. No history/report change. |
| S08-A05 | Equipment row / Add equipment | S10 / S11 scoped to site. | Retired rows only when Show retired switch is on. |
| S08-A06 | Due services / Visits / Follow-ups | S03 with site scope. | Counts and filters visible. |
| S08-A07 | History | S40 site scope. | Includes work originally performed here even after equipment moves. |
| S08-A08 | Book visit / Start visit / Record contact / Add follow-up | S18 / S29 / S28 with context. | New current work unavailable while site/customer archived. |
| S08-A09 | Archive / Restore site | D03, R07. | All blockers explicit; no cascade. |
| S08-A10 | Delete unused site | D03 only when entirely unused. | If default, require another active site choice or explicitly leave customer without a default. |
| S08-A11 | Show retired equipment | Switch Off by default; reveal or hide retained retired rows at this site. | View-only; never returns equipment or plans to service. |

### S09 — Site editor

**Purpose/entry/back:** S06/S08/D02. Uses U2/U3/D01. Customer Required, preselected where possible; Site name Required; Service address Optional; Use customer's main contact switch On by default. Switching Off exposes optional site contact name/phone/email; those fields are then the site contact, not a silent mixture of two recipients. Access notes Optional and always internal.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S09-A01 | Select customer | D02 on new site, or entirely unused existing site only. | Locked once equipment/history/work references exist; R07 explains move-equipment alternative. |
| S09-A02 | Use customer's main contact | Select inherited contact or independent fields. | Switching does not delete staged custom text until Save; hidden custom contact is not used in handoffs. |
| S09-A03 | Choose phone / Choose email | Selected-data picker as S07-A02, for site-specific fields. | No broad contacts access; manual fallback. |
| S09-A04 | Save site | Save and return S08 or caller selector. For eligible reassignment, confirm old/new customer and default-site effects. | Duplicate same-customer site name warns and requires a distinct name; no address-based merge. |
| S09-A05 | Cancel / Back | D01. | Existing site unchanged. |

Address changes update future map actions and new-visit snapshots, not past addresses. Missing address is permitted but reported during booking/report review and disables navigation until provided.

### S10 — Equipment detail

**Purpose/entry/back:** Directory/search/site/work link. Back to caller. Uses U1/U6/D04.

**Content:** Name/reference; current customer/site; type; make/model; serial/internal identifier or Not supplied; profile photo/generic icon; internal notes; In service/Retired state; service-plan rows with separate due dates/status; latest dated machine observation from a nonvoid record; open corrective count; full history. Latest observation and unresolved tasks remain distinct.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S10-A01 | Edit equipment | In service → S11. Retired equipment must be returned to service before operational edits. | Current edits do not rewrite captured history. |
| S10-A02 | Site/customer links | S08/S06. | Current location explicitly distinguished from historical entries. |
| S10-A03 | Photo | D04 read/view mode; replacing profile photo occurs via S11. | Missing copy offers recovery. |
| S10-A04 | Plan row / Add service plan | S13 / S14. Add only in-service equipment at active parent site/customer. | No plan is auto-created from equipment type. |
| S10-A05 | Show paused/ended plans | Reveal retained inactive plans; default hidden beneath an explicit count. | No change to due-work totals. |
| S10-A06 | Book / Start visit / Record past visit | S18 with machine fixed, appropriate mode; select plan work or a one-off line. | Historical mode can reference retired equipment, but never activate its schedule. |
| S10-A07 | Follow-ups / Add corrective follow-up | S03 scoped / S28 Corrective mode. | New corrective task requires active context; historical issues remain viewable. |
| S10-A08 | History | S40 equipment scope across all previous sites/customers, with origin shown on every entry. | No relabelling history under the new owner. |
| S10-A09 | Move equipment | S12 under R07. | Blocked by open visit/follow-up dependencies until explicitly dealt with. |
| S10-A10 | Retire / Return to service | D03; retirement reason required. Returning does not resurrect ended plans. | Parent must be active; blockers listed. |
| S10-A11 | Delete unused equipment | D03 only if no plan, visit, contact/follow-up/history/move references. | Otherwise retirement, not hard deletion. |

No calculated health score, regulatory condition, manufacturer lookup, GPS tracker, inventory valuation, or automatic serial scan.

### S11 — Equipment editor

**Purpose/entry/back:** S05/S08/S10/D02. Uses U2/U3/D01/D04/D13.

**Fields:** Site Required via D02; Equipment name Required; Type Optional free text; Make Optional; Model Optional; Serial number Optional; Internal identifier Optional; Profile photograph Optional; Internal notes Optional. Site is fixed when editing an existing machine; relocation has its own consequences in S12. A generated reference exists even when both identifiers are blank.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S11-A01 | Choose site | New equipment only; D02 customer/site selection or creation. | No choice means Save disabled. |
| S11-A02 | Add/change/remove photograph | D04, staged in form. | Cancel leaves previous image intact; temporary unused copy cleaned after discard. |
| S11-A03 | Save equipment | Create/update current properties; return S10 or selector. | Exact normalized nonblank serial/internal-ID match warns across the dataset; similar name+model at site also warns. D13 permits genuinely separate machines, never silently merges. |
| S11-A04 | Cancel / Back | D01. | No partial equipment row or permanent orphan photograph. |

No serial required, fabricated default identifier, hidden “unknown machine” placeholder, or arbitrary type catalog. Duplicate detection cannot prove that two machines are the same; the technician makes the decision.

### S12 — Move equipment

**Purpose/entry/back:** S10 Move; Back returns to equipment. Uses D02/D03/U4. This is not a name/address edit.

**Content/inputs:** Equipment identity, old customer/site, Destination site Required, effective move date Required default today and not future, Reason Required. List carried plans and their exact next dates/states. Acknowledgement Required: “Carry these plans and dates to the new site; retain history at the original site.” A historical move date adds an explanatory entry; it does not retrospectively reassign intervening visits.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S12-A01 | Choose destination | D02 active site of active customer; same-site selection invalid. | Can add a destination site normally; its creation does not itself move equipment. |
| S12-A02 | Inspect blockers | D03 for any Booked/Working visit or Open equipment-linked follow-up. | User must finish, remove, resolve, or cancel those explicitly first. |
| S12-A03 | Confirm move | All dependencies cleared; change current site, retain plan dates/states, add move entry with both contexts, refresh due/contact/navigation context. | No history/PDF mutation. Failure moves nothing. |
| S12-A04 | Cancel | Return unchanged. | Carry acknowledgement alone commits nothing. |

Cross-customer movement has an additional visible warning: prior customer reports remain associated with the original customer; new visits use the new customer. No automatic transfer of old access notes, bookings, follow-ups, or customer agreements.

### S13 — Service-plan detail

**Purpose/entry/back:** Equipment, due list, search. Back to caller. Shows plan name/reference; machine/context; interval; current due date; Active/Paused/Ended; last in-app fulfilled service, separately labelled supplied earlier service; template and current reusable revision; linked Booked/Working visit; schedule-change and fulfillment history.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S13-A01 | Edit plan | S14 unless a Working item claims it. | Working blocker offers Open visit; no mid-inspection interval/template replacement. |
| S13-A02 | Book / Start service | Active and unclaimed current obligation → S18. | Already claimed offers S13-A03, not a duplicate booking. |
| S13-A03 | Open linked visit | S19 or S20. | Link retains original due-date identity; no state change. |
| S13-A04 | Contact / Record contact / Add contact follow-up | D05 / S29 / S28 with plan/equipment context. | No due change. |
| S13-A05 | Template | S16; archived assigned template is visibly labelled and remains usable at its frozen latest revision. | Missing template data is an error, not a silent empty checklist. |
| S13-A06 | Pause plan | D03/D06: clear Booked/Working claims first; required reason. Retain due date; stop active due/reminder participation. | Unresolved corrective follow-ups remain open. |
| S13-A07 | Resume plan | Paused → D06; show retained due date and explicit Keep current or entered replacement date+reason. | Retained overdue date immediately becomes overdue again. |
| S13-A08 | End plan | Active/Paused, no Booked/Working claims → D03/D06 with reason. Stop future obligation without asserting completion. | Ended is not a toggle; create a new plan for a new commitment. |
| S13-A09 | Record past service | S18 historical mode; Active/Paused/Ended link allowed, default History only. | No automatic resumption/advancement. |
| S13-A10 | Delete unused plan | Only never referenced by visit/contact/follow-up/history other than setup metadata; D03. | Otherwise End plan. |
| S13-A11 | Machine / History entry | S10 / S25 or read-only schedule-change entry. | No detached “last service” editing of final records. |

### S14 — Service-plan editor

**Purpose/entry/back:** S10 Add or S13 Edit. Uses U2/U3/D01/D02. Machine fixed. Fields: Service name Required; Every Required positive whole number, default 6; Unit Required Days/Weeks/Months/Years, default Months; Initial/current next-due date Required, initially blank; Earlier service date Optional and not future; Earlier-service source/note Optional; Inspection template Optional, default None. Blank earlier service means unknown, not never serviced.

When an earlier service date and interval are present, show a suggested next date with a Use suggestion button; never treat a supplied historical date as an in-app completed visit. Next date must be later than the supplied earlier date on setup. Duplicate Active/Paused plan names on the same machine require a distinct name; ended names can be reused on a new plan.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S14-A01 | Choose/clear template | D02 or None. | Clearing affects new work only after Save, not existing captured checklists. |
| S14-A02 | Use suggested due date | Fill completion/supplied-date-plus-interval result; still requires Save. | No known date means no suggestion. |
| S14-A03 | Interval or due-date fields | Stage new values. Existing plan changes display before/after; a changed live due date requires a Reason for date change. Changing interval alone does not move its current due date. | Any unsupported/overflow calculation or live due date not later than the latest valid counted completion (or setup baseline before the first completion) blocks Save with a field error. |
| S14-A04 | Save plan | Create current obligation or update shown properties; log explicit schedule changes. Return S13. Booked items retain the appointment but show Plan changed — review at start. | Working claims block material edits until removed/finalized. No guessed completion history. |
| S14-A05 | Cancel / Back | D01. | Dates/reminders remain as previously saved. |

Once actual in-app fulfillments exist, their last-service date is read-only here; the supplied earlier-service date/source are also locked as original setup information. Before that point they remain editable with visible before/after consequences. Neither is a way to edit a finalized record. The form exposes no runtime, combined rule, weekday recurrence, holiday calendar, or fixed-anniversary mode.

## D3. Reusable inspection templates

### S15 — Template library

**Purpose/entry/back:** S30 or Add template selector; Back returns there. Template rows show name, item count, latest revision, archived state, and number of assigned plans. Uses U1.

| ID | Control | Result | Cancel/error behavior |
|---|---|---|---|
| S15-A01 | Search / Clear | Match template name. | No results offers Clear. |
| S15-A02 | Active / Archived / All | Default Active. | View-only. |
| S15-A03 | Sort | Name (default) / Recently changed. | View-only. |
| S15-A04 | Template row | S16. | Read failure offers Retry. |
| S15-A05 | New template | S17 with blank template. | Cancel publishes nothing. |

Empty library says that checklists are optional and offers Create template. There is no downloaded catalog, specialist compliance template pack, or AI-generated inspection claim.

### S16 — Template detail and preview

**Purpose/entry/back:** Library, plan, or selection review. Shows ordered items, type/unit, required label, guidance, current revision, and assigned-plan count. It is a preview, not a live checklist with answers.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S16-A01 | Edit | Active → S17. | Existing runs unaffected until/unless a new run captures the new revision. |
| S16-A02 | Duplicate | S17 populated with copied configuration and proposed name “Copy of …”; new identity only after Save. | Cancel creates no reusable template. |
| S16-A03 | Assigned plans | S03 Due services with All plan states and this-template scope. | Scope labelled; no new top-level template usage screen. |
| S16-A04 | Archive / Restore template | D03; archive freezes latest revision and hides new selection. Existing plan assignments continue to use it, with an archived badge. | Confirmation lists affected plan count; nothing is silently detached. |
| S16-A05 | Delete unused template | D03 only if never referenced by a plan, working checklist, or record. | Otherwise Archive. |
| S16-A06 | Use this template | Only opened by a selector; return chosen template to caller. | Hidden outside selection mode. |

Archived templates cannot be edited until restored, but may be duplicated. A newly added plan cannot select an archived template; an existing assignment can keep it until explicitly changed.

### S17 — Template editor

**Purpose/entry/back:** New/edit/duplicate from S15/S16/D02. Uses U2/U3/D01/D08. Fields: Template name Required and unique among active templates; ordered item list with at least one item; no section/branch/script designer. Each row displays Label, Type, Unit where applicable, and Required/Optional.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S17-A01 | Add item | D08. | Cancel returns unchanged list. |
| S17-A02 | Edit item | D08 for selected staged item. | No published revision until parent Save. |
| S17-A03 | Move up / Move down | Change staged order; first/last boundary control disabled with descriptive label. Optional drag duplicates these controls. | No historical checklist reordering. |
| S17-A04 | Remove item | Confirm removal from this proposed reusable version. | Existing revisions and responses remain intact. |
| S17-A05 | Preview | Read-only staged item preview within S16 presentation, then return editor. | No template publication. |
| S17-A06 | Save template | Validate all items; create new template or next revision. Show how many plans use it and that new working items will capture this revision. | Existing Working/final checklists unchanged. Failed save retains draft. |
| S17-A07 | Cancel / Back | D01. | Discard staged changes, not past revisions. |

**Response policy previewed here:** Required means a value/explicit applicable disposition is necessary to mark the checklist Reviewed, not to save or finalize an incomplete visit. A required Status item can be OK, Issue found with description, or Not applicable with reason. Not checked never meets the requirement. Required Text/Number permits a value or Not applicable with reason; empty is Not recorded. Optional unanswered fields remain explicitly unrecorded. There are no numeric thresholds or automatic pass/fail/safety diagnoses.


## D4. Booking, fieldwork, completion, and corrections

### S18 — Visit setup and booking editor

**Purpose/entry/back:** New visit from Home/Work/context; edit Booked visit from S19; past-entry action from equipment/plan/history. Back returns to caller using U2/U3/D01. Nothing is booked or started merely by selecting a machine.

**Modes:** Book a visit; Start now; Record past visit. Changing mode preserves compatible staged selections and shows any dates/reminders that cease to apply.

**Fields:** Customer and Site Required through D02; one or more Equipment/work-item selections Required. For each machine select one or more eligible plans and/or Add one-off service. A one-off has Service title Required, default One-off service, and optional template. Date Required: booking date in Book mode, actual service date in Past mode, today in Start mode. Booking start time Optional; estimated duration Optional positive whole minutes when time exists, default 60 when first enabled; overnight bookings are not included. Appointment note Optional/Internal. Reminder: Use app default/Off/2 hours before/1 day before; timed alerts unavailable without a time. Linked follow-ups Optional, selected from the same site/equipment context and remaining Open until explicitly closed later.

Historical mode permits inactive records and explicitly confirmed historical equipment/site associations. It never changes the machine's current location. A former-site entry is History only; current scheduling can be adjusted separately and visibly if necessary. Current modes require active customer/site and in-service equipment at that site.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S18-A01 | Mode | Select Book/Start/Past. Editing an existing Booked visit remains booking mode; Start is a separate action on S19. | Never changes an existing visit state merely by switching a field. |
| S18-A02 | Choose customer/site | D02; default site preselected when unambiguous. | Changing site requires clearing incompatible work/follow-up selections; confirmation lists them. |
| S18-A03 | Add/remove machine | D02, then choose work items. Multiple machines allowed at this site. | Removing a staged selection here removes no history. |
| S18-A04 | Select plan / Add one-off / Edit one-off | Stage named lines. A plan appears once per visit. Plan already claimed elsewhere offers Open existing visit; paused/ended allowed only as History-only links in Past mode. | Invalid/ended/moved selections cannot be silently converted into current obligations. |
| S18-A05 | Select linked follow-ups | D02 same-context Open tasks. | Link alone does not resolve or postpone the task. |
| S18-A06 | Date / Time / Duration / Reminder | Stage appointment or actual date. Default Start actual date is today, not a stale appointment date. Changed booking date/time requires a Rescheduling reason. | Ambiguous/nonexistent local times are resolved explicitly under R08. |
| S18-A07 | Book visit / Save booking | Validate all selections and live obligation claims; create/update Booked visit and its reminder request. Date/selection changes logged. Return S19. | Overlap warning shows other timed bookings; Continue anyway or Return to editing. No automatic conflict resolution. Past booking date is disallowed on new bookings; use Past mode. |
| S18-A08 | Start visit | Start mode; save Working visit, capture work/checklist/identity snapshots, reserve selected current obligations, open S20. | Blocked selection offers correction; failure creates no half-started visit. |
| S18-A09 | Create historical draft | Past mode; date not future; capture draft and open S20 with History-only defaults. | No obligation claimed or advanced by this action. |
| S18-A10 | Cancel / Back | D01. | Existing booking unchanged; new setup creates no visit. |

A time-free booking is a day commitment, not an all-day assertion about actual labor. Overlap detection covers this app's timed bookings only, not calendars, travel, or another person's availability. No booking message is sent automatically.

### S19 — Booked or cancelled visit detail

**Purpose/entry/back:** Work list, linked plan, history, or successful booking. Back to caller. Finalized and Working visits route to S25/S20 instead.

**Content:** Booking reference; current customer/site; appointment date/time/zone/duration; planned machines and named services with their business due dates; appointment note Private; linked follow-ups; booking/reschedule/cancellation history; reminder status. A cancelled visit retains what was planned and why it was cancelled. A changed plan/template/location shows a review warning.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S19-A01 | Edit/reschedule booking | Booked → S18. | Due dates remain unchanged; reasoned appointment change only. |
| S19-A02 | Contact / Open map / Record contact | D05 / S29. | No proof of attendance/contact is inferred. |
| S19-A03 | Machine / Plan / Follow-up row | S10 / S13 / S27. | Parent context preserved on Back. |
| S19-A04 | Start visit | Booked; revalidate current selections, show changed plan/template details, confirm use of latest reusable content; default actual date today; create snapshots and open S20. | Starting before/after booked date shows explicit date notice. Ineligible items require removal/replacement in S18. |
| S19-A05 | Cancel booking | D06 with required reason; set Cancelled, release claims, remove appointment alerts, retain booking history. | Service dates and Open follow-ups unchanged; no “service cancelled” fiction. |
| S19-A06 | Create another visit from this | Cancelled only → S18 populated with still-eligible context and work, new date blank. | Old cancellation remains; unavailable items explained, never revived automatically. |
| S19-A07 | Change reminder | Booked → reminder field in S18; explicit Save required. | Cancelling preserves previous alert configuration. |

No Delete for a saved booking, no automatic no-show judgment, and no final report from a cancelled booking alone. An actual attendance with no work is started and finalized with Not performed outcomes and reasons.

### S20 — Working visit overview

**Purpose/entry/back:** Start/resume actions or Working list. Back leaves the saved Working visit available, not cancelled. Uses durable U3 autosave, D02/D04, and U4. Historical-entry mode is prominently labelled.

**Content:** Customer/site identification snapshot; planned appointment separately from actual service date; Saved on this device time; machine cards with item/checklist progress and unreviewed outcomes; linked Open follow-ups; private appointment/access notes visually separated from report content. Progress is “items reviewed,” never percent of obligations fulfilled.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S20-A01 | Machine card | S21 for that machine. | Saved visit remains open. |
| S20-A02 | Add equipment/services | D02 and S18-style work selection constrained to this site. Capture new lines' current template/plan snapshot now. | Existing lines do not refresh. Duplicate current-obligation claims blocked. |
| S20-A03 | Remove machine/service | Draft-only confirmation lists affected answers, work notes, findings, parts, photographs, and claims. Remove only selected scope after confirmation. | A machine's shared notes/photos/parts remain when another line still references that machine. At least one line required to finalize. |
| S20-A04 | Actual service date | Stage actual date, default today or chosen Past date; not future. | Backdating may make current fulfillment ineligible at review; no date advancement yet. |
| S20-A05 | Review identification | Show captured business/customer/site/equipment names/identifiers and differences from current records. Explicit Refresh from current or edit historical snapshot details; require acknowledgement. | Never changes live customer/equipment records. Origin customer/site/equipment links stay fixed after starting. |
| S20-A06 | Contact / Open map | D05 using clearly labelled current contact/address, with historical snapshot address offered for comparison. | Checkpoint before handoff; return resumes visit. |
| S20-A07 | Linked follow-ups | Open S27 read-only from the draft, or stage Keep open/Resolve later at completion. | No task closes just because it was opened. |
| S20-A08 | Review completion | S24 after successful checkpoint. | Validation errors are listed there with links; incomplete work is allowed. |
| S20-A09 | Leave visit for later | Save checkpoint and return to caller/Work. | Save failure uses D01; no Cancel label for already-saved work. |
| S20-A10 | Discard working visit | Destructive D03 confirmation with required reason and content counts. Ad hoc draft is removed; a booking-origin visit retains a Cancelled booking entry and loses its explicitly discarded working contents. Release claims. | No plan advancement or follow-up closure. Existing finalized records cannot use this action. Prefer finalize partial when actual work should be retained. |

Changing the overall customer/site of a Working visit is not supported: discard an erroneous empty draft or finalize actual partial work and start the correct visit. This prevents moving already-captured machine evidence to a different customer accidentally.

### S21 — Machine within a working visit

**Purpose/entry/back:** S20 machine card; Back returns to S20 after checkpoint. This screen contains visit-specific data, not the master equipment editor.

**Fields/content:** Captured equipment identity and reference; work-item rows; Observation Required before finalization: Not assessed (initial), No issue observed, Issue observed; Customer observations Optional/Public; Internal notes Optional/Private; findings list; parts list; photograph grid; relevant existing corrective tasks. All findings created here are intended for the customer record; private speculation belongs in Internal notes instead.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S21-A01 | Work-item row | S22 for its work note/checklist. | No outcome is inferred on Back. |
| S21-A02 | Observation | Autosave selected observation. Issue finding forces Issue observed; user cannot simultaneously claim No issue observed while retaining an issue. | Not assessed is legitimate and not reported as safe/OK. |
| S21-A03 | Customer observations / Internal notes | Autosave separate labelled text fields. | Failed saves remain visible. Private text is never copied to the public field. |
| S21-A04 | Add/edit finding | S23 with machine and optional checklist origin fixed. | Cancel of a new editor creates no described finding; incomplete auto-created checklist finding remains flagged for attention. |
| S21-A05 | Add/edit part | D09. | No stock/price consequence. |
| S21-A06 | Add/view photos | D04; new photos default excluded from customer report. | Photos must be locally copied and acknowledged before considered saved. |
| S21-A07 | Existing corrective follow-up | S27; optionally link it to the visit via explicit selection. | Does not resolve it or change due date. |
| S21-A08 | Equipment history / Master equipment | S40 / S10 with a clear return-to-visit path. | Master changes do not silently alter the captured identity. |
| S21-A09 | Back to visit | Checkpoint then S20. | D01 on failure; durable saved work remains. |

No machine receives a global Healthy badge just because an inspection was completed or one corrective task was resolved. Equipment detail displays dated observations and independently counted open corrective tasks.

### S22 — Work item and checklist

**Purpose/entry/back:** S21 named plan or one-off line. Back returns to machine. Shows captured service title, plan/reference and due date if applicable, interval snapshot, template name/revision, and save state.

**Inputs:** Work performed Optional while working, required for Performed/Partly performed outcome at final review. No checklist is a valid state. With a checklist, ordered questions retain original labels, guidance, types, units, and required flags. Status starts Not checked; text/number starts blank/Not recorded. Numeric readings accept finite signed decimal values with the captured unit; no automatic threshold judgment. Text/number can be marked Not applicable with a required reason.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S22-A01 | Work performed | Autosave public work note. | Saving note does not fulfill obligation. |
| S22-A02 | OK / Issue found / Not applicable / Not checked | Status item. Save response; Issue found creates/links a finding editor S23; Not applicable requires reason; Not checked may carry optional reason, required when explaining an incomplete required item at finalization. | Draft may remain incomplete; no invented answer. |
| S22-A03 | Text / Number value | Save response; number unit is fixed from snapshot. | Invalid numeric text remains a draft error, not a coerced zero. |
| S22-A04 | Mark not applicable / Enter value instead | Text/number item; capture reason or deliberately return to value entry. | Changing disposition clears incompatible staged value only after confirmation. |
| S22-A05 | View/edit linked finding | S23. | Report review blocks an Issue found response with no description/disposition. |
| S22-A06 | Mark checklist reviewed | All required items accounted for, issue details present; show answered/NA/not-checked counts and explicitly acknowledge review. Set Reviewed. | Optional unchecked items remain labelled unchecked. Editing any answer afterward returns checklist to In progress until reviewed again. |
| S22-A07 | Choose/change checklist | One-off item only, before responses exist; D02 template selection, then capture snapshot. | Once answered, replacement requires explicit removal/recreation of the work item; existing answers are not guessed into new questions. |
| S22-A08 | Keep finding / Remove draft finding | When changing an Issue found answer to another status, show linked finding consequences; retain as independent finding or confirm removal. | Repairing a real issue should normally retain Issue found and mark its finding Resolved during visit, rather than rewriting the observation to OK. |
| S22-A09 | Back | Save checkpoint; return S21. | D01 on failure. |

A Reviewed checklist can contain problems, legitimate NA results, and unanswered optional items. Required Not checked/Not recorded prevents Reviewed, but never prevents recording Partly performed/Not performed work. No bulk “all OK,” autofilled prior readings, cross-plan answer reuse, or automatic machine safety verdict.

### S23 — Finding editor

**Purpose/entry/back:** S21 or an Issue found checklist answer. Uses explicit U2/U3 save within the autosaved visit. Machine and source item are fixed. New generic finding starts as a form; a checklist-generated placeholder is visibly unfinished until its description is saved.

**Fields:** Kind Required Observation/Issue (Issue fixed for a checklist Issue found); Description Required/Public; Disposition Required Resolved during visit/Corrective follow-up needed/No further action; resolution/work note Required when resolved; reason Required for no further action. Optional photo links select existing machine-visit photos or add through D04. When follow-up needed, choose New corrective follow-up or Link existing open corrective task for this exact machine. New task requires title, date, and description, staged through S28.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S23-A01 | Disposition | Stage its required explanatory fields and task option. | Changing away from follow-up removes only an uncommitted new-task proposal after confirmation; never deletes an existing task. |
| S23-A02 | New corrective follow-up | S28 staged-child mode with source finding/machine. | Child action returns a proposal; task becomes live only at finalization. |
| S23-A03 | Link existing corrective follow-up | D02 Open tasks for this machine; stage link. | No state/due change to existing task. |
| S23-A04 | Select/add photographs | D04; link saved local images to finding. | Photograph report inclusion remains separately Off until selected. |
| S23-A05 | Save finding | Validate current inputs and save to Working/correction draft; return caller. Set machine observation Issue observed for an Issue. | Follow-up proposal is not a live task yet. Failed write retains editor. |
| S23-A06 | Remove finding | Draft only; confirm effects on linked checklist answer and uncommitted follow-up proposal. A linked Issue answer must be changed deliberately before removal. | Existing live follow-up is merely unlinked, never cancelled automatically. |
| S23-A07 | Cancel / Back | D01. | Previously saved finding retained. |

Descriptions/dispositions are report content. “No further action” is the technician's recorded judgment, not app endorsement, and it does not certify equipment safety.

### S24 — Completion review

**Purpose/entry/back:** S20 Review completion. Back returns to Working visit; review choices autosave in that draft. They are not business completion until Finalize record.

**Visible sections:** Identification and actual date; each machine's observation; each service line's checklist/work/outcome; selected current obligation and proposed next date; findings and task proposals; existing linked follow-ups; customer report contents; blockers/warnings. Each warning opens its actual source screen/action.

**Required per service line:** Outcome Performed/Partly performed/Not performed (initially Choose outcome); work performed note for first two; reason for Not performed and for required work left incomplete. **Fulfills current obligation** is a separate, initially unchecked choice. Only Performed with a Reviewed assigned checklist (or no checklist) is eligible. A problem found during an inspection does not itself prevent fulfillment of the inspection obligation.

For an eligible current fulfillment, proposed next date = actual date + captured interval. It must be after actual service date, may be in the past relative to today, and can be overridden with a reason. If already past, warn that the plan will remain overdue. Historical-entry lines default to History only; applying one to the current obligation is possible only under R03, never for a former-site/inactive-plan entry.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S24-A01 | Edit date/identification/machine/work | S20/S21/S22 at the relevant field. | Return to review after save; edited answers invalidate prior Reviewed state. |
| S24-A02 | Outcome per line | Stage Performed/Partly/Not performed and required notes. | No global Mark all complete. Performed does not automatically check fulfillment. |
| S24-A03 | Fulfills current obligation / History only | Eligible plan line only. Stage explicit fulfillment or recorded work without schedule change. Show exact old due date, last counted completion, and proposed next date. | Ineligible choice disabled with reason and route to linked booking/plan/record; never silently applied to a newer obligation. |
| S24-A04 | Next due date / Use calculated date | For staged fulfillment, inspect or override calculated date; override reason required. | Other plans on same machine unchanged. Invalid date blocks finalization. |
| S24-A05 | Corrective task proposal | Review/edit via S28; link existing via D02. Every Follow-up needed finding must have one valid proposal/link. | Duplicate new-task proposals can be deliberately combined for findings on the same machine by choosing the same staged task. No cross-machine corrective task. |
| S24-A06 | Existing linked follow-up: Keep open / Resolve | Default Keep open. Resolve requires outcome note/date via D07 and actual relevant work. | No task closes until finalization. A kept-open task retains its own due date. |
| S24-A07 | Select report photos | D04 on this visit's saved photos; Off by default; preview selected captions. | Internal notes/access notes are not selectable. Unselected photos remain in private visit history. |
| S24-A08 | Draft report preview | S39 DRAFT mode from staged public snapshot. | No external Save/Share in draft mode. It never includes unfinished private fields to fill gaps. |
| S24-A09 | Business profile | S31 when report identity is missing; on return explicitly refresh the draft's identity snapshot. | Finalization requires a technician name and business/display name; logo/contact/address remain optional. Fieldwork can be saved without these. |
| S24-A10 | Finalize record | All blockers cleared and saved. Atomically fix record revision, consume only checked eligible obligations, write next dates, create selected tasks, apply explicit task closures, release remaining claims, set visit Finalized, stop appointment alert; open S25. | Write failure leaves Working draft and schedules/tasks unchanged. Retry after a committed result opens that result instead of repeating effects. |
| S24-A11 | Back to working visit | S20 after checkpoint. | Review selections remain draft choices, not finalized obligations. |

**Finalization does not require every service to be performed.** All Not performed with an attendance reason is a valid record. A partly performed maintenance plan retains its original due date and becomes unbooked/overdue as applicable. A corrective follow-up supplements that obligation; it never replaces it. PDF generation is subsequent and cannot undo completed business recording when it fails.

### S25 — Final service record

**Purpose/entry/back:** Finalization, Work/history/search. Read-only current record revision; Back to actual caller. Uses U1/D04. Shows visit reference, actual date, recorded-on timestamp, origin customer/site/equipment snapshots, all item outcomes, which obligations counted, old/new dates, findings, checklist content, parts, and private notes behind an explicitly internal section.

Also shows original versus current linked-task states separately; report versions and sharing notes; correction/void history; unresolved file-integrity warnings. Old report text is never updated merely because a follow-up was later resolved.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S25-A01 | Generate customer PDF / View report | No generated current PDF → generate from fixed public snapshot; success → S39. Existing → S39. | PDF failure keeps final record and schedule intact; Retry supported. No silent photo omission. |
| S25-A02 | Record versions / Report versions | Select a labelled record revision or PDF version; show current/superseded/voided status. | Opening old version does not make it current. |
| S25-A03 | Correct record / Resume correction | S26 with a new or existing durable correction draft. | Main record remains authoritative until correction commits. |
| S25-A04 | Void this record | S26 Void mode; reason and downstream reconciliation required. | Not hard deletion. Previously exported files remain outside control. |
| S25-A05 | Follow-up / Plan / Equipment / Original site/customer | S27/S13/S10/S08/S06. Current entity screens label any changed identity/location. | No rewrite of snapshot. |
| S25-A06 | Create return visit | S18, same active site, propose still-open obligations and corrective tasks. | Does not copy Performed outcomes, answers, or completed obligations into a new visit. |
| S25-A07 | Add contact/sharing note | S29 with record/report version reference. | Manual technician statement only, no automatic Delivered flag. |
| S25-A08 | View retained photograph | D04 read-only, including unselected internal photographs. | Missing file offers Locate original/Restore guidance, never fabricated recovery. |

A voided record is shown with a prominent warning and excluded from valid completion/history summaries; its evidence remains accessible. New routine sharing of a voided/superseded original requires the warning in S39. Only S26 can change finalized content.

### S26 — Correction and voiding workspace

**Purpose/entry/back:** S25. Creates one durable correction draft per visit, or opens Void mode. Back leaves draft without altering the current record. Uses U3/D01/D04/D07; no ordinary Cancel that appears to undo autosaved draft changes.

**Inputs/content:** Reason Required; current record revision; proposed changes; editable historical identity text (not entity links), actual date, public/internal notes, answers, work outcomes, parts, findings, and selected photos. Existing reusable templates are not substituted. New evidence is labelled Added in correction with its added-on date, not falsely shown as captured on the service day. Current customer/site/equipment/plan links are fixed; a genuinely wrong subject requires voiding and recording the correct visit separately.

The second section is **Schedule reconciliation**, one row per affected plan: old record's effect, current plan state/date, later fulfillments or manual changes, proposed retained/new date, and why. The third lists affected follow-ups and report versions. See R06 for the sole policy.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S26-A01 | Edit captured fields/outcomes | Reuse S21–S23 presentations in correction mode; autosave proposed revision only. | Cannot change original entity links or silently use a new template. A date crossing another valid counted completion cannot retain this line's counted fulfillment; show the R06 history-only/reconciliation choice. |
| S26-A02 | Reconcile affected plan | For latest relevant completion, explicitly confirm next date or retain a later/manual schedule with explanation; show before/after. Older records with later valid fulfillment are History change only. | Working/Booked downstream dependencies block schedule-changing commit until resolved; text-only correction can proceed. |
| S26-A03 | Affected follow-up: Keep / Cancel as erroneous / Add required task | Keep is default; cancellations require reason; new required corrective task uses S28 staged mode. | Previously resolved work is never automatically reopened. Reopening is a separate explicit S27 action. |
| S26-A04 | Preview proposed report | S39 DRAFT CORRECTION or VOID NOTICE preview. | No export/share until commit. |
| S26-A05 | Commit correction / Confirm void | Validate all changes/reconciliations; save next record revision or void event and only its explicit linked effects together. Old revisions/PDF bytes retained. Current PDF status becomes Not generated for the new revision. | Failure keeps original revision live. Repeated retry returns the committed revision without advancing again. |
| S26-A06 | Leave correction for later | Return to S25; Resume correction remains available and Home marks a correction draft separately. | Checkpoint failure uses D01. |
| S26-A07 | Discard correction draft | Explicit confirmation discards staged revision only. | Original record, current plans/tasks, and existing PDFs unchanged. |

**Void output:** A newly generated customer-facing notice states that the referenced service record was voided and gives the public correction explanation. It does not rewrite old PDF bytes with a watermark and pretend those bytes were originally issued that way. A void reason has a public explanation plus optional internal detail; private reason text never leaks automatically.

## D5. Follow-ups and manual communication records

### S27 — Follow-up detail

**Purpose/entry/back:** S03/Home/context/finding/history. Uses U1/D05/D07. Shows type, title, due date, Open/Resolved/Cancelled state, customer/site/equipment, source finding/visit, current linked booking, internal task note, and dated change/closure entries. Corrective tasks show the original public finding separately from private planning notes.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S27-A01 | Edit/reschedule | Open → S28. Changed due date requires reason. | Never changes a service-plan due date. |
| S27-A02 | Contact / Open map / Record contact | D05 / S29. | Contact task does not resolve merely because external app opens. |
| S27-A03 | Book / Start related visit | Open with active site/equipment → S18; contact-only customer task first selects a real site/machine or stays a contact task. | Booking badges do not count as resolution. |
| S27-A04 | Resolve / Cancel | Open → D07. If entered from Working review, stage the result rather than closing immediately. | No implicit recurrence effect or safety claim. |
| S27-A05 | Reopen | Closed → D07 with date/reason and active context check. | Previous resolution/cancellation retained. |
| S27-A06 | Source finding/record / linked visit / context | S25/S19/S20/S06/S08/S10 as applicable. | Source content stays historical. |

No hard Delete once a follow-up is saved; cancel an erroneous task with a reason. No assignments, task categories beyond the two defined types, subtasks, arbitrary project boards, or automatic follow-up messaging.

### S28 — Follow-up editor or staged proposal

**Purpose/entry/back:** New/edit task, contact-note child, or finding/completion child. Standalone mode applies on Save; staged-child mode says Use follow-up and returns a proposal to its parent, not a live task. Uses U2/U3/D01/D02.

**Fields:** Type Required Contact/Corrective, default from context; Title Required; Follow-up date Required, blank for new standalone task; Customer Required; Site Optional for Contact, Required for Corrective; Equipment Optional for Contact, Required for Corrective; Internal task notes Optional. Corrective source finding/record links are read-only. Past dates are allowed with explicit “This will already be overdue” warning. All tasks have one date, not arbitrary recurrence.

Context is editable only for an unsaved task or a standalone Open task with no originating finding/visit or booked linkage. Changing a broader context clears incompatible narrower selections with confirmation. A saved source-derived task cannot be repointed to another customer's equipment.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S28-A01 | Type / context selectors | Stage valid scope via D02; changing type shows newly required fields. | Does not preserve invalid dangling links. |
| S28-A02 | Follow-up date | Stage date; existing-date change requires reason. | Does not change service due dates or linked booking date. |
| S28-A03 | Save follow-up / Use follow-up | Validate; standalone writes Open task and returns S27/caller; staged returns proposal, committed only with S24/S26/S29. | No task silently appears after a parent draft is discarded. |
| S28-A04 | Cancel / Back | D01 for local editor changes. | Existing task/parent's prior proposal unchanged. |

At finalization, several findings for the same machine may point to one deliberately selected corrective task. The app does not automatically merge tasks because their titles look similar.

### S29 — Manual contact or sharing note

**Purpose/entry/back:** D05/S06/S13/S19/S25/S27/S39/S40. New/edit/view modes are labelled. It is not a synchronized phone log. Uses U2/U3/D01/D02.

**Fields:** Customer Required; Site/Equipment/Plan/Visit/Report-version links Optional and constrained to actual context; Channel Required Call/Text message/Email/In person/Other; Occurred on date/time Required default now, not future; Outcome Required Attempted/no response, Spoke/exchanged messages, Follow-up requested, Appointment agreed, Other; Details Optional except Other requires explanation. Notes are internal. A note referring to a PDF states the exact version. No “verified delivery” field exists.

Optional Create contact follow-up toggle Off; enabling opens a staged S28 with context and suggested title. Booking is a separate action after saving the note, even when the recorded outcome says Appointment agreed.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S29-A01 | Save contact note | Persist technician-entered result and any staged contact follow-up together. Return to source; show Book visit action when useful. | Save failure creates neither note nor task. No due-date change. |
| S29-A02 | Add/edit follow-up proposal | S28 staged-child mode. | Cancel leaves prior proposal; removing toggle removes proposal only, never an already-live task. |
| S29-A03 | Book visit | Saved note with active context → S18. | Note remains saved if booking cancelled; no automatic Appointment sent claim. |
| S29-A04 | Edit note | Existing note → editable fields; save retains original created-on and adds Edited on. Links to already-created tasks are not rewritten from edited prose. | No silent rescheduling/closure of the task. |
| S29-A05 | Delete erroneous unlinked note | Only when no source/follow-up/booking/report reference depends on it; D03 confirmation. | Otherwise retain and correct the note with an explanation. |
| S29-A06 | Cancel / Back | New/edit → D01; view → caller. | Opening or abandoning the form records no communication. |


## D6. Settings, data ownership, reports, and history

### S30 — Settings

**Purpose/entry/back:** Gear on primary destinations; return to caller. This is the complete settings menu, not an invitation to add unspecified standard settings.

| ID | Row | Content/destination | State/failure |
|---|---|---|---|
| S30-A01 | Business and report identity | S31; current business/technician name or Not set. | Missing identity never blocks directory/fieldwork saving. |
| S30-A02 | Reminders | S32; Requested/Off and actual Android permission/channel status. | Blocked is not shown as working. |
| S30-A03 | Inspection templates | S15; active-template count. | No mandatory templates. |
| S30-A04 | Data and recovery | S33; last verified full backup and changed-since-backup status. | Missing/stale backup is visible. |
| S30-A05 | Privacy, help, and app information | S38. | Bundled help remains available offline. |

No account, sync, team, roles, invoices, theme palette, home-widget designer, analytics switch, sound catalog, report-layout builder, or developer console is implied. System appearance/text size are respected automatically, not replicated as unrelated settings.

### S31 — Business and report identity

**Purpose/entry/back:** S30, first report review, or correction identity review. Uses U2/U3/D01/D04. Fields: Business/display name; Technician name; optional business phone, email, postal address; optional report logo. Names are required before finalizing a reportable record, but this screen may be left without setup while doing earlier tasks. No tax, invoicing, payment, or company-registry fields.

**Business time zone:** Required, initialized from device on first dataset creation; searchable named-zone list; no GPS-based choice. Changing it keeps existing date values and existing timed appointments' instants/original zones. It changes the default for new appointments, the meaning of business Today, and future summary scheduling. Preview consequences before Save. It never rewrites historical report zones.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S31-A01 | Add/change/remove logo | D04 single-image mode. | Only future refreshed/new record snapshots use changed logo. |
| S31-A02 | Business time zone | Stage named zone and display current-device difference and effects. | Cancel selection retains previous zone. Existing appointments are not shifted invisibly. |
| S31-A03 | Save profile | Save valid entered details; return caller. If caller is a working/correction snapshot, offer explicit Refresh identification via S20/S26. | Saved master profile alone never rewrites an issued report. |
| S31-A04 | Cancel / Back | D01. | Existing profile unchanged. |

A technician may use the same name for business and technician if trading individually; no registered-company status is assumed. No jurisdiction or launch language is inferred from this design document.

### S32 — Reminder settings

**Purpose/entry/back:** S30/D10/Home warning. Uses U2/U3/D01/D10. Displays actual permission, each channel status, and a persistent note: “Reminders may be delayed. Work lists remain the source of truth.” P01/P02.

**Actual settings:** Request local reminders (Off initially); Daily summary (On as a stored preference); summary time (08:00 initially, business zone); summary days (Monday–Sunday, all selected initially, at least one when summary enabled); Due-soon horizon (0/7/14/30 days, default 14, also controls Home/default due filter); summary content switches Due services/Visits/Follow-ups/Unfinished visits (all On); include backup reminder when due (On); approximate appointment alerts (Off initially), default lead (2 hours or 1 day, default 2 hours).

Daily summary includes active services overdue through the horizon; Booked visits today/tomorrow plus passed unresolved bookings; Open follow-ups due today/earlier; Working visits; and a stale-backup notice when applicable. Empty categories are omitted; no notification is created for an entirely empty summary. Backup reminder age is configured in S33, not duplicated here.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S32-A01 | Request local reminders | Stage desired state; enabling invokes D10 contextually. Desired On may still show Blocked by Android. | Denial does not flip business features off; no repeated automatic request. |
| S32-A02 | Summary time/days/content | Stage the exact settings above. | Invalid day selection prevents Save; disabled rows explain parent switch. |
| S32-A03 | Due-soon horizon | Stage shared view/reminder horizon. | Changes visibility, never business due dates. |
| S32-A04 | Approximate appointment alerts / default lead | Stage preference for bookings using app default. Explicit per-visit override remains as booked. | Show affected future-booking count before applying changed default. |
| S32-A05 | Save reminder settings | Apply preferences; cancel/replace obsolete reminder requests; master Off withdraws pending app notifications. | No overdue-state, task-date, or booking-date mutation. Failure retains previous settings. |
| S32-A06 | Android notification settings | D10-A02 for app or selected Work summaries/Appointment reminders channel. | Recheck on return. Android owns channel sound/vibration. |
| S32-A07 | Send test notification | Delivery permission/channel available; request a generic test immediately. Show Test requested, not Test heard/delivered. | Unavailable state offers settings; business data unaffected. |
| S32-A08 | Backup reminder settings | S33 after Save/discard decision. | No second independent backup-age setting. |
| S32-A09 | Cancel / Back | D01. | Staged settings discarded; any actual system-permission choice cannot be undone by discarding this form. That distinction is shown. |

**Notification interaction:** A summary opens S02; an appointment opens S19 or its current actual state. No notification contains customer names, contact details, access notes, or findings; appointment text can identify its time. Dismissing removes that notification, not the obligation. Summaries may recur on the next selected day; appointment alerts are one-shot and are suppressed if already started/cancelled/finalized or if their appointment start is already past. CORE offers no snooze action and no notification Mark complete. Rescheduling a task/visit is a separate dated, reasoned action.

### S33 — Data and recovery

**Purpose/entry/back:** S30/Home backup warning. Shows dataset name/reference, counts, app-managed photo/report storage, available local space when obtainable, last backup attempt, last verified full backup (snapshot date, destination/provider, file size), and whether later saved business changes exist. A destination name is not proof of remote upload or continuing accessibility.

**Setting:** Backup reminder Off/1 day/7 days/30 days, default 7. Reminder becomes due only when saved business data changed after the last verified full snapshot and the selected interval elapsed. With no full backup, age runs from the first saved business record. Routine backup metadata writes themselves do not make the dataset “changed.” Home notice remains useful when notifications are off.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S33-A01 | Create full backup | S34. | No automatic upload destination. |
| S33-A02 | Inspect/verify a backup | S35 Inspect-only mode; no replacement controls until explicitly choosing restore. | Cannot destroy data while merely checking a file. |
| S33-A03 | Restore a backup | S35. | Replacement warning and current-data comparison mandatory. |
| S33-A04 | Export readable CSV | S36. | Clearly not a full backup. |
| S33-A05 | Import customers and equipment | S37. | Clearly create-only, not history import or dataset merge. |
| S33-A06 | Backup reminder | Save selected age preference immediately and show Saved; update reminder participation. | Write failure leaves old preference. |
| S33-A07 | Erase this device's data | D12. | No one-tap reset. |
| S33-A08 | Recovery guidance / data-integrity warning | S38 guidance or the affected S25/D04 item list, with exact missing file references. | A damaged record is not silently hidden from backup counts. |

No automatic cloud-backup switch, storage-permission grab, gallery cleanup, or selective purging of old final reports. Complete backups may grow; user-owned copies and device storage need explicit attention.

### S34 — Create and verify a backup

**Purpose/entry/back:** S33; return there after a terminal result. Uses D11/P07–P10. The source dataset remains authoritative throughout. Visible phases: Check source → Create snapshot → Protect file → Choose destination/write → Read back/verify → Result.

**Content/inputs:** Included counts/estimated size and required local staging space; snapshot time; Full backup or explicitly Incomplete recovery copy; passphrase Required (at least 12 characters, spaces allowed), Confirm passphrase Required; Show/hide passphrase; acknowledgement that a forgotten passphrase has no ServiceLoop recovery. Passphrases are not embedded in diagnostics, recoverable input buffers, or backups, stored as an account secret, or recoverable from the app. They remain in operation memory only; an interrupted operation may require re-entry. Existing on-device records remain usable if a backup passphrase is forgotten; losing both device data and a usable passphrase is unrecoverable.

A full snapshot includes business profile/settings; customers/sites/equipment/plans; template revisions; saved contact/follow-up/change history; Booked/Working/Cancelled/Finalized visits; correction and recoverable input drafts; all record revisions; all app-held photographs/logos; and every saved PDF version, with identities/integrity checks. It excludes temporary rendering caches, source-gallery originals, provider credentials, system permissions, and nonexistent external delivery evidence.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S34-A01 | Check data | Verify references and source files; checkpoint saved drafts and display counts/warnings. | Missing files prevent a Complete result but leave records intact. |
| S34-A02 | Create incomplete recovery copy | Only when source files are already missing; list missing references and require explicit acknowledgement. Preserve all readable records/files and mark incompleteness in the package. | Never updates Last verified full backup. Unexpected corruption is not silently excused as an intended missing file. |
| S34-A03 | Show/hide passphrase | Change visibility only. | No clipboard copy or secret persistence by this control. |
| S34-A04 | Create backup / Create recovery copy | Check space, freeze a consistent saved snapshot, protect it with passphrase, open system Create document for `.slbackup`, write and close output. | Cancellation/provider failure retains local source and previous backup. Explain partial destination file when cleanup is not possible. |
| S34-A05 | Verify saved file | Reopen destination and validate the written package against its snapshot. If provider cannot reopen, ask user to select the file explicitly. Successful full verification immediately records the covered snapshot and verification result; it does not wait for Done. | Written but unverified is not marked verified. Provider acknowledgement does not prove an off-device copy is durable. |
| S34-A06 | Choose another destination / Retry | D11. | Do not overwrite the previous good backup automatically. |
| S34-A07 | Done | Return S33 after the already-recorded truthful full/incomplete/unverified result. | Any later business edits remain Changed since backup, even if writing the earlier snapshot finished later. |

The file picker may expose local storage, USB, or an installed cloud document provider. Availability is provider/device-dependent; a same-device backup does not protect against losing the device. Remind the technician to keep a verified copy outside the phone and remember its passphrase. No network/backend is required for a local destination; cloud destinations may require connectivity and separate credentials.

### S35 — Inspect or restore a backup

**Purpose/entry/back:** S01/S33 or S34 verification. Uses U4/D11. Inspect-only mode shows no destructive default action. Restore is complete replacement, not merging or reconciliation of separately edited devices.

**Inputs/content:** Selected `.slbackup`; passphrase; validated snapshot date, business/dataset identity, source app/format compatibility, counts, included PDFs/photos, full versus incomplete status; current-device counts and data newer than snapshot. Provider file name/date alone is not trusted. The app stages and validates actual contents before any replacement.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S35-A01 | Choose backup | System document picker; read selected file into isolated staging. | Cancel returns unchanged. Offline/revoked provider access offers Retry/another file. |
| S35-A02 | Unlock and inspect | Verify protection, structural validity, references, included file integrity, declared missing-file list, and supported format. | Wrong passphrase/damaged protected file is reported without destructive action. Unsupported newer format: use a compatible ServiceLoop release. Unknown/corrupt/path-escaping contents rejected, not partially imported. |
| S35-A03 | Inspect only / View contents summary | Show validation result without switching dataset. | No data mutation. |
| S35-A04 | Back up current data first | S34; return to inspected candidate afterward and revalidate if it changed. | Failure never automatically advances into replacement. |
| S35-A05 | Continue to replacement | Explicit warning: all current local records, changes, drafts, and app-held files will be replaced; no merge. Require acknowledgement of skipped backup if none was made, and acknowledgement of missing files for an incomplete copy. | Cancel discards candidate staging only. |
| S35-A06 | Replace local dataset | Require typing REPLACE; verify sufficient space and unchanged candidate; install validated candidate and switch as a single recoverable operation. | Failure retains/restores previous complete dataset. Never start deleting the live dataset before a replacement is ready. |
| S35-A07 | Open restored data | Success → S02 with restore summary. Reminder delivery starts Off pending review; preferred settings remain available in S32. Show “Use only one device for current work.” | Source backup file is not deleted. System permissions/provider grants are not assumed to transfer. |
| S35-A08 | Return to previous dataset | Only failed/interrupted replacement recovery, before success is accepted; select the intact previous dataset. | If neither copy validates, retain files and show recovery guidance rather than erase/reinitialize. |
| S35-A09 | Cancel / Back | Before final switch, leave current data unchanged and clean staging safely. | During short switch, D11 explains finishing/recovery rather than promising instant cancellation. |

A complete backup missing an expected file is invalid. A deliberately created incomplete recovery copy can be restored only with its explicit missing-file warning, and the missing items remain marked afterward. Restoring an older snapshot genuinely loses newer local work unless separately preserved. Already exported reports may be newer than the restored dataset; unique report-version identifiers prevent a reused visible revision number from masquerading as the same document.

### S36 — Readable CSV export

**Purpose/entry/back:** S33; uses U2/U3/D11. This is a data-ownership tool, not a customer report. Banner: “CSV files are readable and unencrypted. They do not restore photographs, report files, or the complete app.”

**Inputs:** Export type Directory template-compatible CSV / Records package; scope All customers or One customer; Include archived/retired/ended records (On); Include private/access notes (Off, explicit plaintext warning when enabled); Include previous record revisions (Off, Records package only). Existing exports cannot be recalled when settings change.

| ID | Control | Result/change | Cancel/error behavior |
|---|---|---|---|
| S36-A01 | Export type/scope/options | Stage the defined selection; show record/file counts and private-data warning. | No record change. |
| S36-A02 | Create export | Directory → one CSV with S37 headers; Records → ZIP of defined CSV files below and explanatory README. Choose destination via system Create document. | Source remains unchanged; interrupted output labelled incomplete, retry safe. |
| S36-A03 | Save another copy / Share export | Completed file only; system picker or Sharesheet. | Share initiation is not receipt; no automatic backup timestamp. |
| S36-A04 | Done / Back | Return S33 after truthful outcome. | Cancelled picker applies no export mutation to source. |

**Records package contents:** `customers.csv` (reference, name, contact, lifecycle, optional private notes); `sites.csv` (reference, customer link, name/address, contact mode/details, default/lifecycle, optional access notes); `equipment.csv` (reference, current site, identity fields, state, optional private notes); `service_plans.csv` (reference, equipment, name, interval, state, current due, supplied prior-service info, last valid fulfillment reference); `visits.csv` (reference, origin identity, booking/actual dates, state, record revision and recorded-on time); `work_items.csv` (visit/revision, equipment, plan or one-off, outcome, work/reason, fulfillment effect, old/new due dates); `checklist_answers.csv` (work item, captured template/revision, order, question/type/unit/required, value/disposition/reason); `findings.csv` (visit/revision, equipment/source answer, description/disposition, task links); `parts.csv` (visit/revision/equipment, description/quantity/unit/note); `followups.csv` (reference/type/context/source, title/date/state, closure reason/outcome and timestamps, optional private notes); `contact_notes.csv` (reference/context, channel/time/outcome, optional private details, edited timestamp); `changes.csv` (subject references, change type/time, old/new values, reason); `report_index.csv` (visit/record revision/PDF version, unique version ID, generated date, file availability/superseded/voided); `attachment_index.csv` (reference/context, file kind, caption, report inclusion, availability). The README explains keys, blank/private exclusions, date formats, and that files themselves are absent.

Exports use UTF-8, explicit headers, quoted comma-separated values, unambiguous dates, and decimal-point numeric values. Spreadsheet-formula-like text is escaped as text with the convention documented in the README; the app never evaluates imported formulas. This is a proposed export contract, not a requirement to expose a database schema. Only the directory export is accepted by S37, and only under its create-only rules.

### S37 — Fixed customer/site/equipment CSV import

**Purpose/entry/back:** S01/S33. Four phases in this one workflow: Get template → Select file → Validate/review → Commit/result. Back before commit leaves dataset untouched; a saved preview is not imported data. Uses D02 only for inspecting references, D11 for file flow, and D13 for possible duplicates. There is no arbitrary column-mapping wizard.

**Fixed headers:**

`customer_ref, customer_name, customer_contact_name, customer_phone, customer_email, customer_notes_internal, site_ref, site_name, site_address, site_use_customer_contact, site_contact_name, site_phone, site_email, site_access_notes_internal, equipment_ref, equipment_name, equipment_type, make, model, serial_number, internal_identifier, equipment_notes_internal`

**Format and field rules:** UTF-8 (optional BOM), comma separated with ordinary quoting; exact header names, any header order, no additional unknown columns. References contain 1–64 letters/digits/hyphens/underscores and are unique per entity type; customer name/reference required on every row. Site reference/name required when any site or equipment fields are supplied. `site_use_customer_contact` is true/false, default true when blank; true ignores no nonblank custom contact silently—conflicting custom values are a validation error. Equipment reference/name required when any equipment field is supplied. Blank identifiers and addresses are allowed; blank values mean blank, not “clear an existing record.” Ordinary U2 field validation applies. Internal columns remain internal after import.

A customer-only row can omit all site/equipment fields. If a new customer has no explicit site anywhere in the selected rows, preview creates one empty Main site. Otherwise its first listed site becomes default. Repeated customer/site references must carry consistent fields throughout the file. Proposed import guardrails: up to 20 MiB and 10,000 data rows per file, shown before selection; split larger initial directories into create-only batches.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S37-A01 | Save blank template / Save worked example | User-selected CSV destination. Example uses clearly fictitious customer/site/two-machine rows; it is never auto-imported. | Picker cancellation writes nothing valid. |
| S37-A02 | Read import rules | Show the fixed field, key, duplicate, and no-update rules above; linked bundled help available offline. | No broad import-permission request. |
| S37-A03 | Choose CSV | System document picker, local copy for validation. | Wrong type/encoding/delimiter/size/headers produce a specific message; no records added. |
| S37-A04 | Preview row / filter Errors, Warnings, New, Existing unchanged | Show source row number, proposed customer/site/equipment links, and exact messages. | Viewing never commits. |
| S37-A05 | Review possible duplicate | D13 for same name/contact/site or serial/internal-ID candidates; choose Create separate or Skip row/branch, never implicit merge. | An unresolved warning requiring identity judgment blocks commit. |
| S37-A06 | Save validation report | User-selected readable CSV with row numbers/messages. | Source file and database unchanged. |
| S37-A07 | Choose corrected file | Restart validation against actual current dataset. | Prior preview is discarded, not partially applied. |
| S37-A08 | Import reviewed records | All non-skipped rows valid; display counts and require confirmation. Apply all new linked records together. Exact unchanged existing references are reused/skipped, never overwritten. | Any remaining invalid row blocks entire import; user fixes file or explicitly skips its validly removable branch. Crash/failure commits all or none; repeated retry detects committed references. |
| S37-A09 | View imported customers / Equipment without active plans | S05 matching imported records or No active plan filter. | Reminders are not generated from imported equipment alone; plans are added explicitly in S14. |
| S37-A10 | Cancel / Back | Leave before commit; remove staging. | Existing dataset untouched. |

**Duplicate policy:** An exact existing reference may be reused only if its parent and supplied fields match after normalization; the preview labels Existing unchanged. Same reference with different fields is a hard conflict, not a hidden update or a warning that can be overridden. Two conflicting definitions of a reference within one file are also errors. Different references with similar identity are potential duplicates, not automatic matches. No merge action exists. Skipping a customer/site branch also skips dependent proposed children; revalidation must leave a coherent hierarchy. A valid new machine can reference an unchanged existing customer/site by their visible CSV references.

No plans, schedules, photographs, historical visits, PDF reports, billing data, or contacts-database sweep are imported. Reimporting a directory export is not a bulk-edit mechanism. Successful import creates ordinary In service equipment, not fictitious prior service or due dates.

### S38 — Privacy, help, and app information

**Purpose/entry/back:** S30/S01/contextual help. Bundled, searchable-by-topic help, not a support chatbot. Uses U1/U6. All core guidance is available offline.

| ID | Control/topic | Content/result | Cancel/error behavior |
|---|---|---|---|
| S38-A01 | Getting started | Customer → site → equipment → plan → contact/book → visit → report/next service; links to the actual relevant screens, preserving context. | No sample records auto-created. |
| S38-A02 | Due dates, partial work, and corrections | Explain R01–R06, including why booking and report sharing do not fulfill service. | Read-only. |
| S38-A03 | Local storage and recovery | Saved versus backup versus export; lost phone/uninstall; passphrase loss; same-device copy limitations; restore replacement and old-device warning. | Direct links to S33–S35. |
| S38-A04 | Permissions and external apps | Explain optional notifications, selected media/contact data, file picker, and no automatic proof of communication. Links S32/D10. | No coercive request on merely reading help. |
| S38-A05 | Privacy policy | Bundled current policy text; Open published policy in browser. Declare actual local handling, optional handoffs, backups, and any release dependencies accurately. | No browser/network: bundled text remains. Public release URL and publisher identity must be supplied before release; this proposal invents neither. P12. |
| S38-A06 | What reports contain | Fixed public fields; internal/access notes excluded; photo selection; exported copies beyond app control; no regulatory certification/signature service. | Links S39 from a record, otherwise explanatory example text only. |
| S38-A07 | Accessibility and display | Explain system text size/appearance, labelled controls, text report view, and reorder alternatives. | No app-specific cosmetic settings hidden here. |
| S38-A08 | App information / third-party notices | Actual installed version/build, data-format version, publisher, and applicable notices at implementation. | No false certification badge. |
| S38-A09 | Copy support details | Copy app/OS versions, permission/channel state, operation error code, and coarse storage status only; technician may send manually. | No customer names, phone numbers, notes, photos, passphrase, or device advertising identifier. No automatic upload/analytics. |

There is no mandatory analytics SDK, account, advertising identifier, or permission to read broad SMS/call history. Release-specific privacy disclosures still require a real review of the shipped code and dependencies; “offline-first” is not a substitute for that review.

### S39 — Customer report preview and file actions

**Purpose/entry/back:** S24 draft preview, S25 final report, S26 proposed correction, or a historical version. Back returns to the exact caller. Shows visit reference, record revision, PDF version/unique version ID, actual service date, generated date, and Draft/Current/Superseded/Voided/File missing status outside the page preview.

**One fixed layout:** Business/display and technician identity with optional logo/contact/address; customer and site identity as captured; actual service date and visit reference; one section per machine with captured name/make/model/serial/internal identifier; every work item and its Performed/Partly/Not performed outcome, public work/reason, checklist questions and explicit responses/NA reasons/unrecorded status, and recorded next-date effect; machine observations and findings/dispositions; part descriptions/quantities/units; corrective follow-up planned dates based on the finalized snapshot; selected photographs with captions; revision/version identification and page numbers. Missing identifiers/address are shown as Not recorded where needed to identify a limitation, not filled from later data. The report is a service record, not a regulatory certificate.

Private business planning notes, customer internal notes, access notes, internal visit/task/contact notes, unselected photos, template guidance, and current private follow-up commentary are excluded. Public corrective explanation comes from the finding, not an internal task note. An unfinished checklist is printed honestly rather than omitted to imply a pass. A zero-performed attendance report explicitly says no service obligation was fulfilled.

| ID | Control | Available/result | Cancel/error behavior |
|---|---|---|---|
| S39-A01 | Previous page / Next page / Go to page | Navigate actual rendered PDF pages; current page/total labelled. | Invalid page number rejected. Rendering error offers Retry/Text view. |
| S39-A02 | Zoom in / Zoom out / Fit page | Inspect layout; gestures are optional equivalents. | Does not edit file. |
| S39-A03 | Text view / PDF view | Structured public snapshot for accessible reading versus exact PDF page preview. | Text view is not a claim that exported PDFs are fully accessibility-tagged. |
| S39-A04 | Report version | Choose generated version belonging to this visit; order by record revision and generation/version. | Superseded/voided indicators never disappear merely because an old version is opened. |
| S39-A05 | Save a PDF copy | Final generated version only; system Create document; copy exact selected bytes. | Cancel makes no copy; write error preserves original stored PDF. Truthful Saved copy result only. |
| S39-A06 | Share PDF | Final generated version only; Android Sharesheet with a scoped read grant. Explicit warning before sharing an original now marked superseded/voided. | Opening/returning/cancelling Sharesheet is not delivery. Label Share initiated if recording an attempt, never Customer received. P05. |
| S39-A07 | Open in another app | Final generated version only; external PDF viewer if available. | Missing handler leaves internal PDF/Text view usable; no mandatory viewer installation. |
| S39-A08 | Record handoff note | S29 with exact report version. | Technician records what is known; no inferred receipt. |
| S39-A09 | Retry generation / Re-create missing PDF | Generate from fixed record snapshot. Missing original produces a new clearly labelled Re-created copy with new PDF version/unique ID and generation date; never pretends to be the original bytes. | Missing required selected image blocks faithful generation; use Locate original, restore, or explicit correction. Never silently omit it. |
| S39-A10 | Back / Return to editing | Back to S25, or S24/S26 in draft mode. | Draft mode has no Save/Share/external viewer/handoff actions. |

A fixed original PDF is retained even if app rendering/layout code later changes. A recreated file is identifiable as a new rendition; original file metadata remains visible if its bytes are missing. Corrections produce a new record revision and new report version. Changed contact data, template revisions, or resolved tasks cannot silently change either the old record or the old PDF.

### S40 — Scoped history

**Purpose/entry/back:** Customer/site/equipment history action, including in-field reference. Back returns to caller, including Working visit. Shows scope prominently and dates as actual event/service dates, with recorded-on information available for backdated entries.

**Content:** Final/voided visits, cancellations, contact notes, follow-up events, and reasoned lifecycle/date/move/correction entries. Customer/site histories use the context at the event, not the machine's current owner. Equipment history spans previous locations and shows each origin. A move appears at both affected site/customer histories as a move, not as a performed service.

| ID | Control | Result | Cancel/error behavior |
|---|---|---|---|
| S40-A01 | Type filter | All activity/Service records/Contacts/Follow-ups/Changes; default All activity. | Scope remains fixed unless user navigates to another context. |
| S40-A02 | Date range / Clear | All dates or valid From/To range. | Invalid range not applied. |
| S40-A03 | Sort | Event date newest (default)/Event date oldest/Recorded most recently. | Backdated work can be found without falsifying when entered. |
| S40-A04 | Search within history | Match service/finding/task/contact title, reference, equipment identity, and user-entered text available in records; no PDF-byte/photo OCR search. | No results offers Clear. Private content stays inside app. |
| S40-A05 | Entry row | Service → S25; cancellation → S19; contact → S29; follow-up → S27; change → D06 read-only variant with Close only. | Read-only change detail cannot replay an old transition. |
| S40-A06 | Record past visit | S18 historical mode with current scope proposed and actual date required. | No current obligation is advanced automatically. |

No editable aggregate “last serviced” field, automatic service proof from an appointment, or deletion of historical reports from this timeline.


---

# E. Automatic behavior and lifecycle map

These behaviors are CORE even though they are not separate buttons. They must be observable where they affect trust, not presented as hidden synchronization.

| ID | Trigger | Required behavior and visible outcome | Limits/fallback |
|---|---|---|---|
| E01 | Saved record change; business-date rollover; opening/resuming a screen | Recompute due badges/counts, available selections, linked booking/task status, and nearest equipment due date. An active plan has one current obligation. | A failed read shows an error, not zero work. Notifications are not required for these computations. |
| E02 | Daily summary or appointment reminder becomes eligible | Read current saved state before requesting notification. Suppress obsolete/cancelled/completed/duplicate requests; use generic content and correct deep link. One summary per business day, one alert per configured appointment occurrence. | Approximate delivery only. Delayed appointment alert after appointment start is suppressed; passed booking remains visible in Work. No missed-notification replay storm. P01–P02. |
| E03 | Working/correction input changes; navigation; ordinary interruption | Save discrete changes and buffered text; display Saving then a confirmed local checkpoint. Return/resume opens the same saved draft and pending review choices. | Changes after the last successful checkpoint may be lost in abrupt failure. Storage failure is not masked by a green Saved label. |
| E04 | Camera/picker result returns after activity/process recreation | Resolve the persisted destination/visit/item and validate the complete returned image before acknowledging it. A result belongs to its original operation, not whichever machine is now visible. | Removed draft/replaced dataset invalidates stale result attachment; explain and offer a fresh deliberate intake, not silent reassignment. Activity result delivery alone does not persist app data. P03/P04. |
| E05 | Template saved/archived/restored | New working items capture the chosen template revision at the moment they start. Existing working/final checklists retain their captured wording/order/units/required flags. | An archived but still-assigned template remains usable at its frozen latest revision and is visibly labelled; no silent empty checklist. |
| E06 | Finalization/correction succeeds; PDF generation retries | Record version and explicit schedule/task effects become durable once. Report generation uses only a fixed public snapshot; retries cannot repeat fulfillment. | Report error affects PDF availability, not completed business recording. Private fields never serve as missing public text. |
| E07 | Device time/time zone changes; daylight-saving transition | Re-evaluate business Today and future eligible reminders. Due dates remain dates. Existing timed appointments retain their recorded zone/instant; the app displays zone differences. | Device-clock correctness is not certified. Invalid/ambiguous user-entered local appointment times require a visible choice, not an invisible guess. R08. |
| E08 | Reboot, app update, or normal process restart | Recover saved records/drafts; reschedule/reconcile eligible reminder work when permitted and after protected data becomes available. Retain notifications as optional assistance. | No promise of customer-data processing before first unlock, or execution while force-stopped. On next launch recompute current work rather than replay every missed alarm. P02. |
| E09 | Notification permission/channel changed externally | Recheck on resume and before delivery; show actual blocked/off state with optional settings route. | Core lists remain fully usable; no permission loop, automatic special-access request, or battery-exemption demand. |
| E10 | App resumed after background file operation or abrupt interruption | Validate operation checkpoint/temporary outputs and display completed/failed/interrupted accurately. Preserve previous good backup/PDF/live dataset. | An unfinished external file can remain in a third-party provider; disclose it. Retry must be safe and explicit where access must be reselected. |
| E11 | Restore or dataset reset completes | Invalidate old deep links/pending work/results for the replaced dataset; recompute counts. Restore retains preferred reminder configuration but starts local delivery Off pending review. | No automatic activation on two independently edited devices. User must choose one current device. |
| E12 | Saved business changes after backup snapshot | Mark Changed since backup according to what the snapshot covered, not when a slow destination finished writing it. Backup reminders use the configured age and changed state. | Verifying another business's backup does not satisfy recovery coverage for the current dataset. A recovery copy never satisfies Full backup status. |
| E13 | Attachment becomes missing/unreadable or storage fills | Show specific file and affected record; prevent false report/backup success; preserve other readable data. Offer locate-exact-original, restore guidance, or labelled incomplete recovery copy. | No magical reconstruction of an absent photograph. Recovery copy supports missing attachments with a readable record store, not arbitrary database repair. |
| E14 | Safe temporary-file cleanup becomes eligible | Remove abandoned capture/render/import staging files and unreferenced temporary copies only. Retain files referenced by any saved record revision/PDF. | Never purge historical evidence, source-gallery files, or user-owned document-provider copies to free space silently. |
| E15 | Data-format migration after an app update | Validate a recoverable migration before accepting changed data; retain recoverability and show a clear failure state if migration cannot finish. | Never initialize an empty dataset over one that cannot be opened. Supported older-backup migration paths and rejected newer formats must be tested before release. |

---

# F. Cross-screen rules and critical journeys

## F1. The business rules

### R01 — Due dates, appointments, contact, and reminders are different facts

Booking, moving/cancelling a booking, contact logging, reminder dismissal, and follow-up scheduling do not fulfill service or change a plan's due date. An overdue service stays overdue even when booked next week. A date can change without service only through an explicit plan date edit with a reason; the history labels that as a schedule change, not a service record.

Pausing removes a plan from active obligations/reminders while retaining its date. Ending ceases its future obligation without claiming it was performed. Neither closes an unrelated corrective task. A passed appointment with no outcome is a Booked visit needing attention; elapsed time never supplies evidence of attendance.

### R02 — Completion has five independent meanings

| Event/decision | What it changes | What it does not imply |
|---|---|---|
| Checklist marked Reviewed | Required responses/dispositions were accounted for and technician acknowledged review. | No blanket pass; no automatic service fulfillment or machine safety. |
| Work item marked Performed | That named service was recorded as performed, with a work note. | No recurrence advance until explicit eligible fulfillment confirmation. |
| Current obligation fulfilled | Consume this plan's current occurrence once, retain the actual completion evidence, and set the confirmed next date. | No completion of other plans on the same machine. |
| Visit finalized | Fix a record of all selected outcomes, including partial/no work. | No universal fulfillment; no automatic task resolution or PDF delivery. |
| Corrective follow-up resolved | Record the task's resolution and stop its own reminder. | No retroactive change to the original finding/PDF and no regulatory safety certificate. |

Not applicable with a reason can satisfy a required response; Not checked/Not recorded cannot. Required means required to label the checklist Reviewed, not permission to erase legitimate partial work. A periodic inspection may be fully performed while identifying a fault; that inspection can fulfill its plan while a corrective task remains Open. A maintenance item left partly performed cannot fulfill its obligation.

### R03 — Recurrence, current-obligation identity, and backdating

**Initial setup:** Require an explicit next-due date. Optional supplied earlier-service information may suggest it but creates no historical visit. Earlier-service setup date is editable only until the first valid in-app fulfillment; afterward its original value remains historical setup information rather than a replacement for recorded service.

**Calculation:** Next date is actual completion date plus the captured positive day/week/month/year interval. Weeks mean seven-day increments. For months/years, an unavailable day clips to that target month's last valid day: 31 January 2026 + one month → 28 February 2026; 29 February 2024 + one year → 28 February 2025. The next cycle starts from its actual later completion date; there is no hidden return to the 31st or fixed anniversary. A manual next-date override must be later than the actual completion date and have a reason.

**Eligibility:** To apply work to the current obligation, the plan must be Active, the equipment/current context eligible, the selected obligation still current, no other Booked/Working visit claiming it, and its actual completion date later than the plan's latest valid counted completion. Before any in-app completion, the supplied earlier-service date is the comparison baseline when present. Same-day second fulfillment of the same plan is not supported: record additional work as History only or correct the original record. Multiple different plans may be fulfilled on the same day.

**Historical entries:** Past mode defaults to History only. An eligible backdated entry can deliberately fulfill the still-current obligation after review, but not an obligation already consumed by a later record. Entries at a former site or against Paused/Ended plans remain History only. A very old date can calculate a next date that is already overdue; show it honestly rather than repeatedly advancing until it reaches the future.

**Editing:** An interval edit does not move the existing current due date. Manual due-date changes require a reason, must remain after the latest valid counted completion (or the supplied setup baseline before the first one), and leave previous appointment dates unchanged. Booked work detects a changed plan at Start; current Working claims block material plan edits. A historical-only draft may retain an older plan snapshot, so its eventual fulfillment eligibility is rechecked against live data.

**Once only:** Selection/finalization carries the exact obligation it is intended to satisfy. Completion/retry/reopen/PDF generation never targets a newly created “next” obligation by accident. Corrections modify or reverse the original effect under R06; they are not another service execution.

### R04 — One-site, multi-machine, partial-work behavior

Each visit contains at least one named work item on equipment and belongs to one site. One equipment item can have several plan/one-off lines. Selecting two plans with the same reusable template produces two independently named work records; there is no implicit shared answer or double-completion shortcut.

Final review accounts for every selected line. Fully performed eligible lines advance only when checked for fulfillment. Partial/unperformed lines retain their original dates and release their visit claim, becoming available for a return booking. Linked corrective tasks can remain open or be explicitly resolved; new task proposals go live only when the record commits. All outcomes appear in the report.

Work spanning separate actual service dates uses separate visits: finalize the first date's partial work, then create a return visit. This is a deliberate first-product simplification, not time tracking. A genuine visit without a recurring plan uses a one-off item. An attempted phone call is a contact note, not a site visit.

### R05 — Snapshots and report identity

Reusable template content and equipment/customer/site/business identity are captured when a working item/visit starts; newly added work captures at its own start. A visible explicit refresh/typo correction is possible in the draft. Finalization freezes the record revision and public report selections. Current master-data edits do not rewrite snapshots.

Every final visit has a stable reference independent of a resettable invoice-style sequence. Every record revision and PDF rendition has its own version identity; PDF metadata visibly includes revision, generation time, and a unique version identifier. This matters after restoring an older backup, when a visible revision count alone might otherwise be reused. This is identification and local history, not tamper-proof certification.

Stored original PDF bytes remain the original. Regenerating a missing PDF creates a labelled new rendition, not a claim of identical recovered bytes. An exact missing-photo replacement requires an integrity match; new/replacement evidence enters through an explicit correction and is labelled as added later. Report selections include only public content; later follow-up resolution cannot update an old report automatically.

### R06 — Corrections and voids reconcile, never replay

Corrections require a reason, retain prior revisions, and do not mutate the original subject links. Wrong customer/site/equipment assignment requires voiding and making a correctly associated record. A void removes the record from valid-service summaries but retains its evidence and version identifiers.

| Situation | Required schedule treatment in S26 |
|---|---|
| Text/photo-selection/identity-typo correction with unchanged service/fulfillment facts | No schedule change. New record/report revision only. |
| Latest counted completion's actual date changes, and current next date still comes from that record | Propose recalculation from corrected actual date and its captured interval; require explicit confirmation. Modify the original contribution, not consume the next obligation. |
| A corrected date would cross or equal a preceding/following valid counted completion of the same plan | Do not silently reorder completion history. Require removal of this line's counted-fulfillment effect while retaining the corrected work as History only, then reconcile using the applicable rows below. Dates that remain strictly between neighboring counted completions can retain their original effect. |
| Latest counted fulfillment is removed or record voided | Show the original pre-fulfillment due date as the candidate outstanding date; explicitly confirm/reconcile it. Do not pretend the service still supports an automatically advanced schedule. |
| A later valid fulfillment already exists | Correct historical record only. Preserve the current schedule derived from later service; no chain-wide rolling recalculation. |
| Current due date was manually changed after the record | Default to retaining that current date, visibly labelled as a later manual decision; require acknowledgement/reason or an explicit replacement date. Never silently overwrite it. |
| Plan is Paused | Keep it Paused; reconcile its retained date when the record actually supports that date. No automatic resumption. |
| Plan is Ended | Remains Ended. Correct history; do not create an active future obligation. |
| A correction newly asserts eligible latest fulfillment | Apply only to the actually current, unclaimed obligation after R03 checks and explicit date confirmation. Otherwise history-only correction. |
| A Booked/Working downstream visit depends on a schedule being changed | Block that schedule-changing commit until the claim is deliberately removed/resolved. Offer those references; text-only corrections remain possible. |

Existing follow-ups default to Keep. A task created from an erroneous finding is cancelled only by an explicit reasoned choice. A resolved task is not automatically reopened because text changed. New required corrective tasks are reviewed before committing. Record revision and selected schedule/task effects commit together; retries identify the existing successful revision.

### R07 — Record lifecycle and relocation

No active work can be created under an archived customer/site or retired equipment. Restoring a parent does not automatically restore children, resume plans, or revive tasks. Historical viewing/export remains possible.

| Entity/action | Eligibility and consequences |
|---|---|
| Archive customer | All sites archived; no Open customer-level task or Booked/Working visit. Preserve all history. Active dependencies are linked, not silently closed. |
| Archive site | All equipment retired or moved out; no Booked/Working visit or Open task anchored here. Default site must be deliberately reassigned or cleared. |
| Retire equipment | All plans Ended; no Booked/Working selection or Open equipment-linked task. Preserve historical photos/reports and dated observations. |
| Return equipment to service | Parent active; retain same identity/history, but previously Ended plans remain ended. Create new plans for new commitments. |
| Pause/end plan | First remove/finish any Booked/Working claim. Retain history and current/last date for explanation; stop active reminders. Pause can resume; End cannot be toggled back. |
| Move equipment | No Booked/Working selection or Open equipment-linked task; explicit destination/reason/carry confirmation. All retained plan states/dates move with current equipment; old visits, customer/site histories, and reports keep original context. |
| Reassign a site to another customer | Only wholly unused site, with no equipment/work/contact/follow-up/history reference. Otherwise create destination site and move individual equipment explicitly. |
| Change ordinary master name/address/contact | Current facts update for future use; existing snapshots remain unchanged until an explicit permitted draft refresh or record correction. |
| Archive template | Hide from new selections; retain frozen latest revision for existing assignments and retain every captured checklist. Restore before editing, or duplicate as a new template. |
| Delete customer/site/equipment/plan/template | Only truly unused and unreferenced, under the specific screen's rule. Empty auto-created first site may be included in deleting an unused customer. Nothing referenced by historical records is silently cascaded. |
| Delete/abandon draft | Only working/uncommitted content, after explicit scope warning. Booking-origin discard preserves a cancellation entry. No service or follow-up effect is replayed. |
| Delete final service record/report version | No ordinary hard-delete control. Correct/void the record; whole local erasure is separate D12. Exported/shared copies remain external. |

This is a conservative first-product policy. It does not claim to implement every jurisdiction's retention/erasure requirements; those must be reviewed for the chosen market before release. There is no background customer-merge or cross-business history migration.

### R08 — Calendar, business zone, and actual dates

Service/follow-up dates are calendar dates interpreted against business Today. Changing a device zone does not change them. Existing timed bookings retain their recorded time zone and instant; changing the business zone changes future booking defaults and summary scheduling, not those existing appointments. A day-only booking retains its calendar date.

New booking time is interpreted in the business zone displayed on the form. A nonexistent daylight-saving local time cannot be saved until the technician chooses a valid time. A repeated local time shows the two offsets and requires a choice; no invisible guess. Summaries use the configured business-local time and days, subject to Android delay. Business Today and timed-appointment grouping are computed in the configured business zone; a differing original appointment zone remains visible.

The actual service date is explicitly confirmed at completion. Crossing midnight does not silently change it; a prompt asks the technician to confirm the intended date. The app records when an entry was made separately from when service occurred. It cannot certify that the device clock or technician-entered date is correct.

### R09 — Import, export, backup, and restore are separate operations

CSV import is a create-only directory operation with explicit identity keys and a coherent all-or-nothing commit of reviewed, non-skipped rows. Existing-reference conflicts are errors, not an update mechanism. No unresolved invalid row is silently dropped. A preview, a cancelled picker, or a validation report creates no customer or equipment.

CSV export is readable data without physical attachments/PDF bytes or complete app restoration semantics. A verified full backup contains all saved app-held data/files; an explicitly incomplete recovery copy lists already-missing attachments. Exporting CSV, opening Sharesheet, or receiving a provider write acknowledgement does not by itself establish a verified complete backup.

Restore validates the actual protected package in staging and replaces, never merges. Newer unsupported formats, undeclared missing files, bad integrity, corrupt references, and insufficient space are rejected before replacement. A verified supported older format may be migrated in staging. Cancellation/failure leaves the previous live dataset intact; interrupted replacement must recover to one intact version, never half of each.

Only a verified snapshot of the current dataset can indicate its backup coverage. Inspecting another dataset's valid file does not satisfy the current dataset's backup reminder. After a successful restore, the restored snapshot is shown as the recovery source; subsequent business changes require a newer full backup. Restore does not prove that the old device stopped being edited. The product explicitly requires one authoritative device and offers no conflict resolution.

### R10 — Private information and external handoffs

Internal/access notes, unselected photos, private task/contact notes, and source photo metadata never enter a customer PDF by default or through a generic “include everything” action. The fixed report includes customer/site identification and address, not the customer's phone/email contact list; business contact details may appear. Public findings/checklist outcomes are not silently hidden to make an incomplete visit look successful.

Full backups intentionally include private information and require passphrase protection. CSV private fields require an explicit option and plaintext warning. Exported PDFs/CSV/backups and clipboard contents can be retained by external applications/recipients. Removing/correcting data inside ServiceLoop cannot recall those copies.

External dialer/composer/map/viewer/share launches are attempts/handoffs, never proof of connection, sending, receipt, attendance, or approval. Missing apps and cancellations have manual/copy/in-app fallbacks. No backend, broad address-book access, call/SMS-log reading, background location, or accessibility automation is used to infer events.

### R11 — Honest local saving and failure boundaries

Saved on this device means a completed local write, not a backup, upload, delivered report, or legally verified record. Fieldwork, record finalization, and file intake need acknowledged checkpoints. A temporarily recoverable form is still unsubmitted until Save; a working visit is durable but not finalized; a finalized record can exist while its PDF is unavailable.

A failure never turns an operation into a successful empty result. Database-open/migration failure must preserve the existing files and offer recovery, not initialize a replacement empty app. Incomplete recovery copies address missing attachments when the record store is readable; they are not a promise to repair arbitrary database corruption. Free-space/provider errors identify the affected operation without erasing existing good data.

## F2. Critical journeys

The names and dates below are illustrative scenarios, not claims about actual customers or scheduled user events.

### J01 — First customer, site, machine, two plans

From S01-A01, use S02-A07 → S07-A01 to create **Harbor Fitness** and its first site, renamed **Riverside gym**. A name is enough; contact/address can be completed later. S08-A05 → S11-A03 adds **Treadmill T1**, without a serial number if unknown; its stable reference still identifies it.

S10-A04 → S14-A04 creates **Quarterly inspection**, Every 3 Months, first due 15 September 2026. Repeat for **Lubrication**, Every 6 Months, first due 1 October 2026. A reusable inspection template can be attached, but is not required to create obligations. Home and Work now show two obligations, not one machine-level date. No booking or historical service is invented from setup.

### J02 — Due work, contact, booking, postponement, actual completion

On the illustrative due date, S03-A09 opens the inspection plan. S13-A04 → D05-A02 opens the dialer; after the actual call, S29-A01 records Spoke/exchanged messages. S03-A11 → S18-A07 books 18 September. S19-A01 → S18-A07 postpones to 22 September with a reason.

The inspection remains due 15 September and is overdue after that date. On 22 September, S19-A04 starts work; S22-A06 marks the completed checklist Reviewed. At S24-A02 choose Performed, then explicitly use S24-A03 to fulfill the current inspection obligation. S24-A04 proposes 22 December 2026. S24-A10 finalizes; only this inspection advances. S25-A01 generates the report, and S39-A06 initiates sharing without asserting delivery.

### J03 — Several machines, partial service, fault, corrective return

A visit at one site includes T1 inspection and lubrication, T2 maintenance, and Bike B1 maintenance, all hypothetically due 15 September. Start through S18-A08. T1 inspection is reviewed but identifies a damaged belt; S23-A02 stages a corrective task dated 29 September. T1 lubrication is partly performed; T2 service is fully performed; B1 could not be accessed.

At S24, mark the four lines Performed / Partly performed / Performed / Not performed. Check fulfillment only for T1 inspection and T2 service, with notes and independent next dates. Keep any existing unrelated tasks Open. Finalize using S24-A10: two plans advance, lubrication and B1 retain 15 September and remain overdue, and the corrective task becomes live. The report records all four outcomes and the belt issue; it does not describe T1 as safe because its inspection obligation advanced. S25-A06 starts planning a return without copying previous successful answers.

### J04 — Interrupted work with photographs

In S21, notes/answers reach Saved on this device. D04-A01 checkpoints before the external camera opens. A phone call, rotation, or app recreation occurs. On resumption, E03/E04 restore the saved visit and associate a validated photo with its original machine, not the machine currently selected elsewhere.

If storage filled during photo copying, the image is visibly Not saved; notes already acknowledged remain. Retry with D11-A02 after freeing space, choose another image, or continue without claiming the failed photo exists. S20-A09 leaves the draft; S02-A04/S03-A09 resumes it. Only the last acknowledged checkpoint is guaranteed, not never-written final keystrokes. Finalization is blocked until required selected report files are genuinely available.

### J05 — Template/address change after final report

A visit captures Template revision 2 and the site's then-recorded address. S24-A10 finalizes and S25-A01 creates record r1/PDF p1. Later, S17-A06 publishes template revision 3 and S09-A04 changes the site's current address.

New working visits use the new template/address. S25/S39 still show the old visit's captured wording and location, and original PDF bytes remain unchanged. A historical typo is corrected only by S25-A03 → S26-A05, with a reason and a new report version. The current address is not automatically substituted into a report about an earlier visit at a different address.

### J06 — Backdated entry and correction without double advancement

A plan has a valid counted service dated 22 September and next due 22 December. S13-A09 → S18-A09 records omitted work actually done on 10 September. S24-A03 remains History only: this older entry cannot consume the newer current obligation.

For a typo in the 22 September record, S25-A03 opens S26. Correcting actual date to 21 September proposes 21 December only if that record still supports the current schedule; S26-A02 makes the comparison explicit. S26-A05 amends the original effect once. Generating/sharing/reopening the corrected PDF cannot advance to another March date. If a later December service already established March's due date, the September correction remains historical and March is unchanged. A dependent booking must be dealt with before a schedule-changing correction commits.

### J07 — Notifications denied, missing app, cancelled share

The technician declines D10-A01's system request. S32 shows requested-but-blocked delivery, while S02/S03 still show all obligations and appointments. D05-A04 finds no suitable email composer; D05-A06 copies the prepared details for a manual alternative. No customer-contact event is invented.

A finalized report is opened with S25-A01 and shared using S39-A06; the technician cancels the Sharesheet. The saved PDF remains Ready, due dates do not change, and no Received/Delivered label appears. An optional S39-A08/S29 note records only what the technician actually knows. Dismissing a later notification likewise has no completion effect.

### J08 — Initial import, repeat import, full backup, destructive restore

Use S37-A01 to obtain the fixed template, then S37-A03 to select filled customer/site/equipment rows. One row has a conflicting site reference. S37-A04 exposes the exact error; no records are imported. Correct the file via S37-A07 or explicitly skip a coherent branch. S37-A08 commits only after valid preview confirmation. Imported machines have no service plans until S14 adds them.

Reimporting unchanged references produces Existing unchanged rather than duplicates; changing existing reference fields causes a conflict, never overwrite. Similar identity under a new reference requires D13 review.

S34-A04 creates a passphrase-protected full snapshot; S34-A05 reads it back before calling it verified. Later S35-A02 inspects it and compares current versus snapshot data. S35-A04 offers a current-data backup. Only S35-A06 with REPLACE replaces the dataset; there is no merge. Wrong passphrase, missing undeclared photo, unsupported newer format, cancelled picker, or insufficient staging space leaves current data intact. A declared incomplete recovery copy requires its extra warning and retains missing-file badges after restore. Reminder delivery starts Off for review, and the restored snapshot cannot recover changes made after it.


---

# G. Brief feasibility and dependency notes

**Verification date: 5 September 2026.** The facts below were checked against official Android and Google Play documentation. The **ServiceLoop response** column is a design recommendation, not a claim that these safeguards are already implemented or tested. Ordinary customer/plan/checklist business rules are product choices; Android documentation does not validate their usefulness.

No minimum/target SDK, UI framework, database library, build toolchain, Play approval, or universal device behavior is certified by this review. Compatibility choices and applicable release-time Play requirements must be checked when an implementation is actually prepared. No paid account or licensing behavior is included, so this review does not imply a billing design.

## G1. Platform-dependent promises

| ID / user-visible capability | Verified supported route | Material limitation | ServiceLoop response and fallback |
|---|---|---|---|
| **P01 — Optional work notifications** | Android supports app notification channels and, on Android 13 and later, runtime notification permission. The user controls permission and channel availability. [O01] | Permission can be denied or later revoked; desired in-app settings are not proof of delivery capability. | S32 separates requested settings from actual permission/channel state. Ask contextually through D10. Generic notifications contain no customer/access details. S02/S03 remain fully usable when notifications are unavailable. |
| **P02 — Approximate reminders and ordinary background recovery** | Inexact alarms and persistent background work provide supported scheduling routes. Persistent work can survive ordinary process/app restarts and reboot. [O02–O03] | Doze and system restrictions can delay execution. Force-stop is not ordinary process death: a stopped app cannot promise background delivery; Android 15 also cancels its pending intents until it leaves the stopped state. [O04] | Daily summaries and optional appointment alerts are approximate. Reconcile on next allowed execution or user opening; suppress stale appointment alerts. No exact-alarm permission, battery-exemption demand, accessibility service, or persistent foreground service for reminder delivery. The Work list is the fallback, not a false guarantee of punctuality. |
| **P03 — Chosen photographs and camera handoff** | Android Photo Picker supports selection without broad media-library access; common intents support a delegated camera capture. Activity Result APIs support receiving results across activity recreation when the app preserves the needed operation context. [O05, O07–O08] | A selected cloud image may need network access; grants/results do not themselves create a durable app-owned attachment. External camera availability varies; the app process can be destroyed while another app is open. | D04 checkpoints the visit and records its exact target before handoff; validates and copies returned media into app-held storage before Saved. Cancel adds nothing. Missing camera → choose existing photo; unavailable photo → retry or continue without it. No in-app camera/scanner or broad photo permission is needed for this CORE route. |
| **P04 — Durable local data and attachment ownership** | App-specific internal files are available without unrestricted storage permission. Android distinguishes persistent files from discardable cache. [O06] | Uninstall/clearing app data removes app-held files. A URI pointing at somebody else's movable file is not a durable owned copy. Local files do not constitute off-device recovery. | Store record attachments/final PDFs in persistent app storage, not cache-only references. Show Saved on this device only after acknowledged writes. Temporary thumbnails and rendering caches are disposable; source records and finalized evidence are not. Lost/cleared device → S35 with an available backup. |
| **P05 — Offline PDF creation, preview, and sharing** | `PdfDocument` can create pages locally; `PdfRenderer` can render PDFs inside the app. `FileProvider` and temporary content-URI grants support secure file handoff. [O09–O11] | Android supplies mechanisms, not ServiceLoop's pagination, readable layout, accessibility, or delivery confirmation. Another application may be absent, cancelled, or retain a received copy. | S39 works without an external viewer, offers structured text, and handles pagination/long fields as product requirements. Share only explicit report files with temporary access, not the whole private store. Missing external viewer → in-app preview; cancelled share → existing Ready PDF. No “Delivered” status. |
| **P06 — Contact selection, dialer, composers, and navigation** | Common intents support a selected phone/email data picker, `ACTION_DIAL`, `SENDTO` composers, and address-based map intents. A specific selected contact-data item can be read using the grant returned by its picker. [O07] | A phone/email picker is not permission to ingest an entire contact record or address book. Launching a dialer/composer does not prove a call connected or a message was sent. Handlers may be missing. | Choose one specific phone or email at a time; enter other details manually. No broad contact access, SMS/call logs, automatic calls/messages, or location permission. D05 offers Copy details and S29 optional manual outcome entry. CORE does not import communication histories. |
| **P07 — User-selected backup/export/import locations** | The Storage Access Framework supports choosing an existing document or creating one through local or installed document providers without all-files access. [O12] | Provider availability, quota, network, permission persistence, and remote synchronization are outside ServiceLoop's control. A successful local-provider write does not prove another device can recover the file. | S34–S37 use deliberate system selection. Show destination and write/read-back state separately; retry or choose another destination. Ask the user to verify off-device availability. Cancelled selection never imports, restores, or claims a backup. CORE has no automatic cloud-destination promise. |
| **P08 — Do not mistake OS backup for complete product recovery** | Standard Android Auto Backup has a 25 MB app-data cloud quota and conditional operation. Android also documents a Large Backups API, but access is approval-only. Backup rules can include/exclude app data. [O13–O14] | A photographic service history can exceed ordinary automatic-backup capacity; ServiceLoop has no established eligibility for the approval-only program. OS/device transfer behavior is not a controllable multi-device merge mechanism. | The explicit complete package is the recovery contract. Configure business data/attachments consistently so an incomplete OS subset is not presented as a complete recovered service book. Do not claim all OEM transfer behavior can be prohibited. A transferred dataset still needs validation and the one-authoritative-device warning. No dependency on Large Backups approval. |
| **P09 — Verified replacement restore** | User-selected document input plus app-owned files provide a supported route to read a package, validate it in temporary local storage, and adopt a verified local dataset. [O06, O12] | Android does not automatically make a custom database-plus-attachments replacement atomic, nor supply ServiceLoop's compatibility checks. Large archives need real free space. | S35 stages before replacement and retains the old intact state until adoption completes. Reject unsupported/corrupt packages and unsafe archive paths/expansion before applying. Interruption must recover one intact dataset. This is a mandatory implementation/test responsibility, not an API-level guarantee or arbitrary corruption-repair promise. |
| **P10 — Portable protected backups** | Android exposes standard cryptographic facilities and documents recommended authenticated-encryption-capable primitives, including AES-GCM. [O15] | Supported cryptography does not validate a custom archive format, password derivation, key handling, or recovery UX. A secret available only on the lost phone cannot provide portable recovery. | N1 uses a versioned, authenticated, passphrase-protected format with reviewed password-based key derivation and fresh randomness; no original-phone-only key dependency. S34 verifies a round trip; S35 requires the passphrase. Forgotten passphrase has no reset/backdoor. Security review and malformed-file tests are release gates, not claims of certification. Do not adopt the deprecated `security-crypto` library merely because older examples mention it. [O15] |
| **P11 — Accessible touch, text, and preview alternatives** | Android accessibility guidance supports labelled semantics, screen-reader descriptions, and adequate touch targets, including the documented 48 dp minimum convention. [O16] | Automatic framework defaults cannot verify a complex form's reading order, expanded text, checklist meaning, or PDF accessibility. | U6 is acceptance behavior: labelled controls, non-color states, wrapping text, focus order, explicit reorder buttons, and S39 structured public text. Verify with TalkBack and large system text. The PDF is not advertised as a tagged accessibility-certified document. |
| **P12 — Proportionate permissions and truthful Play disclosures** | Google Play restricts sensitive permissions and requires privacy/data-use disclosures reflecting actual app and SDK behavior. Its User Data policy requires a privacy policy, including for apps without personal/sensitive-data access. [O17–O19] | “Offline” alone does not determine all Data safety answers; embedded SDKs and any actual transmission matter. A conceptual map cannot guarantee store acceptance or jurisdiction-specific compliance. | CORE uses no advertising/mandatory analytics, broad files/media/contacts, call/SMS logs, background location, or accessibility automation. S38 provides bundled privacy information plus the release's real public policy URL. Audit dependencies and complete accurate release disclosures; do not invent publisher details, policy URLs, certification, or account-deletion screens for nonexistent ServiceLoop accounts. |

**Maintenance judgment.** These routes avoid a backend, custom camera engine, sensitive-permission workarounds, and automatic cloud sync. They do not remove the testing burden of snapshots, attachments, recurrence reconciliation, backup formats, or readable PDF pagination. The map is plausible as a bounded native product, but it is not evidence for a “several weeks” estimate or a particular multiple of Routine Repeater's effort.

## G2. Source register

All official entries below were consulted for this review on **5 September 2026**. Links are retained in the document so it remains usable outside this conversation. Sources describe platform capabilities/policy; proposed business behavior is defined by this map.

| ID | Source and role |
|---|---|
| B1 | Supplied `ServiceLoop_Functional_App_Map_Prompt_v1_1.md`, edition 1.1, 5 September 2026 — deliverable, detail contract, proposed boundary. |
| B2 | Supplied `ServiceLoop_Research_and_Product_Context.md`, version 0.1, 5 September 2026, including Appendix A — provenance, user intent, historical research, unapproved proposals. |
| O01 | Android Developers — [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission). |
| O02 | Android Developers — [Alarms and scheduling](https://developer.android.com/develop/background-work/services/alarms). |
| O03 | Android Developers — [Persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent). |
| O04 | Android Developers — [Android 15 behavior changes for all apps](https://developer.android.com/about/versions/15/behavior-changes-all), package stopped-state changes. |
| O05 | Android Developers — [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker). |
| O06 | Android Developers — [App-specific storage](https://developer.android.com/training/data-storage/app-specific). |
| O07 | Android Developers — [Common intents](https://developer.android.com/guide/components/intents-common), camera, contact-data selection, communication, and maps. |
| O08 | Android Developers — [Getting a result from an activity](https://developer.android.com/training/basics/intents/result), recreation and separately preserved operation state. |
| O09 | Android API reference — [PdfDocument](https://developer.android.com/reference/android/graphics/pdf/PdfDocument). |
| O10 | Android API reference — [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer). |
| O11 | Android Developers — [Secure file sharing](https://developer.android.com/training/secure-file-sharing). |
| O12 | Android Developers — [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files). |
| O13 | Android Developers — [Auto Backup](https://developer.android.com/identity/data/autobackup). |
| O14 | Android Developers — [Large Backups API Program](https://developer.android.com/identity/data/large-backups), eligibility and approval restriction. |
| O15 | Android Developers — [Cryptography](https://developer.android.com/privacy-and-security/cryptography), standard facilities, recommendations, and deprecated-library caution. |
| O16 | Android Developers — [Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). Cited for behavior, not to select a UI framework. |
| O17 | Google Play — [Permissions and APIs that access sensitive information](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en). |
| O18 | Google Play — [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). |
| O19 | Google Play — [Data safety form guidance](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en). |

---

# H. Separate extension map and exclusions

## H1. LATER candidates — absent from CORE

The entry points below are **hypothetical additions**, not hidden or disabled CORE menu commitments. Their detailed feasibility must be reviewed when selected; the presence of a promising platform route does not approve the extension. None supplies data required by the CORE workflows.

| ID / candidate | Possible entry point and principal actions | New dependency or rule | Why defer / recommended boundary |
|---|---|---|---|
| **L01 — Runtime/hour/cycle servicing** | Equipment → Readings: Add dated reading, correct with reason, inspect reading history. Plan editor: choose one usage unit, interval, initial next threshold. Completion: confirm measured reading and proposed next threshold. | Manual meter observations, units, unknown/stale readings, resets/replacements, and historical corrections. A date does not tell the app that a machine ran. | Useful for some trades but introduces a second recurrence model. No live updating, sensor integration, inferred runtime, or unexplained decrease in a cumulative reading. CORE retains date-only plans. |
| **L02 — Combined date-and-usage rules** | Plan editor → Due by date or usage: set both limits; view which trigger is satisfied; confirm both after actual completion. | L01 plus an explicit “whichever comes first” obligation model and treatment of missing usage observations. | Adds ambiguous due-status and advancement cases. Do not display “not due” merely because an unknown meter reading is below an unobserved threshold. Separate approval needed after a single usage model is useful. |
| **L03 — QR/barcode equipment identification** | Equipment → Assign code; Search → Scan code; scanned match → confirm equipment/site. Unmatched code → search existing or create equipment with explicit confirmation. | Code identity and collision rules, a scanning route/camera permission where applicable, readable physical labels, damaged-label fallback. | Speed enhancement rather than core record integrity. No manufacturer database or automatic equipment specifications. Manual identifier search remains complete. Label printing and bulk label generation would need their own decision, not automatic inclusion. |
| **L04 — Customer acknowledgement/signature** | Completion/report preview → Request acknowledgement: show the exact public content, identify signer, capture acknowledgement/drawing or record declined/unavailable, confirm. | Consent wording, fixed content version, correction invalidation/supersession, identity limitations, added sensitive information. | Some customers need it, but a drawn mark is not verified identity or a certified electronic-signature service. Never treat refusal as proof service did not happen. A later corrected report must not silently carry assent to changed content. |
| **L05 — User-confirmed calendar export** | Booked visit → Add to calendar: preview title/site/time, open the calendar insertion composer, return to the visit. Re-export only after an explicit duplication warning. | A suitable external calendar application and its user-confirmed event creation; another copy of appointment information. Common intents provide an insertion route. [O07] | Convenience, not scheduling truth. No two-way sync, full calendar read/write permission by default, automatic event tracking, or promise that a cancelled/rescheduled ServiceLoop visit updates the exported event. No automatic “calendar saved” claim from launch alone. |
| **L06 — Additional report layouts or specialist fields** | Settings → Report layouts: choose a validated layout; templates → limited trade fields; preview using representative long/missing data. | New field semantics, migration, translations, pagination, customer expectations, and possibly trade-specific reporting rules. | Validate the fixed report with one accessible trade first. Do not turn this into a designer, branching forms engine, manufacturer form catalogue, or regulatory-certification platform. Old reports keep their original layout/values. |
| **L07 — Optional off-device backup automation** | Data → Automatic backup: choose a supported destination, authorize access, choose schedule/network preference, test restore, inspect last verified copy, pause/reconnect. | Provider access duration, credentials/quotas, reliable background opportunities, protection-key handling, retention, failed-transfer recovery, and versioned copies. | Valuable but not a trivial switch. A provider or approval-only backup program is not presumed available. It must remain backup—not live shared editing, merging, or assurance that the other device stopped changing data. Manual S34/S35 remains independently complete. |

**Commercial licensing — OPEN, not an extension specification.** The proposed CORE exposes all mapped records, reports, and exports without a commercial state machine. A future trial/unlock proposal must separately define current Play billing compliance, purchase restoration, offline access, refunds/revocation behavior, and continued access to existing records/exports. No price, duration, asset limit, subscription, billing API, or paywall has been approved here.

## H2. OUT of this product boundary

| Excluded area | Exact boundary retained |
|---|---|
| Quoting, invoices, payments, accounting, payroll | Work/parts descriptions only; no prices, taxes, debt ledger, payment status, invoices, or financial reports. |
| Sales pipelines and broad CRM | Customer contact details, manually entered contact events, and service-related follow-ups only. No leads, opportunities, marketing funnels, campaigns, or customer segmentation. |
| Stock purchasing and inventory control | Parts used are descriptions/quantities on a visit. No stock balance, purchasing, reservations, suppliers, or reorder rules. |
| Dispatch, team permissions, labor management | One technician; no assignments, roles, timesheets, payroll, shift status, attendance tracking, or workforce scheduling. |
| Simultaneous editing, cloud sync, multi-technician collaboration | A separate product/architecture decision. Neither backup, restore, CSV export, nor a calendar handoff is synchronization. This exclusion applies to the proposed product, not a claim about every future ServiceLoop version. |
| Live location, route optimization, route rounds | An explicit map handoff to a supplied site address only; no tracking, automatic check-in, optimized stops, or GPS-derived proof of attendance. |
| Customer portals and automatic communication | User-confirmed external app handoffs only. No portal accounts, customer scheduling pages, campaign sending, inbound-message ingestion, SMS/call-log import, or delivery tracking. |
| Sensors, predictive maintenance, manufacturer databases | Technician-entered observations and files only; no telemetry, automatic diagnosis, inferred service interval, proprietary specifications, or equipment integrations. |
| Regulatory certification | A customer-ready record of entered work, not a safety certificate, legally tamper-proof audit trail, qualified electronic signature, or assertion that a trade's required inspection has been met. |
| General project management or configurable business platform | Service-linked follow-ups and fixed simple forms only; no arbitrary task boards, workflow automation, custom entity types, scripting, or generic dashboard builder. |

---

# I. Coverage audit and review decisions

## I1. Traceability of the requested CORE

The letters below refer to the companion prompt's CORE areas, not this document's section headings. The complete action definitions remain in Section D; this table identifies the principal paths rather than replacing those definitions.

| Requested capability | Principal screens and representative actions | Rules / failure coverage |
|---|---|---|
| **A — First use and business profile** | S01-A01–A03; S31; D10 | U2–U3; R10–R11; empty app, optional setup, denied notification request. |
| **B — Home and navigation** | S02; S03; S04; S05; S30 | U1; R01; E01–E02; summary destinations/count units, no-results, unavailable target, failed-read distinction. |
| **C — Customers and sites** | S06–S09; D02; D03; D05 | R07/R10; default site, explicit inheritance, restricted reassignment/deletion, archived context, contact handoff failure. |
| **D — Equipment** | S10–S12; D04; D13; S40 | R05/R07; absent identifiers, duplicate warning, attachment failure, relocation dependencies, retired/read-only history. |
| **E — Service plans** | S13–S14; D06 | R01–R03/R07/R08; initial due, separate plans, interval edits, pause/resume/end, leap dates, occupied obligations, historical entry. |
| **F — Contact and booking** | D05; S18–S19; S29 | R01/R04/R08/R10; manual outcomes, one-site grouping, reschedule/cancel without completion, no handler, cancelled composer. |
| **G — Inspection templates** | S15–S17; D08; S22 | R02/R05/R07; item types/requiredness, duplication/reorder, archived assignment, frozen work snapshots. |
| **H — Working visit** | S20–S23; D04; D09 | U3; R04/R11; one-off work, multi-machine changes, saving/checkpoints, interruptions, missing photographs, abandoned/unfinished work. |
| **I — Completion and next service** | S24-A01–A11; S25; S26 | R02–R06; explicit per-line outcomes, fulfillment opt-in, partial work, confirmed next dates, idempotent finalization, PDF failure. |
| **J — Follow-ups** | S27–S29; D07; staged S28 from S23/S24/S26 | R02/R04/R07; contact versus corrective context, date changes, explicit closure, staged versus live tasks, no implicit recurrence change. |
| **K — History and reports** | S25–S26; S39–S40; D04 | R05–R06/R10; original snapshots/PDF bytes, correction/void versions, selected public images, accessible preview, missing file/share failure. |
| **L — Reminders** | S32; D10; S19 appointment override | R01/R08; E01–E02/E07–E09; approximate alerts, summaries, denial/force-stop, channel state, no hidden snooze or completion action. |
| **M — Data ownership** | S33–S37; D11–D13 | R09/R11; E10–E15; full versus incomplete backup, passphrase loss, provider verification, invalid import, failed/destructive restore, missing files. |
| **N — Settings, privacy, help** | S30–S32; S38; U6 | R10–R11; P11–P12; actual enumerated settings, accessible controls/text, no account/analytics dependencies, offline help and external-policy failure. |

## I2. Structural and consistency review

**Navigation completeness.** The map has three primary destinations, 40 identified destination responsibilities, 13 reusable dialogs/sheets, and 357 explicitly identified action entries. The 40 include list/detail/editor/work/recovery/report responsibilities, not 40 bottom-navigation sections or a promise that each needs a separate visual layout. Section B supplies routes to every destination; local entry/back behavior and resulting states are specified in Section D. A restore or stale deep link cannot leave a screen attached to the wrong dataset.

**Persistence completeness.** Actions distinguish input buffering, explicit Save, durable fieldwork autosave, finalization, generated files, and external handoffs. Staged follow-ups belong to their parent until the documented commit. Selection/preview does not mutate live data. Finalization, correction, import, and restore have a defined success boundary and failure outcome. Read failures and missing files are not disguised as empty or successful states.

**State consistency.** R01–R06 define one recurrence policy and one correction policy. Booking, contact, reminder dismissal, PDF generation, and share actions have no service-fulfillment side effect. Every due obligation may be fulfilled at most once per counted completion; partial outcomes are retained. Archival, relocation, plan edits, and downstream claims cannot silently invalidate working/final records.

**Privacy consistency.** Internal fields cannot become public through a generic report option. Customer reports, plaintext CSV exports, and protected full backups deliberately contain different data. Issued files cannot be recalled by modifying local history. Permission denial has a bounded fallback instead of an undocumented sensitive-permission workaround.

**Boundary consistency.** Every CORE journey runs without L01–L07, licensing, a customer portal, sensor, proprietary database, cloud account, calendar integration, or a second technician. Supporting N1–N3 are explicit recommendations. The map defines ordinary operating limits; it does not prove market preference, effort estimates, legal sufficiency, or release quality.

## I3. Critical acceptance cases implied by this proposal

These are **requirements for a future implementation**, not tests already run against an app.

| Area | Required demonstration before calling the behavior dependable |
|---|---|
| Recurrence and partial work | Month-end/leap-year examples; next dates still overdue after backdated service; two plans on one machine; one fulfilled/one partial; one current obligation claimed once; retry finalization without second advancement. |
| Corrections and dependent work | Latest counted date corrected; older record corrected after later valid service; later manual due override retained; paused/ended plans stay so; downstream Booked/Working claim blocks only schedule-changing commit; void produces an identifiable new notice without rewriting old bytes. |
| Historical stability and public content | Rename/move customer/site/equipment, edit/archive templates, change business logo, and still reproduce old captured content; no internal/access notes, source metadata, or unselected photos leak; large answers and many equipment sections paginate without clipping. |
| Interruptions and storage failure | Recreate/terminate during camera handoff, autosave, finalization, PDF generation, backup, and restore; show the last acknowledged state; preserve independent earlier saves; never create a false empty database or half-switched dataset. |
| Backup and import adversarial inputs | Wrong passphrase, truncated archive, malformed references, unsafe archive paths, excessive expansion, missing declared/undeclared files, newer format, insufficient free space, lost provider access, inconsistent CSV keys, duplicate import, spreadsheet-formula content. All must produce a visible bounded outcome. |
| External platform conditions | Notifications denied/revoked, channel blocked, ordinary reboot, force-stop/reopen, changed time zone/clock, missing camera/composer/viewer/provider, cancelled shares, offline cloud photo/destination. Business data must remain meaningful. |
| Accessibility and directory scale | TalkBack reading/action order, large text, keyboard access, explicit reorder controls, long names/addresses, empty/no-results distinctions, many records, and the stated import/photo guardrails without silent truncation. |

The release gate for backup protection includes an actual off-device restore with only the package and passphrase. A successful save to the original phone is not a substitute.

## I4. Consequential owner decisions

| Decision for review | Recommended default |
|---|---|
| **1. First trade and report expectations** | Pilot with one accessible trade using its real multi-machine visits and customer-facing report requirements. Keep the conceptual entities general; do not include certification claims or specialist forms before that review. Launch country/languages and actual publisher privacy details remain unselected. |
| **2. One authoritative device** | Keep the local-only, one-business/one-technician boundary for this product. Test whether a complete manual backup/restore workflow is acceptable before adding any automatic off-device service. Reject silent multi-device use rather than pretending restore merges edits. |
| **3. Scheduling and visit semantics** | Adopt completion-date-plus-interval, one current obligation per plan, one site/actual date per visit, and explicit per-service fulfillment. A genuine need for fixed-anniversary contracts or multiday work should trigger an explicit scope revision, not a hidden variation. |
| **4. Historical correction and retention** | Preserve final records and original PDF versions; correct/void visibly and reconcile current dates. Keep the conservative archive/delete restrictions. Confirm that this retention model suits the selected operating context before release; the app makes no regulatory retention claim. |
| **5. Recovery safeguards N1–N3 and initial migration limits** | Include protected portable full backups, explicit correction/reconciliation, and clearly incomplete recovery copies; retain the create-only fixed directory import. Do not cut these safeguards to add cosmetic dashboards or more integrations. Review passphrase usability with the pilot. |
| **6. Commercial layer and scope commitment** | Leave price, trial, purchase mechanism, and paywall undecided until the complete workflow has been reviewed. Treat this full CORE as a meaningful business app, not a small reminder app with PDFs attached; approve/cut features deliberately before estimating or implementing it. |

**End status: proposed app map ready for review.** The bounded workflow is specified for product discussion, including failures and recovery; no implementation, customer validation, regulatory certification, frozen scope, or commercial approval is asserted.
