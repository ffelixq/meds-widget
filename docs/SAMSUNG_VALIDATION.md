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
