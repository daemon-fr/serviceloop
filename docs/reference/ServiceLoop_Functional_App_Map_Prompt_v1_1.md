# ServiceLoop — Complete Conceptual App Map

**Edition:** 1.1 · **Updated:** 5 September 2026  
**Change from the original prompt:** Added the context-reading and interpretation section below. The functional task and proposed feature scope are unchanged.

## 0. Read the research context before performing this task

Read the accompanying **`ServiceLoop_Research_and_Product_Context.md` completely**, including its evidence-status notes, differences between the two assessments, open decisions, and Appendix A containing the other model’s original proposal.

The two documents have separate jobs:

- **Context file:** The research background, the user’s reason for exploring ServiceLoop, source pointers, competing judgments, scope rationale, and what has not been approved.
- **This prompt:** The app-map deliverable, the proposed design boundary, the required level of detail, and the output format.

Use the user’s current explicit instructions first. Preserve actual explicit approvals when supplied; do not manufacture them from a prior assistant proposal. For unresolved design choices, use the proposed baseline in this prompt as the starting point for the map and label it accordingly. Historical research and the other model’s broader V1 are background, not automatic additions to CORE.

In particular, do not silently reinstate runtime-based recurrence, signatures, cloud synchronization, or broad calendar integration simply because they appear in the original proposal. Explain any new recommendation and its scope effect. Do not transfer the original MixedAsset score or the other model’s 88/100 into an assertion of validated ServiceLoop demand.

Maintain the distinction between USER-STATED intent, REPORTED EVIDENCE from earlier research, ANALYST JUDGMENT, PROPOSED BASELINE, and OPEN decisions. The archival links are not newly verified facts; perform the targeted official Android/Play checks required by Section 4, not a new broad market-opportunity study.

The optional `research_archive/Android_opportunity_screening_2026-09-05.md` contains the original screening ledger. It is background, not an additional feature backlog, and it is not required to understand the context file. Do not assume access to an earlier chat or to Routine Repeater’s repository. If a required attachment cannot be read, state that limitation and do not pretend to have read it.

After reading, produce the requested app map rather than stopping at an acknowledgement or a fresh research summary.

## 1. Your role and the requested deliverable

Act as a senior mobile product designer, business analyst, and pragmatic Android product architect.

Create a complete, structured **functional app map for ServiceLoop**, an Android-first recurring customer-equipment service manager for independent technicians.

The deliverable must describe the app **screen by screen, button by button, menu by menu, and function by function**, including the important behavior that happens without a button press.

This is a product-design document for review before implementation. It is NOT a market-research report, a high-level feature list, a coding prompt, a technical architecture, or a visual design exercise. Do not write code or generate images. Use a navigational text tree, concise descriptions, and readable tables where helpful.

Keep the perspective primarily that of the technician using the product. Explain technical matters only when they affect what the app can honestly promise, what the user must do, or how a failure is handled.

**“Everything” means every behavior of a clearly bounded, coherent product—not every imaginable feature of field-service software.** Be exhaustive inside the boundary, not expansive outside it.

## 2. Product premise and planning assumptions

Working name: **ServiceLoop**. The name is provisional.

Product proposition:

> An offline service book for independent technicians: know which customer equipment needs attention, record the work, and leave the customer with a professional report.

The target user installs or services equipment belonging to customers. Examples include fitness equipment, water-treatment equipment, coffee machines, generators, gates, and workshop machinery. These examples are context, not a requirement to build specialist modules for every trade.

The central problem is maintaining the connection between future service obligations, customer contact, actual fieldwork, and its recorded outcome.

Core relationship:

**Customer → site → equipment → service plan → service visit → service record/report → next service obligation.**

A customer can have several sites. A site can contain several machines. One machine can have several distinct service plans. A visit can cover several machines and service plans at one site. Preserve these distinctions without exposing unnecessary administrative complexity.

Use the following proposed planning boundary:

- One business and one technician using one authoritative local dataset on one device.
- Core business functions work offline, without a mandatory ServiceLoop account or a backend.
- External actions, purchases, and backup destinations may have their own availability requirements; describe those honestly rather than promising that literally everything works offline.
- Development and ongoing maintenance must remain realistic for one technically capable developer using AI-assisted coding.
- Routine Repeater is a scope reference for a focused, polished Android app—not a requirement to reuse its scheduling model, toolchain, or implementation.
- The core product is a service book, not a CRM, accounting package, team-dispatch system, or compliance-certification platform.

This is a **proposed baseline**, not an already-approved specification. Identify your design assumptions and any recommended changes explicitly. Do not present earlier suggestions, including pricing, as decisions the user has already made.

Do not stop for a preliminary questionnaire. Choose practical, reversible defaults where possible, explain them briefly, and collect consequential unresolved decisions at the end.

