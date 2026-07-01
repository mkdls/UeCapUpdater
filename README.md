# UECapUpdater

[English] | [繁體中文](README_zh.md)

A reactive, real-time baseband runtime configuration overriding tool tailored for Google Pixel devices (Tensor/Samsung Modem infrastructure). It bypasses system namespace isolations to bind-mount customized `.binarypb` config files directly into the modem daemons, enabling persistent network capability updates without bricking risk.

---

## 🛠️ Verified Environment

> [!IMPORTANT]
> This tool has been rigorously tested and verified exclusively on the following configuration. Operational stability or compatibility is **NOT guaranteed** on any other devices, older Android revisions, or different patch levels.

* **Device**: Google Pixel 9 Pro Fold
* **OS Version**: Android 17 (Developer/Beta/Preview Branch)
* **Build Number**: `CP2A.260605.021`
* **Root Environment**: Magisk `30.7 (30700)`

---

## 💡 What This App Can Do

* **Flash Custom Carrier Configurations Instantly**: Inject your customized `*.binarypb` files directly into the live modem filesystem without modifying the underlying `/vendor` partition read-only blocks.
* **Accelerate Single-File Mounting**: Heavy optimizations switch repetitive namespace boundary crossings to an atomic batch mount process, minimizing IO blockage and speeding up execution drastically.
* **Extract & Aggregate Magisk Modules**: Directly parse, unzip, and consolidate multiple baseband-related Magisk flashable `.zip` packages from scoped storage into a unified global mount configuration.
* **Stream Hybrid Terminal Logs Line-by-Line**: Combines internal app staging events and low-level `shamp`/`UECAP` logcat output streams into a single reactive terminal console with automatic scrolling.
* **Deterministic Signal-Lock Tracking**: Implements a dual-layer state lock (Android Core Telephony Registry check + `shamp` boot initialization tracing) to wait out real hardware registration perfectly before closing streams.
* **Generate Systemless Boot Modules**: Automatically creates a persistent startup mount script in the background to seamlessly retain your modifications across device reboots.
* **One-Click Factory Fallback**: Safely unmounts all active runtime overrides inside PID 1, letting the modem daemon securely drop back to the clean, default official carrier profile within seconds.

---

## 📖 Usage

### 1. Preparations
* Ensure your Pixel device has granted full Root access to this app.
* Place your customized carrier configuration files (`*.binarypb`, e.g., `lte_xxx.binarypb`) into your phone's storage.

### 2. Single-File Mode
1. Switch to the **Single File** tab inside the app.
2. Click **Select Files** and pick one or more `.binarypb` files. The UI will update to display `✅ X files ready`.
3. Click **Apply & Restart Radio**. The live hybrid console will immediately stream the PID 1 unmounting, batch bind-mounting, and raw modem process recycles.
4. Wait for the terminal to display `[INFO] Signal lock confirmed! Awaiting UECAP file reads...` and finish the 5-second log flushing countdown before closing.

### 3. Module Extract Mode
1. Switch to the **Module Extract** tab.
2. Click **Select Modules** and load your Magisk flashable `.zip` configuration packages.
3. The app will automatically unzip, parse the tree structure, aggregate all valid binary files, and execute the batch injection runtime seamlessly.

### 4. Rollback to Factory
* In the Single-File tab, simply click **Restore Factory**. The app will wipe all virtual runtime overrides in PID 1 and force the modem daemon (`shamp`) to fall back to the secure official carrier firmware.

---

## 🚀 Technical Insights

The utility works by deploying a multi-stage execution runner:
1. **Namespace Piercing**: Issues an atomic `nsenter -t 1 -m` call to safely cross the PID 1 namespace boundary.
2. **Dynamic Bind Mount**: Binds local cache files onto `/vendor/firmware/uecapconfig/*.binarypb` seamlessly.
3. **Raw Radio Booting**: Hard-kills modem daemons (`rild`, `shamp`, `vcd`) to force a hardware fallback, forcing the baseband parser tool to re-evaluate configuration payloads from the dynamically overriden environment on service revival.

---

## ⚠️ Disclaimer

This tool is designed strictly for telecommunications research and infrastructure diagnostics. Modifying baseband parameters may violate local carrier regulations or radio laws. Use at your own risk.