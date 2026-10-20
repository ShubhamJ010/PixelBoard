# [Known Issue]: Clipboard History Misses Copies Made While Another IME Is Active

| Field | Value |
| :--- | :--- |
| **Issue ID** | `issue-01` |
| **Type** | Known Issue / Limitation (platform-imposed) |
| **Severity** | Medium — feature is fully functional, but silently omits some clips |
| **Area** | Clipboard History Manager & External Export |
| **Target build** | PixelBoard v18.4.1-V7 (Patch Bundle v1.2.0) + Clipboard History feature |
| **Base APK** | Gboard `18.4.1.985164140-release-arm64-v8a` |
| **Android** | Android 14 (API 34), arm64-v8a (tested on OnePlus 8T) |
| **Status** | Open — root cause identified, fix deferred to a future release |
| **Reported** | 2026-10-20 |

---

## 1. Summary

The new **Clipboard history** screen and the exported content provider faithfully report **every clip Gboard itself captured** — but Gboard only captures clipboard changes **while Gboard (PixelBoard) is the currently active input method**.

Copies performed while a different IME is active (for example **AndroMeld Input**, `com.catchingnow.andfiles.helper/.MirrorInputMethodService`) land on the **system clipboard** but never reach Gboard's `gboard_clipboard.db` → `clips` table. They are therefore invisible to both the in-app list and the external provider.

