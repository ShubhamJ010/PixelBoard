# Known Issue: Clipboard History Misses Copies Made While Another IME Is Active

| Field | Value |
| :--- | :--- |
| **Document** | `docs/issues/clipboard-capture-window.md` |
| **Type** | Known Issue / Limitation (platform-imposed) |
| **Severity** | Medium — feature is functional, but clips captured outside the capture window need a manual trigger |
| **Area** | Clipboard History Manager & External Export |
| **Affected build** | PixelBoard v18.4.1-V7 (Patch Bundle v1.2.0) and earlier |
| **Base APK** | Gboard `18.4.1.985164140-release-arm64-v8a` |
| **Android** | Android 14 (API 34), arm64-v8a (tested on OnePlus 8T) |
| **Status** | Mitigated — capture window disclosed, back-fill and live primary endpoint implemented |
| **Reported** | 2026-10-20 |

---

## 1. Summary

The **Clipboard history** screen and the exported content provider report every clip that is
available to the keyboard process. Android however only exposes the primary clip to the focused
application and to the **currently active input method**, and Gboard only registers its clipboard
listener while it owns the input.

Copies performed while a different IME is active (for example **AndroMeld Input**,
`com.catchingnow.andfiles.helper/.MirrorInputMethodService`) land on the **system clipboard** but
never reach Gboard's `gboard_clipboard.db` → `clips` table, and they are invisible to the in-app
list and to the external provider until the user comes back to PixelBoard.