## 3. Scope control

Classify capabilities as:

**CORE:** Included in the proposed complete first product. Map these exhaustively, including their ordinary operational edge cases. Do not use “MVP” to omit essential recovery, reporting, or data integrity.

**LATER:** A potentially useful extension, explicitly absent from CORE. Give it a separate compact functional map with its entry point, principal actions, dependencies, and reason for deferral.

**OUT:** Deliberately excluded. State the boundary without designing an entire excluded subsystem.

Every CORE capability must serve a concrete task in the service workflow or make that workflow dependable. A menu option is a product commitment, not free decoration. Do not add dashboards, statistics, customization, or integrations merely because other business apps have them.

The CORE must not depend on anything classified LATER or OUT. Any additional capability you propose must be labelled as a new recommendation with a short justification.

### Proposed CORE to account for

**A. First use and business profile.** Starting with an empty app; business and technician details; optional report logo; explaining local storage and recovery; contextual permission requests. Do not require unnecessary setup before the first useful action.

**B. Home and navigation.** Overdue and due-soon work, booked visits, unfinished visits, outstanding follow-ups, and relevant search/filter/sort actions. Define the destination of every summary card and count. Choose a small navigation structure rather than a separate top-level section for every entity.

**C. Customers and sites.** Create, view, edit, search, and archive customers and service locations; contact details; site access notes; a simple default-site path for customers with one location. Describe linking, selection, reassignment, and deletion restrictions where relevant.

**D. Equipment.** Name/type, make/model, serial or internal identifier, photograph, notes, and assigned site; equipment history; search; editing; retirement/archive. Decide how missing identifiers and potential duplicates are handled. Explain what changing an equipment location does to existing records and future work.

**E. Service plans.** Named date-based recurring services, intervals, an initial next-due date, optional known last-service information, and an optional inspection template. Support separate plans for different services on the same machine. Include editing, pausing, resuming, and ending a plan without erasing history.

**F. Customer contact and booking.** Open an appropriate external app to call, compose a message/email, or navigate to a site. Record contact and a follow-up date manually. Book, reschedule, and cancel a visit. Associate the relevant equipment/plans without turning an appointment into proof that service occurred. Do not claim to know that a call connected or a message was delivered merely because another app opened.

**G. Inspection templates.** Create, duplicate, edit, reorder, and archive reusable templates. Keep item types simple: status result, short text, and numeric reading with a unit. Status results distinguish OK, issue found, not applicable, and not checked. Decide required/optional behavior explicitly. No branching form builder or automatic safety diagnosis.

**H. Working visit.** Start or resume a visit; select one or several machines and the relevant services; execute checklists; capture findings, work performed, photographs, and parts used as text/quantity records. Autosave working progress and make save status understandable. Address interruptions, changes to the selected work, and unfinished work. Decide how to record a legitimate one-off visit without forcing the user to invent a recurring plan.

**I. Completion and next service.** Review each machine/service outcome, identify which obligations were actually fulfilled, confirm the actual service date, and confirm the proposed next dates. Separate completed inspections, equipment findings, unfinished maintenance, and corrective follow-up. Finishing a visit must not blindly mark all selected work complete.

**J. Follow-ups.** Keep customer-contact follow-ups and corrective work distinguishable. Link each to its actual customer/site/equipment context. Include opening, updating, rescheduling, resolving, or cancelling it as appropriate. Do not turn this into a generic project-management system.

**K. History and reports.** Equipment/site/customer service history; finalized service records; explicit corrections; one fixed customer-ready PDF layout per visit with multiple equipment sections. Include report preview, selected photographs, customer-visible versus internal information, saving, and sharing. Historical records and issued reports must not silently change when templates or contact details are edited.

**L. Reminders.** User-controllable local reminders for relevant due work, appointments, or follow-ups where justified. Define lead times, summary versus individual behavior, opening an item, snoozing or dismissal where offered, and operation when notifications are unavailable. Reminders supplement the due-work dashboard; they do not define business truth.

**M. Data ownership.** Complete backup/restore including attachments and finalized reports; visible last-backup information; backup reminders; portable CSV export; initial customer/equipment import through a fixed CSV template with preview, validation, and a deliberate duplicate policy. Distinguish a readable export from a complete restorable backup.

**N. Settings, privacy, and help.** Enumerate the actual settings rather than writing “standard settings.” Include the relevant business details, reminder settings, data operations, and concise help about local storage and external sharing. Decide sensible accessibility behavior, labels, text scaling, and icon alternatives. Avoid unrelated customization or mandatory analytics.

### LATER candidates to evaluate separately

Runtime/hour/cycle-based servicing; combined date-and-usage rules; QR/barcode identification; customer acknowledgement/signature; user-confirmed calendar export; additional report layouts or specialist fields; optional cross-device backup automation.

