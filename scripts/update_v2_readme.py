from pathlib import Path

path = Path("README.md")
text = path.read_text()

text = text.replace(
    """Meds Widget is a focused, native Android medicine-tracking utility. It keeps an
afternoon/night checklist in the app and in two home-screen widgets. It is a
tracking utility only: it does not provide medical advice, dosing advice,
reminders, treatment recommendations, or drug information.""",
    """Meds Widget is a focused, native Android medicine-tracking utility. V2 supports
Morning, Afternoon, Evening, and Night routines across the app and home-screen
widgets, with optional personal reminders, countdowns, supply tracking, history,
and export. It is a tracking utility only: it does not provide medical advice,
dosing advice, treatment recommendations, drug-interaction guidance, or drug
information.""",
)
text = text.replace(
    """- V1.1 distribution: signed APK through Firebase App Distribution and GitHub
  Actions artifacts; no Google Play publication""",
    """- V2 distribution: signed APK through Firebase App Distribution and GitHub
  Actions artifacts; no Google Play publication""",
)
text = text.replace("automation definitions used for V1.", "automation definitions used for the current release.")
text = text.replace(
    """- Independently enabled afternoon and night slots, with custom per-medicine
  labels. At least one slot is required. Blank labels are rejected for enabled
  slots; a blank disabled-slot label is normalized to that slot's default.""",
    """- Independently enabled Morning, Afternoon, Evening, and Night slots, with
  custom per-medicine labels. At least one slot is required. Blank labels are
  rejected for enabled slots; a blank disabled-slot label is normalized to that
  slot's default.""",
)
text = text.replace(
    """- Optional 1–1,440 minute personal countdowns configured independently for
afternoon and night, with 30/60/90/120-minute presets and custom hours/minutes.""",
    """- Optional 1–1,440 minute personal countdowns configured independently per
  enabled slot, with 30/60/90/120-minute presets and custom hours/minutes.""",
)
text = text.replace(
    """- A responsive, vertically scrollable 4×2 Glance widget containing every
  active dose and using the same size categories.""",
    """- A responsive, vertically scrollable 4×2 Glance widget containing every
  active dose, plus a responsive 4×4 dashboard widget for a larger Today view.""",
)
text = text.replace(
    "- Compact history grouped by logical medication day.",
    """- History and adherence summaries grouped by logical medication day.
- Optional per-slot device-local medicine reminders with Android notification
  permission handling.
- Optional supply tracking, low-supply status, and app-based refills; taken doses
  reduce supply and app Undo restores a previously taken dose.
- Settings shortcuts for pinning supported widget sizes and a local CSV export
  of medicine setup and dose history.""",
)
marker = "## Features\n\n"
link = "See [V2 test release](docs/V2_RELEASE.md) for V2 behavior and the physical Samsung validation checklist.\n\n"
if marker in text and link not in text:
    text = text.replace(marker, marker + link, 1)

path.write_text(text)
