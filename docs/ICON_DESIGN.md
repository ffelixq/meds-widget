# App icon design

The refreshed Meds Widget icon follows the same principles as the current interface rather than imitating iOS chrome:

- **Purpose:** a medicine capsule remains the primary symbol.
- **Familiarity:** the completion check remains visible so the icon still reads as medication tracking.
- **Simplicity:** only two semantic elements are used at launcher size: medicine + completion.
- **Craft:** the icon uses Android adaptive-icon layers, an Android 13 monochrome/themed mark, and a separate notification-small-icon asset.
- **Consistency:** the background uses the app's blue action color and the completion mark uses the same green success language as the UI.
- **Accessibility:** high-contrast white geometry stays legible at small launcher sizes and under common adaptive masks.

Android, not the vector itself, controls the final adaptive mask shape. The foreground artwork stays centered so circle, squircle, rounded-square, and other launcher masks do not clip the medicine mark.