Do not imply that local runtime records update themselves. Do not equate calendar export with two-way synchronization, backup with synchronization, or a drawn signature with a legally certified electronic-signature service.

Commercial licensing is not settled. If you include a trial/unlock flow, isolate it as a proposed commercial layer, verify its Android feasibility, and explain offline behavior, purchase restoration, and continued access to existing records and exports. Do not invent a price or paywall as an approved requirement.

### OUT for this product boundary

Quoting, invoices, payments, accounting, payroll, sales pipelines, stock purchasing, technician dispatch, team permissions, simultaneous editing, live location, route optimization, customer portals, automated messaging campaigns, full communication-history ingestion, sensor integrations, predictive maintenance, proprietary manufacturer databases, and regulatory-certification claims.

Cloud synchronization and multi-technician collaboration require a separate scope decision, not an inconspicuous “Sync” switch in Settings.

## 4. Android feasibility is a condition of inclusion

Do not simply assume that a feature is feasible because it sounds like something a phone could do.

Before including a capability in CORE, establish that its promised behavior has a realistic path using supported Android capabilities, proportionate permissions, current Google Play requirements, and a maintenance burden appropriate to this project.

Use current **official Android documentation and official Google Play policy/documentation** to verify material platform-dependent claims. State the verification date. Do not rerun market research or browse to justify ordinary product-design choices.

Check particularly where relevant:

- Notifications, background execution, timing, permission denial, and application lifecycle.
- Camera/photo selection, attachment persistence, file access, PDF creation/viewing, and sharing.
- Contact selection versus broad contact access; opening a dialer or composer versus performing communication automatically.
- Backup destinations, restore behavior, storage limits, and any claimed automatic off-device operation.
- Purchase/unlock behavior, but only if that optional layer is included.

Do not promise exact delivery, guaranteed execution after force-stop, automatic proof of message delivery, unrestricted storage access, reliable third-party app availability, universal PDF-viewer availability, or automatic cloud recovery without an appropriately supported mechanism.

Do not solve ordinary service reminders through special permissions or sensitive services unless genuinely necessary and justified. Do not use accessibility automation, background location, SMS/call-log access, or similar capabilities as shortcuts around normal platform boundaries.

For a material constraint, explain briefly:

**User-visible behavior → practical implementation route → limitation → fallback.**

Keep these notes short. The app map must not become an Android tutorial. Do not lock SDK, library, or toolchain versions unnecessarily, or claim that a conceptual review certifies Play approval or behavior on every device.

If the required behavior cannot be justified, narrow it, defer it, or reject it explicitly. Do not leave unsupported promises in CORE under a vague “implementation detail” label.

## 5. Required level of detail

Give every screen, significant dialog/sheet, and meaningful action a stable, readable identifier, such as `S04 Equipment detail` and `S04-A03 Archive equipment`.

For every destination, specify:

**Purpose and entry points.** Why it exists, how it is reached, and where Back returns.

**Visible content.** The actual information, summaries, statuses, and relationship links shown.

**Inputs.** Field labels; required/optional status; defaults; selection options; validation; and relevant consequences of leaving a field empty. Avoid collecting data without a use.

**Controls and actions.** Enumerate every button, actionable row, menu item, switch, filter, sort option, contextual action, and meaningful system handoff. Show which actions are hidden or disabled and why.

**Behavior.** For each action, state what happens, what is saved or changed, the destination or resulting state, any confirmation/feedback, and relevant effects on dates, reminders, reports, or related records.

**Cancellation and failure.** Explain what remains unchanged when a dialog or system flow is cancelled, and how a user recovers from a relevant failure.

**Drafts and navigation.** Define autosave versus explicit Save, Back behavior, abandoning work, and visible unsaved/saving/saved states where needed. Do not use a misleading Cancel button on an already-autosaved form.

**Screen states.** Include meaningful empty, no-results, archived, read-only, unavailable, and error states. Cover permission denial, missing external handlers/files, and storage failure where they actually apply. Avoid repeating irrelevant error boilerplate on every screen.

A compact action table can use:

`ID | Control/label | Available when | Result/change | Cancel/error behavior`

Define repeated behavior once, then reference it explicitly from every applicable screen. This may include a shared photo picker or delete-confirmation pattern. Do not hide domain-specific differences behind generic “CRUD,” “manage,” “standard actions,” or “etc.”

Specify record deletion only where the proposed product actually permits it. Otherwise state why the action is absent and what safe alternative exists.

## 6. Business rules that must be resolved

These rules are part of the app map, not optional implementation notes:

