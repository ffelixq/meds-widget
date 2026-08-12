# Samsung One UI validation

> **REQUIRES_PHYSICAL_SAMSUNG_VALIDATION**

This checklist has not passed until all 34 steps are performed on an actual
Samsung phone using the signed release APK. Emulator, Robolectric, Compose, and
Glance tests do not validate One UI's widget picker, grid sizing, scrolling,
widget stacks, resizing, font scaling, or launcher refresh behaviour.

## Preconditions

- Use a release APK signed with the same release key intended for future
  updates.
- Confirm the APK SHA-256 against `VALIDATION_REPORT.md`.
- Use a test Firebase account and non-medical sample medicine names.
- Ensure the phone runs Android 8.0 or newer with a normal Samsung One UI home
  screen.
- Record phone model, Android version, One UI version, launcher version, font
  scale, APK version, commit SHA, date, and tester.
- Capture screenshots only if they contain no private account or medicine data.

## Required 34-step checklist

1. Install the signed release APK from the Firebase App Distribution tester
   link. If Android blocks it, open the prompt's **Settings**, allow installs
   from the Firebase App Tester/browser source for this installation, return,
   and select **Install**. Confirm Android identifies the app as **Meds
   Widget**.

2. Open Meds Widget, choose **Create an email account**, enter a test display
   name, email, and password, and complete email/password sign-up. Confirm the
   signed-in main screen opens without exposing a raw Firebase error.

3. Open **Settings**, select **Sign out**, then sign in again with the same
   email and password. Confirm the same account data is shown and no prior
   account's cached widget content flashes.

4. Use the email-authenticated session to confirm navigation among the main
   screen, history, and settings, then sign out again in preparation for the
   other provider.

5. Select **Continue with Google**, choose the intended test Google account in
   Android Credential Manager, and confirm Google sign-in completes. If this is
   a new Firebase user, confirm a display name is present or can be set in
   Settings.

6. Add **Medicine A** with both **Afternoon** and **Night** enabled. Use custom
   labels such as **After lunch** and **Before bed**, save, and confirm both
   unchecked rows appear.

7. Add **Medicine B** with exactly one enabled slot, save, and confirm only that
   slot appears in the app and previews.

8. Long-press an empty area of the Samsung home screen.

9. Select **Widgets** from the One UI home-screen menu.

10. Find and expand **Meds Widget** in the widget picker. Confirm it exposes
    **Meds Widget — Single medicine** and **Meds Widget — All medicines** with
    understandable descriptions.

11. Add a 2×2 **Meds Widget — Single medicine** widget, select **Medicine A** in
    the configuration activity, and select **Save widget**. Confirm cancelling
    and retrying configuration does not leave a broken widget.

12. Add another 2×2 single-medicine widget, select **Medicine B**, and save it.
    Keep both single widgets on the home screen simultaneously.

13. Confirm the first 2×2 widget shows only Medicine A and its two enabled
    labels, while the second shows only Medicine B and its one enabled label.
    Reconfigure one where One UI exposes the control and confirm the other
    instance does not change. The app declares the single widget reconfigurable
    in its Android 9/API 28+ provider metadata, but One UI decides where that
    control appears.

14. Add the 4×2 **Meds Widget — All medicines** widget and confirm its header
    shows **Today’s medicines** plus compact progress.

15. Confirm every active dose from Medicine A and Medicine B is reachable by
    vertical scrolling inside the 4×2 widget. Verify One UI scrolls the widget
    collection instead of silently omitting off-screen rows.

16. From Medicine A's 2×2 widget, tap its unchecked afternoon/custom-label row
    once.

17. Confirm the row immediately becomes checked and shows a compact,
    locale-appropriate check time. Compare that displayed time with the phone
    clock.

18. Open the app and inspect the 4×2 widget. Confirm the app, live previews,
    Medicine A 2×2 widget, every other widget instance for that dose, 4×2
    progress, and checked time converge to the same taken state.

19. Tap the already checked Medicine A item again in the home-screen widget.
    Confirm it does not become unchecked; opening the app is acceptable.

20. In the full app, tap the checked row, confirm the explicit undo dialog, and
    choose **Undo check**. Confirm history retains the original check and shows
    that it was later undone.

21. Return to the home screen and confirm both single/all widgets and progress
    update to the unchecked state without removing historical data.

22. In **Settings**, set the daily reset time three to five minutes ahead of
    the current local time. Save it and confirm the app explains that the
    visible logical day may change and all widgets remain usable.

23. Keep the device awake across the boundary, then open/interact with the app
    and widgets. Confirm the new logical medication day appears and all doses
    are unchecked for the new day.

24. Open **History** and confirm records from the prior logical day remain,
    including the original check time, source, and undo time.

