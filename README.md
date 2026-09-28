# UECapUpdater

[English] | [繁體中文](README_zh.md)

A baseband configuration overriding and editing tool tailored for Google Pixel devices (Tensor / Samsung Modem architecture). It bypasses system namespace isolation to bind-mount customized `.binarypb` configuration files directly into modem daemons, enabling persistent network capability updates without modifying system partitions.

---

## 🛠️ Verified Environment

> [!IMPORTANT]
> This tool has been tested on the following configuration. Operational stability or compatibility is **not guaranteed** on other devices, older Android revisions, or different patch levels.

* **Device**: Google Pixel 9 Pro Fold
* **OS Version**: Android 17
* **Build Number**: `CP2A.260605.021`
* **Root Environment**: Magisk `30.7 (30700)` 

---

## 💡 Key Features

* **Custom Carrier Configuration Injection**: Inject customized `*.binarypb` files directly into the live modem filesystem without modifying the read-only `/vendor` partition.
* **Built-in Visual UECAP (.binarypb) Editor**: Integrated Material Design 3 Web editor to inspect, edit, and generate capability configurations. Features NR/LTE band combination editing, custom Feature Sets, automated 3GPP combination batch generation (e.g., `b3+b7C+b8A+n78C`), SA ULCA generation, and direct export to `.binarypb` files or Magisk `.zip` flashable modules.
* **Magisk / KernelSU Module Extraction**: Parse, unzip, and aggregate `.binarypb` files from multiple Magisk/KernelSU flashable `.zip` packages into a unified mount configuration.
* **Active Configs & System Scanner**: Inspect active namespace mounts in PID 1, cached files, `shamp` daemon parsing status, and launch Pixel Hardware Diagnostics (MDS/VCD tool) with one click.
* **Real-time Log Streaming & Debug Console**: Stream low-level `UECAP` and `shamp` logcat outputs in real-time during injection or reset. Includes a Root terminal console with diagnostic quick-action chips.
* **Deep Hardware Modem Reset**: Issue `AT+GOOGCPRESET` to `/dev/umts_router` to force a Cellular Processor (CP) cold restart and re-evaluate modem NV cache.
* **Dynamic Signal-Lock Guard**: Monitors Android Telephony Registry (`telephony.registry`) and `shamp` boot state while purging stale logs to verify real cellular network registration before finishing.
* **Automated Magisk Boot Module**: Generates a persistent startup module (`/data/adb/modules/uecapupdater_live`) to retain modifications across device reboots, with clean removal on factory restore.
* **Factory Fallback**: Safely unmount all active runtime overrides inside PID 1 and remove the persistent module, returning the modem daemon to default official carrier firmware.

---

## 📖 Navigation & Usage

### 1. Single-File Mode
1. Switch to the **Single-File Mode** tab.
2. Select one or more `.binarypb` configuration files.
3. Click **Apply & Restart Radio**. The live console streams unmounting, bind-mounting, hardware cold reset, and signal lock confirmation.

### 2. Module Extract Mode
1. Switch to the **Module Extract** tab.
2. Select Magisk/KernelSU `.zip` packages.
3. The app parses the archive tree, aggregates valid `.binarypb` files, and batch-applies them.

### 3. Visual UECAP Editor
1. Access the **Editor** from the top-right menu.
2. Load an existing `.binarypb` file or restore a previous editing session.
3. Edit band parameters, add custom DL/UL feature sets, or use **Batch Gen** for automated 3GPP combination generation.
4. Export directly as a `.binarypb` file or a flashable Magisk `.zip`.

### 4. Active Configs & Diagnostics
1. Open **Active Configs** from the menu.
2. Click **Refresh System Status** to view active mounts in PID 1, cached configurations, and `shamp` log entries.
3. Click **Launch Pixel MDS** to open the native Pixel hardware diagnostic utility.

### 5. Terminal Debugging
1. Open **Terminal** from the menu.
2. Use quick filter chips (Dump UECAP logs, Check mounts, List vendor files, Clear logcat) or run custom root shell commands.

### 6. Restore Factory
* Click **Restore Factory** in the Single-File tab to unmount runtime overrides in PID 1, remove the auto-generated Magisk module, and restart the modem daemon in pure factory state.

---

## 🚀 Technical Insights

The utility operates through a multi-stage runner:
1. **Namespace Boundary Crossing**: Executes `nsenter -t 1 -m` to operate within the PID 1 mount namespace.
2. **Bind Mounting**: Binds staged configuration files over `/vendor/firmware/uecapconfig/*.binarypb`.
3. **Hardware Cold Reset**: Sends `AT+GOOGCPRESET` to `/dev/umts_router` to trigger Cellular Processor cold restart and reload configuration payloads.
4. **Signal Lock Verification**: Queries `dumpsys telephony.registry` while filtering historical log entries to ensure hardware network attachment.

---

## 🙏 Acknowledgments

* **[NXij/pixel-pb](https://github.com/NXij/pixel-pb)**: Special thanks to NXij. The core UI layout, UECAP Protobuf decoding, and band combination encoding logic in the visual editor (`index_md3.html`) are inspired by and based on `pixel-pb`.

---

## ⚠️ Disclaimer

This tool is designed for telecommunications research and network diagnostics. Modifying baseband parameters may violate local carrier terms or radio regulations. Use at your own risk.