1. **Due date versus appointment:** booking, postponing, cancelling, contacting a customer, or snoozing a reminder does not complete service or silently change its due date.
2. **Completion semantics:** a visit, an inspection, a service-plan obligation, an equipment condition, and a corrective follow-up are different things. Define valid transitions and their consequences.
3. **Recurrence:** start with actual completion date plus interval, visibly confirmed and manually overridable. Explain initial setup, month-end/leap-year behavior, backdated records, manual changes, and preventing duplicate advancement. Fixed-anniversary behavior is not silently included.
4. **Partial work:** show how multiple machines and multiple plans are handled when only some work is completed. Explain what remains overdue, what advances, and what appears on the report.
5. **History:** define when working checklist content is captured, what becomes fixed at finalization, how corrections work, and how existing PDF versions remain identifiable. Do not invent compliance-grade audit claims.
6. **Record lifecycle:** resolve archival/deletion, customer or site changes, moved or retired equipment, paused/ended plans, edited templates, and their effects on active visits, reminders, and history.
7. **Local-first limits:** identify what is durably saved, what remains only a draft, what survives interruptions, what depends on an external application, and what can be recovered only from a backup.
8. **External handoffs:** opening a composer, navigation app, viewer, or share sheet is not proof that the intended external action succeeded. Use truthful status labels.
9. **Import and restore:** choose clear duplicate, invalid-row, compatibility, and replacement rules. Do not quietly turn simple restore into multi-dataset merging. Explain destructive consequences before confirmation and what happens if the operation fails.
10. **Privacy and report content:** prevent internal/access notes and unselected photographs from leaking into customer reports by default. Explain the limits of control over files already exported or shared.

When choosing a policy, give the recommended rule and a brief reason. Do not leave multiple incompatible options scattered through the document.

## 7. Required output structure

### A. Product boundary and design assumptions

A brief statement of the proposed CORE, explicit assumptions, and important departures from this prompt. No lengthy viability introduction.

### B. Complete navigation map

A readable text tree of primary destinations, detail/edit screens, important dialogs/sheets, and system handoffs. Distinguish navigation destinations from actions. Cross-reference screen IDs.

### C. Conceptual information model and vocabulary

Explain the records, relationships, and status terms in plain language. No database schema, programming classes, or framework discussion.

### D. Screen-by-screen functional map

The main deliverable. Use the detail contract in Section 5 and account for every CORE area in Section 3. Include full menus and meaningful settings, not just the most prominent buttons.

### E. Automatic behavior and lifecycle map

List important behavior not initiated by an explicit in-app button: refreshing due states, reminder processing, interrupted-session recovery, template/history preservation, and relevant date/time or application-lifecycle changes. Describe triggers, visible outcomes, and constraints without low-level engineering detail.

### F. Cross-screen rules and critical journeys

Consolidate the important state transitions and demonstrate the map with realistic end-to-end paths. Reference existing action IDs rather than repeating every screen specification.

At minimum cover:

- First customer/site/machine, two service plans, and the first future obligation.
- Due equipment, customer contact, a booked visit, postponement, and eventual completion.
- Several machines in one visit, partial service, an identified fault, and corrective follow-up.
- Interrupted fieldwork with photographs, resumption, and honest save/recovery behavior.
- A changed template or customer address after a report has already been finalized.
- A backdated service entry or correction without accidentally advancing a plan twice.
- Notifications denied, an unavailable external application, or a cancelled share operation.
- Importing existing records and restoring a full backup, including important failure paths.

### G. Brief feasibility and dependency notes

A compact table covering only material Android/platform dependencies. Include supported route, limitation, fallback, and official citations. Separate verified platform facts from your design recommendations.

### H. Separate extension map and exclusions

Keep LATER and OUT visibly separate from CORE. Show why each extension is deferred and what extra user workflow or dependency it introduces. No hidden dependencies back into CORE.

### I. Coverage audit and review decisions

Provide a compact traceability table linking each CORE capability to its screens/actions and relevant rules. Check for unreachable screens, missing outcomes, actions that do not persist their result, contradictions, and dependencies on excluded systems.

End with a short list of genuinely consequential decisions for the product owner, each accompanied by your recommended default. Label the result as a proposal ready for review, not a frozen specification.

## 8. Quality bar

The map should let the product owner answer:

> “Where do I go, what do I see, what can I press, what happens next, what gets recorded, and what happens when something goes wrong?”

A reader should not have to invent missing behavior before they could sketch the screens and evaluate the complete service workflow.

Keep individual explanations compact, but do not trade away coverage. Do not replace sections with “similar to the above,” unexplained placeholders, or promises to finish later. Share work-in-progress updates sparingly rather than interrupting the map with commentary.

Produce the proposed app map itself. Do not write implementation code, reopen the market-opportunity ranking, or begin by asking whether to proceed.