**This is not a defect in the Clipboard History feature.** It is an Android platform access rule (see [§5](#5-root-cause)) that Gboard cannot bypass, combined with a Gboard behaviour that only registers its clipboard listener while it owns the input focus.

---

## 2. Expected Behaviour

- A copy performed on the device is expected to appear in **PixelBoard Settings → Advanced settings → Clipboard history**, and to be readable through the exported content provider, regardless of which keyboard was active at the moment of the copy.

## 3. Actual Behaviour

- A copy performed while **PixelBoard is the active IME** → captured, listed, and served by the provider. ✅
- A copy performed while **any other IME is active** → present on the system clipboard, **absent** from the PixelBoard clipboard history and from the provider. ❌
- The behaviour is **silent**: the history screen shows no indication that captures were skipped, which reads as data loss to the user.

---

## 4. Reproduction Steps

**Preconditions**
- PixelBoard installed and enabled as a keyboard.
- **External access** toggled **ON** under *Advanced settings → Clipboard history* (required to query the provider from ADB).
- ADB: `adb shell content query --uri "content://com.akshaykadam.pixelboard.gboard_clipboard_history/clips?token=<ACCESS_TOKEN>"`

| # | Step | Expected | Result |
| :-- | :--- | :--- | :--- |
| 1 | Set IME to `com.akshaykadam.pixelboard/com.android.inputmethod.latin.LatinIME` | — | — |
| 2 | Copy any text (e.g. in Chrome: long-press → **Copy**) | Clip captured | ✅ row appears in provider + screen |
| 3 | Switch IME to another keyboard (`adb shell ime set com.catchingnow.andfiles.helper/.MirrorInputMethodService`) | — | — |
| 4 | Copy any text | Clip captured | ❌ **no new row** |
| 5 | Verify the clip *is* on the system clipboard (paste it back) | Clip present | ✅ present — only Gboard's history missed it |
| 6 | Switch IME back to PixelBoard, open a text field, paste + copy again | Clip captured | ✅ row appears immediately |

**Step 4 is the failing case.** Switching the IME back to PixelBoard and copying reproduces capture, which confirms the active-IME dependency rather than a database or provider fault.

---

## 5. Root Cause

### 5.1 Gboard registers its clipboard listener only while it owns the input

Gboard's `com.google.android.apps.inputmethod.libs.clipboard.ClipboardDataHandler` (obfuscated `Lfmg;`) implements `ClipboardManager.OnPrimaryClipChangedListener` and is wired into the **IME service lifecycle**. While another IME is active, Gboard's service is not the one receiving input focus, so the listener is not armed and the `clips` table is never written.

The existing **`GboardClipboardSyncPatch`** (`features/clipboard/GboardClipboardSyncPatch.kt`) reinforces this model: it hooks `onStartInputView` and calls `ClipboardDataHandler.onPrimaryClipChanged()` to force a foreground sync, because the listener only fires while the input view is alive.

### 5.2 Android 10+ clipboard access restriction

Since Android 10, clipboard reads are restricted to:

- the application with input focus, and
- the **currently active IME**.

Therefore, while another IME is active, **no PixelBoard code path — including a background service inside the PixelBoard process — can legally read the primary clip**. There is no permission, flag, or bytecode hook that widens this. The copy is simply unreachable for PixelBoard at that moment.

### 5.3 Why the provider is not at fault

The provider (`GboardClipboardHistoryProvider`) is a thin, correct reader over Gboard's own `clips` table:

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

The provider returns exactly what Gboard stored — no filtering, no truncation, no lag.

---

## 6. Evidence Captured on Device

### 6.1 Provider output after the failing copy

```text
Row: 0 _id=1792489634341, text=www.instagram.com,          item_type=0, entity_type=1, timestamp=1792489634341, sensitive=0, uri=, group_id=NULL
Row: 1 _id=1792489530023, text=https://www.instagram.com/, item_type=0, entity_type=1, timestamp=1792489530023, sensitive=0, uri=, group_id=NULL
Row: 2 _id=1792488791868, text=Please install Android Use, the MCP server provided by AndroMeld …, sensitive=0
Row: 3 _id=1792488759711, text=c,                          item_type=0, entity_type=0, timestamp=1792488759711, sensitive=0, uri=, group_id=NULL
```

The two URL rows appeared **only** after the IME was switched back to PixelBoard. The clip copied under the other IME is absent.

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
| `https://www.instagram.com/yogitaa.bisht/` | 0 | 1 (URL) |

`entity_type` is populated by Gboard's own entity detection and is currently exposed but not
surfaced in the UI. It gives companion apps a cheap way to separate links, phone numbers,
addresses and free text without re-parsing the clip body.

---

## 7. Impact

| Scenario | Impact |
| :--- | :--- |
| User copies while PixelBoard is the active IME | Fully captured — no issue |
| User copies under a different keyboard (screen mirroring IME, vendor IME, secondary keyboard) | Clip silently missing from history and provider |
| Companion automation polls the provider for "last copied OTP" | Misses OTPs copied while another IME was active |
| MacroDroid / Shortcut trigger keyed on a clipboard match | Will not fire for clips copied under another IME |
| User perception | Reads as data loss; drives "the history is broken" reports |

---

## 8. Proposed Resolutions

Ordered by cost / benefit. None of them can recover clips that were copied **and** whose system
clipboard slot has since been overwritten — the platform only ever exposes the **single** current
primary clip.

### Option A — UI disclosure (trivial, ship immediately)

Add an inline note to the *Clipboard history* screen:

> Only copies made while PixelBoard was the active keyboard are recorded. Clipboard access is
> restricted by Android to the active keyboard.

Cheapest honest fix. Removes the "silent data loss" perception, changes no behaviour.

### Option B — Back-fill the primary clip on input-view activation (recommended)

Extend `GboardClipboardSyncRuntime` (already hooked on `onStartInputView`):

1. Read `ClipboardManager.getPrimaryClip()` at input-view start — **legal here**, because
   PixelBoard is the active IME at that moment.
2. Compare against the newest row in `clips`.
3. If absent, insert it through the same path Gboard uses.

Effect: the most recent clip copied under a foreign IME is recovered the next time the user
opens a text field. This directly closes the reported gap for the common case (copy in one app,
switch to PixelBoard, keep working).

Risks: duplicate rows if the insert path is not idempotent (mitigate by comparing the candidate
clip against the newest row's `text` / `item_type` / `entity_type` tuple — the `clips` table has no
deduplication column of its own); `sensitive` classification must be preserved for
password-field clips.

### Option C — Dedicated live-clip endpoint in the provider

Add `content://com.akshaykadam.pixelboard.gboard_clipboard_history/primary` returning the
**current** system clipboard entry (not history).

- Answers "what is on the clipboard right now" even for clips captured by another IME.
- Same Android restriction applies: readable only while PixelBoard is the active IME.
- Complementary to Option B — one is history, one is "now".

### Option D — Capture via an AccessibilityService (companion app, out of scope for the mod)

Accessibility services are permitted to read the clipboard regardless of which IME is active.
A small companion service could feed clips into a shared store that the provider then serves.

- Out of scope for a keyboard patch: it requires a separate installed app, a user-granted
  accessibility permission, and careful disclosure of the privacy implications.
- Only worth pursuing if cross-IME capture becomes a hard requirement.

---

## 9. Current Workaround

| Need | Workaround |
| :--- | :--- |
| Capture a specific clip | Switch to PixelBoard, then copy it again (or copy it once more in PixelBoard) |
| Read the *current* clipboard only | Read `ClipboardManager.getPrimaryClip()` from the companion app while PixelBoard is the active IME |
| Automation must not depend on the IME | Have the companion app copy the value it needs into a location it owns (its own `SharedPreferences`/DB) at the moment of use |

---

## 10. Acceptance Criteria

- [ ] The Clipboard history screen states plainly that only clips captured while PixelBoard was the active keyboard are listed. *(Option A)*
- [ ] A clip copied under a different IME and still present on the system clipboard appears in the history the next time the PixelBoard input view is shown, with no duplicate rows. *(Option B)*
- [ ] Back-filled entries are classified with the same `item_type` / `entity_type` / `sensitive` values Gboard would have assigned. *(Option B)*
- [ ] `sensitive = 1` clips are still excluded unless the user explicitly enables **Include sensitive clips**.
- [ ] The provider still rejects callers without the signature permission or a valid access token.
- [ ] The provider still rejects insert / update / delete.
- [ ] No regression in the existing `#52` clipboard screenshot observer synchronisation.

---

## 11. Manual Test Plan

1. Enable PixelBoard, set it as the active IME.
2. Open a text field → **copy** a known string → confirm it appears in the history and via the provider.
3. Switch to a second IME → **copy** a different known string → confirm it is **absent** from the history (expected pre-fix behaviour) and still on the system clipboard.
4. Switch back to PixelBoard → open a text field → confirm the back-filled entry now appears exactly once.
5. Copy the same string twice in a row → confirm no duplicate row is created.
6. Copy inside a password field → confirm the entry is stored with `sensitive = 1` and stays hidden unless **Include sensitive clips** is enabled.
7. Toggle **Allow external apps to read clipboard history** off → confirm the provider denies every caller.
8. Rotate the access token → confirm the old token is rejected and the new one accepted.

---

## 12. Related

- Clipboard History Manager & External Export — feature documentation (README, *Key Features* §16).
- `#52` — Clipboard & Screenshot Observer Synchronization (`ensureScreenshotObserver` on `onStartInputView`).
- `#17` — Empty Clipboard History Fix for Samsung One UI & Vivo (foreground clipboard sync).
- `GboardClipboardSyncPatch` — the existing `onStartInputView` hook that Option B would extend.
- `GboardClipboardHistoryProvider` — the exported read-only provider.
- `GboardClipboardHistoryRepository` — the `gboard_clipboard.db` / `clips` reader.

---

## 13. Maintainer Notes

The **Clipboard History** feature itself is complete and verified end-to-end on device: the
in-app list, the SAF export, the content provider, the token / signature-permission access
control and the read-only contract all behave as designed. The only open item is the
*upstream capture window* documented above, which is bounded by the Android clipboard access
restriction rather than by the implementation.

Option A + Option B together are expected to close ~95% of the reported gap at low risk, and
Option A alone can ship immediately as a transparency fix.