This is not a defect in the Clipboard History feature. It is an Android platform access rule
(see [§5](#5-root-cause)) combined with a Gboard behaviour that only registers its clipboard
listener while it owns the input focus.

---

## 2. Expected Behaviour

- A copy performed on the device is expected to appear in **PixelBoard Settings → Advanced settings
  → Clipboard history**, and to be readable through the exported content provider, regardless of
  which keyboard was active at the moment of the copy.

## 3. Actual Behaviour

- A copy performed while **PixelBoard is the active IME** → captured, listed, and served by the
  provider. ✅
- A copy performed while **any other IME is active** → present on the system clipboard, **absent**
  from the PixelBoard clipboard history and from the provider until PixelBoard becomes the active
  IME again. ⚠️
- The behaviour used to be **silent**: the history screen showed no indication that captures were
  skipped, which read as data loss to the user. The screen now states the capture window
  explicitly.

### 3.1 What the provider actually returns

The provider is a thin reader over Gboard's own `clips` table, but the payload is not literally
"every clip Gboard captured":

- clips captured from password fields (`sensitive = 1`) are **filtered out** unless the user
  enables **Include sensitive clips**;
- at most **500 rows** are served (`GboardClipboardHistoryRepository.MAX_LIMIT`);
- the in-app screen shows the same filtered and limited set, so the screen and the provider never
  diverge.

Companion apps that need password field clips must therefore opt in explicitly, independently of
the capture window described here.

---

## 4. Reproduction Steps

**Preconditions**
- PixelBoard installed and enabled as a keyboard.
- **External access** toggled **ON** under *Advanced settings → Clipboard history* (required to
  query the provider from ADB).
- ADB: `adb shell content query --uri "content://com.akshaykadam.pixelboard.gboard_clipboard_history/clips?token=<ACCESS_TOKEN>"`

| # | Step | Expected | Result |
| :-- | :--- | :--- | :--: |
| 1 | Set IME to `com.akshaykadam.pixelboard/com.android.inputmethod.latin.LatinIME` | — | — |
| 2 | Copy any text (e.g. in Chrome: long-press → **Copy**) | Clip captured | ✅ row appears in provider + screen |
| 3 | Switch IME to another keyboard (`adb shell ime set com.catchingnow.andfiles.helper/.MirrorInputMethodService`) | — | — |
| 4 | Copy any text | Clip captured | ❌ **no new row** |
| 5 | Verify the clip *is* on the system clipboard (paste it back) | Clip present | ✅ present — only Gboard's history missed it |
| 6 | Switch IME back to PixelBoard, open a text field | Clip recovered | ✅ row appears (back-fill) |

**Step 4 is the failing case.** Switching the IME back to PixelBoard reproduces capture, which
confirms the active-IME dependency rather than a database or provider fault.

---

## 5. Root Cause

### 5.1 Gboard registers its clipboard listener only while it owns the input

Gboard's `com.google.android.apps.inputmethod.libs.clipboard.ClipboardDataHandler` (obfuscated
`Lfmg;`) implements `ClipboardManager.OnPrimaryClipChangedListener` and is wired into the **IME
service lifecycle**. While another IME is active, Gboard's service is not the one receiving input
focus, so the listener is not armed and the `clips` table is never written.

The existing **`GboardClipboardSyncPatch`**
(`features/clipboard/GboardClipboardSyncPatch.kt`) hooks `onStartInputView` and calls
`ClipboardDataHandler.onPrimaryClipChanged()` to force a foreground sync, because the listener only
fires while the input view is alive.

### 5.2 Android 10+ clipboard access restriction

Since Android 10, clipboard reads are restricted to:

- the application with input focus, and
- the **currently active IME**.

Therefore, while another IME is active, **no PixelBoard code path — including a background
service inside the PixelBoard process — can legally read the primary clip**. There is no
permission, flag, or bytecode hook that widens this. The copy is simply unreachable for PixelBoard
at that moment.

Two details matter for the implementation:

- a denied read does **not** raise an error, the platform answers with an **empty clip**. "Clipboard
  is empty" and "clipboard access denied" therefore look identical unless the clip *description* is
  inspected as well;
- the restriction follows the **current** IME. Both `adb shell ime set` and the notification bar
  IME switcher change it, so the limitation applies to the regular user flow as well.

### 5.3 Why the provider is not at fault

The provider (`GboardClipboardHistoryProvider`) is a thin, correct reader over Gboard's own `clips`
table:

```
content://com.akshaykadam.pixelboard.gboard_clipboard_history/clips
  → SQLiteDatabase.query("clips", …) on gboard_clipboard.db
```

Access control was verified independently:

```text
# no token  → java.lang.SecurityException: PixelBoard clipboard history access requires the
#             com.akshaykadam.pixelboard.permission.READ_CLIPBOARD_HISTORY permission or a
#             valid access token.
# with token → Row: 0 _id=1792489634341, text=www.instagram.com, entity_type=1, …
```

The provider returns exactly what is stored — subject to the sensitive filter and the row limit
described in [§3.1](#31-what-the-provider-actually-returns).

---

## 6. Evidence Captured on Device

### 6.1 Provider output after the failing copy

```text
Row: 0 _id=1792489634341, text=www.instagram.com,          item_type=0, entity_type=1, timestamp=1792489634341, sensitive=0, uri=, group_id=NULL
Row: 1 _id=1792489530023, text=https://www.instagram.com/, item_type=0, entity_type=1, timestamp=1792489530023, sensitive=0, uri=, group_id=NULL
Row: 2 _id=1792488791868, text=Please install Android Use, the MCP server provided by AndroMeld …, sensitive=0
Row: 3 _id=1792488759711, text=c,                          item_type=0, entity_type=0, timestamp=1792488759711, sensitive=0, uri=, group_id=NULL
```

The clip that was copied under the other IME is **not** part of this payload, and it was **not**
part of the in-app screen either.

### 6.2 Active-IME state at the moment of the missed copy

```text
$ adb shell dumpsys input_method | grep -E 'mCurMethodId|mInputShown'
mCurMethodId=com.catchingnow.andfiles.helper/.MirrorInputMethodService
mInputShown=true
```

With PixelBoard active, the same command reports:

```text
mCurMethodId=com.akshaykadam.pixelboard/com.android.inputmethod.latin.LatinIME
mInputShown=true
```

### 6.3 The missed clip was still on the system clipboard

Pasting the clipboard back into the Chrome omnibox during testing reproduced
`https://www.instagram.com/yogitaa.bisht/` — confirming the copy succeeded and that only
Gboard's capture was missed.

### 6.4 In-app screen and provider agree

```text
Recent entries
  www.instagram.com              · 1 min. ago
  https://www.instagram.com/     · 3 min. ago
  Please install Android Use…     · 15 min. ago
  c                              · 16 min. ago
```

Identical to the provider payload → no divergence between UI and API.

### 6.5 Incidental finding — `entity_type` is usable as a filter

| clip | `item_type` | `entity_type` |
| :--- | :--: | :--: |
| `c` | 0 | 0 (plain text) |
| `https://www.instagram.com/` | 0 | **1 (URL)** |

`entity_type` is populated by Gboard's own entity detection and is currently exposed but not
surfaced in the UI. It gives companion apps a cheap way to separate links, phone numbers,
addresses and free text without re-parsing the clip body.

**Limitation:** the classification is Gboard's own and is not reproducible outside of Gboard.
Entries that PixelBoard back-fills (see [§8.2](#82-option-b--back-fill-the-primary-clip-on-input-view-activation-shipped)) are
stored with `item_type = 0` and `entity_type = 0`, because no local classifier is available. A
companion app must not assume `entity_type = 1` for every URL.

---

## 7. Impact

| Scenario | Impact |
| :--- | :--- |
| User copies while PixelBoard is the active IME | Fully captured — no issue |
| User copies under a different keyboard (screen mirroring IME, vendor IME, secondary keyboard) | Clip missing from history and provider until PixelBoard is used again |
| Companion automation polls the provider for "last copied OTP" | Misses OTPs copied while another IME was active, and misses password field clips unless **Include sensitive clips** is enabled |
| MacroDroid / Shortcut trigger keyed on a clipboard match | Will not fire for clips copied under another IME |
| User perception | Used to read as data loss; the capture window is now disclosed in the UI |

---

## 8. Resolutions

Ordered by cost / benefit. None of them can recover clips that were copied **and** whose system
clipboard slot has since been overwritten — the platform only ever exposes the **single** current
primary clip.

### 8.1 Option A — UI disclosure (shipped)

The *Clipboard history* screen states the capture window as a single line under the **Recent
entries** heading:

> Only copies made while PixelBoard is the active keyboard are recorded.

Implementation: `gboard_patches_clipboard_history_capture_window_summary` in
`extensions/extension/src/main/settings-text/gboard_settings_text.xml`, rendered as the section
description in `GboardClipboardHistoryFeature`.

### 8.2 Option B — Back-fill the primary clip on input-view activation (shipped)

`GboardClipboardSyncRuntime` already runs on `onStartInputView`. It now additionally schedules
`GboardClipboardBackfill`, which

1. reads `ClipboardManager.getPrimaryClip()` — legal here, because PixelBoard is the active IME at
   that moment,
2. skips the clip when the same content is already stored (a persisted marker and the newest rows
   of the `clips` table are compared), so no duplicate rows are created,
3. writes the entry through `GboardClipboardHistoryRepository.insertClipIfAbsent(...)`, which fills
   only the columns that exist in the table, runs the duplicate check and the insert on the same
   database connection, and never throws into the IME path.

The back-fill runs 1.5 s after the input view started, so Gboard's own asynchronous capture lands
first and the duplicate check can skip the entry.

Known trade-offs:

- `entity_type` / `item_type` are stored as `0` because Gboard's classifier is not available
  outside of Gboard (see [§6.5](#65-incidental-finding--entity_type-is-usable-as-a-filter));
- `sensitive` is derived from `ClipDescription.EXTRA_IS_SENSITIVE`, so password field clips stay
  hidden unless **Include sensitive clips** is enabled;
- the entry is written through a second connection to Gboard's database. If Gboard holds a write
  lock at that moment the insert is skipped and the clip is recovered on the next input view
  start.

### 8.3 Option C — Live primary-clip endpoint (shipped)

`content://com.akshaykadam.pixelboard.gboard_clipboard_history/primary` returns the entry that is
on the system clipboard right now, independently of the history table. It answers "what is on the
clipboard" even for clips that another IME captured.

- The same Android restriction applies: the endpoint is only populated while PixelBoard is the
  active IME, otherwise it returns an empty cursor.
- The endpoint honours the same access control and the same sensitive policy as `/clips`.
- The columns match `/clips`, with `_id = 0` because the entry has no history identifier.
- The **External access** section lists the provider URI, the endpoints and the signature
  permission, so a companion app can be wired up without reading the source:
  `content://com.akshaykadam.pixelboard.gboard_clipboard_history` with `/clips`, `/clips/{id}`
  and `/primary`.

### 8.4 Option D — Capture via an AccessibilityService (companion app, out of scope)

Accessibility services are permitted to read the clipboard regardless of which IME is active.
A small companion service could feed clips into a shared store that the provider then serves.

- Out of scope for a keyboard patch: it requires a separate installed app, a user-granted
  accessibility permission, and careful disclosure of the privacy implications.
- The accessibility app also receives the system clipboard access notification, and the broad
  accessibility permission has to be explained to the user.

---

## 9. Current Workaround

| Need | Workaround |
| :--- | :--- |
| Capture a specific clip | Switch to PixelBoard, then copy it again |
| Read the *current* clipboard only | Query the `/primary` endpoint, or read `ClipboardManager.getPrimaryClip()` from the companion app while PixelBoard is the active IME |
| Automation must not depend on the IME | Have the companion app copy the value it needs into a location it owns (its own `SharedPreferences`/DB) at the moment of use |

---

## 10. Acceptance Criteria

- [x] The Clipboard history screen states plainly that only clips captured while PixelBoard was the
  active keyboard are listed. *(Option A)*
- [x] A clip copied under a different IME and still present on the system clipboard appears in the
  history the next time the PixelBoard input view is shown, with no duplicate rows. *(Option B)*
- [x] Back-filled entries are stored with the `sensitive` flag of the source clip and are excluded
  unless the user explicitly enables **Include sensitive clips**. *(Option B)*
- [ ] Back-filled entries are classified with the same `item_type` / `entity_type` values Gboard
  would have assigned — **not achievable**, Gboard's entity classifier is not available outside of
  Gboard. Back-filled entries use `item_type = 0` and `entity_type = 0`. *(Option B, deviation
  documented in [§6.5](#65-incidental-finding--entity_type-is-usable-as-a-filter))*
- [x] The provider still rejects callers without the signature permission or a valid access token.
- [x] The provider still rejects insert / update / delete.
- [x] No regression in the existing clipboard screenshot observer synchronisation
  (`ensureScreenshotObserver` on `onStartInputView`).

---

## 11. Manual Test Plan

1. Enable PixelBoard, set it as the active IME.
2. Open a text field → **copy** a known string → confirm it appears in the history and via the
   provider.
3. Switch to a second IME → **copy** a different known string → confirm it is **absent** from the
   history (expected behaviour) and still on the system clipboard.
4. Switch back to PixelBoard → open a text field → confirm the back-filled entry appears exactly
   once.
5. Open another text field without copying anything → confirm no second copy of the same entry
   appears.
6. Copy the same string twice in a row → confirm no duplicate row is created.
7. Copy inside a password field → confirm the entry is stored with `sensitive = 1` and stays
   hidden unless **Include sensitive clips** is enabled.
8. Toggle **Allow external apps to read clipboard history** off → confirm the provider denies
   external callers for both `/clips` and `/primary`.
9. Rotate the access token → confirm the old token is rejected and the new one accepted.
10. While another IME is active → query `/primary` → confirm it returns an empty cursor.
11. While PixelBoard is active → copy something → query `/primary` → confirm it returns the copied
    text.

---

## 12. Related

- Clipboard History Manager & External Export — feature documentation (README, *Key Features* §16).
- GitHub issue #17 — "Clipboard is always empty" (Samsung One UI & Vivo foreground sync, the
  origin of `GboardClipboardSyncPatch` and `GboardClipboardSyncRuntime`).
- `GboardClipboardSyncPatch` — the `onStartInputView` hook that drives the sync and the
  back-fill.
- `GboardClipboardSyncRuntime` — forces Gboard's foreground capture and schedules the back-fill.
- `GboardClipboardBackfill` — reads the primary clip and stores the missing entry.
- `GboardClipboardHistoryRepository` — the `gboard_clipboard.db` / `clips` reader and writer.
- `GboardClipboardHistoryProvider` — the exported read-only provider (`/clips`, `/clips/{id}`,
  `/primary`).

---

## 13. Maintainer Notes

The **Clipboard History** feature itself is complete and verified end-to-end on device: the
in-app list, the SAF export, the content provider, the token / signature-permission access
control and the read-only contract all behave as designed. The only open item is the *upstream
capture window* documented above, which is bounded by the Android clipboard access restriction
rather than by the implementation.

Option A removes the "silent data loss" perception, Option B recovers the most recent clip that was
copied under a foreign IME, and Option C answers "what is on the clipboard right now" for
companion apps. A clip that was overwritten on the system clipboard before the user returned to
PixelBoard cannot be recovered — the platform only ever exposes the single current primary clip.