25. Reboot the Samsung phone normally with all three widgets still placed.

26. After unlock and launcher startup, confirm both 2×2 instances retain their
    independent medicine selections, the 4×2 list returns, and each widget
    recovers without a crash or permanent loading state.

27. Disable both Wi-Fi and mobile data. Open the app and confirm cached medicine
    rows remain visible with a non-intrusive cached/offline indication.

28. While still offline, check an unchecked dose. Confirm the app/widget updates
    immediately and indicates pending synchronization without crashing.

29. Re-enable connectivity, keep the app available long enough to synchronize,
    and confirm the pending indication clears and the same state survives an
    app restart or Firestore reload.

30. Confirm cloud synchronization by viewing the state from another already
    authenticated test device/session or by inspecting only this test account's
    document in the Firebase console. Do not use or expose production user
    data.

31. Resize both widget types to the minimum and larger sizes One UI permits.
    Confirm names truncate safely, controls remain readable/tappable, 4×2 rows
    remain scrollable, and no content overlaps or disappears unexpectedly.

32. Add one Meds Widget instance to a Samsung widget stack, swipe into and out
    of it, scroll/tap its rows, and confirm stack gestures do not make the
    widget unusable.

33. Test light and dark device/home-screen themes. Confirm text, checked state,
    checkbox symbols, cached/pending information, and tap targets remain
    understandable without relying on colour alone.

34. Create a 100-character medicine name and 60-character custom labels, then
    increase Android **Font size** in Settings. Recheck the app, both previews,
    2×2 widget, and 4×2 widget for safe truncation, readable text, accessible
    touch targets, scrolling, and absence of crashes.

## Evidence to record

For each step, record **PASS**, **FAIL**, or **BLOCKED**, plus a short note. A
failed step must include:

- exact device/One UI information;
- expected and observed behaviour;
- reproducible actions;
- relevant non-sensitive logs or screenshots; and
- the commit SHA/APK hash tested.

After testing, restore the preferred reset time and Android font setting.
Remove test widgets/accounts if appropriate, and revoke **Install unknown apps**
permission for the installer source if it is no longer needed.

Do not replace the label at the top of this document or report Samsung testing
as passed until all 34 steps have evidence from a physical Samsung device.

## V1.1 responsive sizing and countdown addendum

Every item below is also **REQUIRES_PHYSICAL_SAMSUNG_VALIDATION**:

1. Install the V1.1 App Distribution build over `1.0.0 (10)` on the same phone
   and confirm Android accepts the existing signing identity.
2. Compare the 2×2 typography and controls with 1.0.0 (10); confirm previously
   unused area is used without clipping.
3. Test one-slot and two-slot single widgets at every permitted horizontal and
   vertical resize.
4. Resize the 4×2 widget narrow/wide and short/tall; confirm the header remains
   compact and all rows remain reachable by vertical scrolling.
5. Repeat sizing with default and increased Android font/display size, long
   medicine names/labels, and light/dark home-screen themes.
6. Configure Afternoon for 2 hours and Night for 90 minutes.
7. Start Afternoon using the 2×2 widget's separate **Start 2h** target; confirm
   the dose stays unchecked and the app shows the same target time.
8. Start Night from the 4×2 widget; confirm both timers belong to the correct
   medicine slots and neither action checks a dose.
9. Confirm scheduled refreshes reduce the displayed remaining time without
   opening the app.
10. Use a short custom duration to confirm the widget reaches **READY** and
    does not automatically check the dose.
11. Tap a running/READY label and confirm it opens the app instead of restarting
    or cancelling from the widget.
12. Check the dose before/after READY; confirm the timer disappears, check time
    appears, all surfaces converge, and another widget tap cannot undo it.
13. In the app, start then cancel and restart intentionally; verify start/target
    time details and that changing duration offers Keep or Restart.
14. Start a timer offline, confirm immediate local display, reconnect, and
    confirm cloud convergence without a duplicate timer.
15. Reboot during an active timer and confirm it resumes from its absolute
    target rather than restarting its duration.
16. Kill the app process, then start timers from each widget type; confirm the
    cold Glance callback works.
17. Keep multiple independently configured 2×2 instances and confirm a Start or
    Check affects only the selected medicine/slot while all matching surfaces
    refresh.
18. Cross midnight and a custom logical-day reset with a running timer; confirm
    it stays associated with its originating logical day and is not silently
    reused for the new day.

## V2.2 patient accessibility addendum

Every item below is also **REQUIRES_PHYSICAL_SAMSUNG_VALIDATION** for the signed
`2.2.0` release candidate. Automation may support these checks but must not be
reported as a substitute for the physical-device evidence.

