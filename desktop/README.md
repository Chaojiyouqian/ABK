# ABK Desktop

Rust GTK/libadwaita desktop shell for ABK.

Current structure:

- `CLI` page: runs the existing `cli/abk.py` so desktop GitHub/build coverage tracks the CLI surface.
- `Device` page: starts the Android ABK agent over `adb`, forwards its localhost port, and renders session/runtime/root-grant/SUSFS snapshots.
- `Actions` page: sends Android/root actions to the phone agent, including module control, SUSFS apply, installs, flashing, and diagnostics export.

Run locally:

```bash
cargo run
```

The desktop app expects:

- `python3` for `cli/abk.py`
- `adb`
- GTK4 + libadwaita development packages on the host

Diagnostics downloads are stored under `desktop-downloads/` in the repo root.
