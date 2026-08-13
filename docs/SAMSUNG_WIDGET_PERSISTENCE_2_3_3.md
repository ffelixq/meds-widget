# Samsung widget persistence validation — 2.3.3

Status: `REQUIRES_PHYSICAL_SAMSUNG_VALIDATION`

The automated suite covers the 4×4 update coordinator, bounded non-collection rendering,
widget repair routing, existing dose callbacks, and minified release callback integrity.
It cannot prove One UI launcher persistence, so the release must be checked on a physical
Samsung phone after installation.

## Required physical checks

1. Add a 4×2 All medicines widget from Widget setup.
2. Confirm it renders medicine rows and can record an unchecked dose.
3. Leave the home screen by opening another app or another launcher page, then return.
4. Confirm the 4×2 widget still renders and does not become a blank/configure placeholder.
5. Repeat steps 1–4 with the 4×4 Today dashboard.
6. Long-press each automatic widget and use the launcher's Reconfigure/Configure action when available.
7. Confirm it opens the exact Meds Widget repair screen rather than the normal app home screen.
8. Tap Repair widget and confirm the same widget instance refreshes without removal or reinstallation.
9. If a Glance composition error is deliberately or naturally encountered, confirm the fallback shows
   Repair widget and that the button opens the exact repair screen.
10. Resize both automatic widgets and repeat the leave/return check.

Do not mark physical Samsung persistence as validated from emulator, Robolectric, or Glance
unit-test results alone.