1. Upgrade from the latest installed 2.1.x release to 2.2.0 without clearing
   app data. Confirm medicines, history, settings, widget configurations,
   countdowns, reminders, and account state survive the upgrade.
2. Open **Settings > Easy to use**, select **Patient mode**, and confirm the
   main navigation contains only **Today**, **History**, and **More**. Confirm
   medicine editing, Widget Studio, and other caregiver tools are not in the
   patient's normal daily navigation.
3. Switch back to **Caregiver mode** and confirm **Medicines** and the advanced
   management surfaces return without changing medicine or dose data. Switch
   to Patient mode again for the remaining accessibility checks.
4. On Patient Today, verify the next medicine is visually dominant and the
   primary **I TOOK IT** target can be activated comfortably with one thumb.
   Repeat several times while intentionally tapping near the target edges to
   check for accidental neighbouring actions.
5. Record a dose with **I TOOK IT**. Confirm there is immediate haptic/visual
   confirmation and a visible **Undo** opportunity. Use Undo once and verify
   the dose returns to pending while history preserves the audit trail.
6. Open **Remind me later** and exercise the 15-minute, 30-minute, and 1-hour
   choices. Verify each creates one reminder, does not mark the medicine taken,
   and does not create duplicate reminders after reopening the app.
7. Configure a short wait timer. Verify Patient mode uses plain-language states
   such as **Wait ...** and **You can take it now**, and that reaching zero does
   not automatically mark the dose taken.
8. Mark a dose **I am not taking this dose** / **I did not take it** in the app.
   Confirm the app describes the state without suggesting a replacement dose,
   double dose, catch-up dose, or other medical action.
9. With that same skipped dose, inspect the 2×2, 4×2, and 4×4 widgets. Confirm
   they show **NOT TAKEN**, do not offer the check callback or Start Timer for
   that row, and tapping the row opens the app to change the record.
10. Turn on Android **TalkBack**. Navigate Patient Today, History, More, the
    Easy-to-use settings, reminder choices, and dose-detail controls. Confirm
    each actionable control is announced with a meaningful label/state and no
    essential action is exposed only as an unlabeled icon.
11. With TalkBack still enabled, traverse real home-screen widgets. Confirm dose
    rows announce medicine, slot, state, and expected action for **TAKEN**,
    **NOT RECORDED**, **WAIT**, **TAKE NOW**, and **NOT TAKEN** states.
12. Turn on **Voice Access** where available. Confirm the major Patient-mode
    actions can be selected without precise tapping and that duplicate/ambiguous
    labels do not make the primary medication workflow impractical.
13. Test the app's **Phone setting**, **Large**, and **Extra Large** text modes.
    Then increase Android system font size up to the device's 200% maximum.
    Confirm text reflows vertically instead of clipping or shrinking essential
    controls, and all Patient-mode actions remain reachable by scrolling.
14. At Extra Large/200% text, repeat Today, dose detail, History, More, account
    deletion progress, Patient/Caregiver switching, and reminder selection.
    Confirm no important control becomes permanently hidden below or behind
    another element.
15. Repeat the primary workflow using only one hand and deliberately imprecise
    taps to approximate reduced dexterity/tremor. Confirm primary and secondary
    actions have adequate separation and that accidental medicine recording can
    be corrected from the app.
16. Test light and dark themes plus Samsung high-contrast/accessibility display
    options that are available on the device. Confirm **TAKEN**, **NOT TAKEN**,
    pending, wait, and ready states remain understandable without colour alone.
17. Resize the 2×2, 4×2, and 4×4 widgets through every One UI size the launcher
    permits while Patient mode and large system text are enabled. Confirm
    status words remain legible, rows remain reachable, and no action target is
    clipped into another target.
18. Reboot the Samsung phone with Patient mode, large text, active widgets, and
    at least one scheduled reminder. After unlock, confirm Patient mode remains
    selected, text preference remains selected, widgets recover, and reminder
    behaviour remains sane without duplicate notifications.
19. Test offline mode: record one medicine, start one countdown, and schedule
    one remind-later action while connectivity is unavailable where supported.
    Reconnect and confirm the dose/countdown cloud state converges without
    changing the local accessibility preferences or duplicating the reminder.
20. Perform a final plain-language review with TalkBack off and on. Confirm the
    daily workflow never labels an unrecorded or overdue dose with instructions
    to compensate medically; ambiguous states remain **Not recorded yet** or an
    equivalent factual tracking state until the user records an action.

Record PASS/FAIL/BLOCKED evidence for all V2.2 addendum steps with the exact
signed APK version code, commit SHA, APK SHA-256, Samsung model, Android/One UI
version, accessibility settings used, and tester/date. Keep
**REQUIRES_PHYSICAL_SAMSUNG_VALIDATION** until these checks are completed on the
actual release candidate.
