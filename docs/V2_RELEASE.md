# Meds Widget V2 test release

V2 expands the V1.1 widget-first tracker into a fuller medication routine app while keeping the same privacy and safety boundaries. It remains a personal tracking utility and does not provide medical advice, dosing recommendations, drug-interaction guidance, or treatment decisions.

## Included in V2

- Four independent daily slots: Morning, Afternoon, Evening, and Night.
- Custom labels, optional countdown duration, and optional reminder time per enabled slot.
- Optional medicine nickname and discreet widget naming.
- Optional course start and end dates.
- Today states for Pending, Taken, and Skipped.
- App-only Skip and Undo with immutable history events.
- History and adherence summaries.
- Existing 2×2 single-medicine widget and 4×2 all-medicines widget, plus a 4×4 dashboard widget.
- Widget actions remain check-only: a widget can mark a pending dose taken, but it cannot undo a taken or skipped dose.
- Countdown start remains available from app/widgets; cancellation and restart remain app-only.
- Widget cache/rollover supports all four slots and preserves privacy-safe display names across reboot, process death, and logical-day rollover.
- Device-local reminder scheduling using Android WorkManager. Android 13+ requires notification permission before reminders can be shown.
- Optional supply tracking. A taken dose subtracts the configured units-per-dose; app Undo restores a previously taken dose's amount; Skip does not change supply. Refills add stock from the Today screen.
- Low-supply status when the current supply is at or below the configured threshold.
- Settings shortcuts for pinning the 2×2, 4×2, and 4×4 widgets when supported by the launcher.
- Local CSV export of medicine setup and dose history using Android's secure FileProvider/share sheet.
- Firebase Authentication, Firestore offline persistence, immutable audit history, and Firebase App Distribution remain the cloud/distribution foundation.

## No-cost boundary

V2 remains within the project's Firebase Spark/no-billing constraint. Reminders, widget pinning, supply calculations, and CSV export are device-side features. No Cloud Functions, Cloud Storage, paid APIs, ads, subscriptions, or billing-account dependency is introduced by V2.

## Supply behavior

The existing `supplyInitialUnits` medicine field is treated by V2 as the current recorded remaining supply. The medicine editor therefore labels it **Current supply**. This avoids a second inventory schema while preserving backward compatibility with existing V1 data.

Supply is advisory tracking data and never blocks a dose check. If a dose is logged after the recorded stock reaches zero, the remaining value may go negative; the user can correct it with a refill. This is preferable to rejecting a medication history event because inventory metadata is stale.

## Reminder behavior

Reminder times are independent from countdowns. A reminder is a recurring device-local notification for a configured slot; a countdown is an explicitly started timer such as "take this two hours after food." Reminders do not automatically mark doses taken.

WorkManager delivery is intentionally battery-friendly and may not be exact to the second. V2 does not request Android exact-alarm special access.

## Upgrade behavior

The V2 release keeps the existing Android application ID and release signing identity. A correctly signed V2 APK should install as an **update over V1.1**, preserving app data and existing widget configuration. Do not uninstall the old app unless an update installation genuinely fails and the cause has been investigated.

## Required physical Samsung validation

Automated CI cannot close physical Samsung validation. After installing the Firebase App Distribution build, verify all of the following on a real Samsung phone:

1. Update installs over the existing version without uninstalling it.
2. Existing 2×2 and 4×2 widgets remain present; add and test the new 4×4 dashboard.
3. Morning, Afternoon, Evening, and Night rows appear correctly in app/widgets when enabled.
4. Resize each widget and verify readable text at normal and larger Android font scales.
5. Widget check works for every slot and cannot undo a taken dose.
6. App Skip works; a skipped dose cannot be checked from a widget until it is undone in the app.
7. Countdown start works from app and widget; READY state and app-only cancel/restart work.
8. Configure a near-future reminder, grant notification permission, and verify a notification is delivered and opens the app.
9. Disable notification permission and verify the app fails safely without crashing.
10. Enable supply tracking, check a dose in the app, and verify supply decreases by units-per-dose.
11. Undo that dose in the app and verify supply is restored.
12. Check a dose from a physical home-screen widget and verify supply also decreases after sync.
13. Skip a dose and verify supply does not change.
14. Add a refill and verify current supply increases.
15. Trigger low supply and verify the Today screen displays the warning.
16. Export CSV and verify Android's share sheet opens and the exported file contains medicine/history rows.
17. Test nickname and hidden widget naming so the real medicine name is not shown on widgets when privacy mode is enabled.
18. Turn Wi-Fi/mobile data off, perform supported actions, reconnect, and verify Firestore reconciliation.
19. Reboot the phone without opening the app first; verify widgets still render all configured slots and actions recover correctly.
20. Exercise multiple widget instances for different medicines and verify no cross-medicine action/configuration leakage.

Until those checks are completed, release status remains `REQUIRES_PHYSICAL_SAMSUNG_VALIDATION`.

## Explicitly deferred

The following are not part of this V2 test release: caregiver/household sharing, Wear OS, Health Connect, prescription OCR/barcode scanning, drug-interaction analysis, and medical/dosing advice.
