# Dashboard Editor Regression Notes

This note exists to avoid repeating the same editor bugs while extending dashboard editing to more pages.

## Hard requirements

- Page previews in the page picker must use proportional scaling.
- Page picker cards must be independent preview containers, not one container swapping content.
- Pinch-to-open in editor mode must observe gestures without breaking single-finger widget editing.
- Draft layouts must survive page switching inside the editor session.
- Saving a page layout must preserve that layout's own `pageId`.
- Status and runtime drafts must not share mode state by accident.

## Known pitfalls already hit

### 1. Runtime save lost after exit

Root cause:
- `DashboardLayoutEngine.sanitize()` rewrote every layout to `DashboardPageId.STATUS`.

Effect:
- Runtime layouts were saved as `status`.
- On reload, runtime import rejected the payload as the wrong page and fell back to defaults.

Rule:
- Never hardcode `pageId` inside shared layout normalization/sanitization.
- Keep `layout.pageId` unless an explicit migration is happening.

### 2. Page picker showed mixed grid/freeform pages

Root cause:
- Status and runtime each kept their own `layoutMode`.
- Page picker opened without syncing the non-active page draft to the active page's mode.

Rule:
- Before opening the page picker, remap the other editable page draft to the current editor page's `layoutMode`.

### 3. Pinch gesture regressed normal editing

Root cause:
- Full-screen transparent gesture layers consumed or intercepted pointer input.

Rule:
- Observe pinch on the root container with a non-consuming observer.
- Do not place a topmost blocker over the editor canvas for pinch detection.

### 4. Duplicate helper implementations caused compile failures

Root cause:
- Shared hidden-widget helpers were extracted, but old `StatusScreen` copies stayed in place.

Rule:
- After extracting shared editor utilities, remove page-local duplicates immediately.

## When adding a new editable page

1. Add page-specific persisted layout keys and restore flow.
2. Add draft state and edit-mode state to `MainUiState`.
3. Keep page-specific widget definitions and default layouts separate.
4. Reuse shared editor UI helpers for tray/menu/drag preview.
5. Verify save -> exit -> reopen -> restart still restores the edited layout.
6. Verify page picker previews the page draft, not only the last saved layout.
