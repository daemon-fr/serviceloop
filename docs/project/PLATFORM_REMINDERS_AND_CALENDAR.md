# V16 Service — Reminder and Calendar Contract

## Local reminders

Reminders are optional, local, and off by default. One persisted Due-soon horizon is shared by Home, Due services, and reminder summaries. Scheduling uses the injectable business clock/time zone, invalidates at the next business-local midnight and on foreground/time/zone changes, and does not poll.

The app schedules approximate one-shot alarms. Notifications use privacy-safe aggregate summaries and appointment alerts. Dataset-scoped pending intents suppress duplicate/stale reminders. Reboot, package replacement, time, time-zone, and process changes reconcile requested state with current business rows. Notification delivery requires platform permission and explicit local enablement; notification state never changes service completion.

Device-local reminder delivery intent is excluded from portable Recovery. Dataset replacement/erase follows the adopted rule and resets local delivery Off. Restoring business data does not silently claim notification delivery.

## Android Calendar projection

Calendar is an optional one-way local projection through Android Calendar Provider, off by default. The user deliberately grants platform permission and selects a writable calendar. No Calendar content is synchronized to a server.

Only timed Booked Visits create managed events. One Visit has at most one managed link. Schedule/details update the same event. External deletion is shown as Missing; recreation is deliberate. Per-Visit Remove suppresses projection; Add explicitly reenables it. Working, finalized, and participation-complete Visit history is retained. Cancellation or withdrawn Dispatch work removes a managed event where possible.

Calendar selection and event identity are device-local, stored in `noBackupFilesDir`, scoped to the current dataset ID, and excluded from Recovery. Dataset replacement/erase turns integration Off without deleting unrelated existing events. Room-driven reconciliation reads current Visits, Customers, and Sites. Provider failures remain visible/pending; the projection does not claim attendance, completion, or service delivery.

## Evidence boundary

Calendar provider writes, reminder permission, and notification posting are Android system behavior. They need device evidence when those handoffs are part of acceptance; domain tests alone do not prove them.
