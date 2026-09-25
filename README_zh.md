# UECapUpdater

[English](README.md) | [繁體中文]

一個專為 Google Pixel 系列（搭載 Tensor / 三星基帶架構）設計的基帶配置動態注入與編輯工具。透過穿透系統命名空間隔離，將自定義的 `.binarypb` 配置直接綁定掛載至基帶守護進程中，實現即時網絡能力升級與持久化套用。

---

## 🛠️ 實機測試環境

> [!IMPORTANT]
> 本工具在以下特定硬體與系統環境中完成測試與驗證，**不保證**在其他設備、舊版 Android 或不同安全修補程式版本上的運行效果。

* **設備 (Device)**: Google Pixel 9 Pro Fold
* **系統版本 (OS Version)**: Android 17 
* **修補程式版本 (Build Number)**: `CP2A.260605.021`
* **Root 框架 (Root Environment)**: Magisk `30.7 (30700)` / KernelSU

---

## 💡 主要功能

* **電信商配置動態注入**：直接將自定義 `*.binarypb` 檔案綁定掛載至執行中的基帶檔案系統，無需修改 `/vendor` 唯讀分區。
* **視覺化 UECAP (.binarypb) 編輯器**：內建 Material Design 3 風格的 Web 編輯器，支援 NR/LTE 頻段組合編輯、自定義 Feature Sets、符合 3GPP 規則的批量 Combinations 生成（如 `b3+b7C+b8A+n78C`）、SA ULCA 生成，並可直接導出 `.binarypb` 或 Magisk `.zip` 刷機包。
* **Magisk / KernelSU 模組提取**：自動解壓與解析多個 Magisk `.zip` 刷機包，提取隱藏於其中的 `.binarypb` 配置檔案並進行批量套用。
* **生效配置與系統狀態掃描**：掃描 PID 1 命名空間中的掛載點、快取檔案、`shamp` 守護進程解析日誌，並可一鍵啟動 Pixel 底層基帶診斷工具（MDS/VCD）。
* **實時日誌串流與控制台除錯**：在套用與恢復過程中實時串流 `UECAP` 及 `shamp` 的 Logcat 日誌，並提供內建 Root Shell 控制台與常用診斷快捷鍵。
* **硬體級數據機冷重置**：向 `/dev/umts_router` 寫入 `AT+GOOGCPRESET` 指令，強制 Cellular Processor（CP）斷電冷啟動並重新載入 NV 快取。
* **動態信號鎖定守護**：結合 Android Telephony Registry (`telephony.registry`) 與 `shamp` 進程初始化特徵，自動過濾歷史殘留日誌，確保網路註冊完成。
* **自動生成 Magisk 開機掛載模組**：自動生成持久化開機模組（`/data/adb/modules/uecapupdater_live`），保障手機重啟後設定依然生效，並可在恢復原廠時自動清除。
* **一鍵恢復原廠基帶**：安全卸載 PID 1 空間內的所有動態掛載並移除持久化模組，使基帶守護進程回歸官方唯讀韌體狀態。

---

## 📖 功能導覽與使用說明

### 1. 單檔套用模式
1. 切換至 **單檔套用模式** 標籤頁。
2. 點擊 **選擇檔案**，選取一或多個 `.binarypb` 配置文件。
3. 點擊 **套用並重載基帶**。控制台將實時顯示卸載舊掛載、綁定掛載、硬體冷重置及信號鎖定過程。

### 2. 模組提取模式
1. 切換至 **Magisk 模組提取** 標籤頁。
2. 點擊 **選擇多個 Magisk/KernelSU 模組 (.zip)**。
3. App 將自動解析模組結構、歸納配置檔案並執行批量注入。

### 3. 視覺化 UECAP 編輯器
1. 從右上角選單開啟 **編輯器**。
2. 載入本機 `.binarypb` 檔案或還原歷史編輯紀錄。
3. 可手動編輯頻段參數、新增自定義 DL/UL Feature Sets，或使用 **Batch Gen** 根據 3GPP 規則自動生成頻段組合。
4. 編輯完成後可直接匯出為 `.binarypb` 檔或 Magisk `.zip` 刷機包。

### 4. 生效配置與診斷
1. 從右上角選單開啟 **生效配置**。
2. 點擊 **重新整理系統狀態** 查看 PID 1 的活動掛載、快取檔案與 `shamp` 解析紀錄。
3. 點擊 **啟動 Pixel 底層基帶診斷 (MDS)** 可直接開啟官方 MDS 診斷介面。

### 5. 終端機除錯
1. 從右上角選單開啟 **終端除錯**。
2. 可點擊快捷晶片（傾印 UECAP 日誌、檢查掛載點、列出 Vendor 檔案、清除 Logcat）或自行輸入 Root Shell 指令。

### 6. 恢復原廠基帶
* 在單檔套用頁面點擊 **恢復原廠基帶**，App 將卸載 PID 1 中的動態掛載、清除 `/data/adb/modules/uecapupdater_live` 模組，並將數據機重置回官方韌體。

---

## 🚀 技術原理簡述

本工具透過以下階段實現系統級修改：
1. **空間穿透**：利用 `nsenter -t 1 -m` 進入 PID 1 的頂層隔離空間。
2. **全局綁定**：將暫存區檔案以 `bind` 模式掛載至 `/vendor/firmware/uecapconfig/`。
3. **硬體冷啟動**：透過 `/dev/umts_router` 發送 `AT+GOOGCPRESET` 指令觸發 Cellular Processor 徹底重置，迫使基帶重新載入 NV 與配置檔案。
4. **信號狀態鎖定**：查詢 `dumpsys telephony.registry` 並過濾歷史紀錄，確認硬體成功完成電信商網路註冊。

---

## 🙏 特別鳴謝 (Acknowledgments)

* **[NXij/pixel-pb](https://github.com/NXij/pixel-pb)**：特別感謝原作者 NXij。視覺化編輯器（`index_md3.html`）的核心介面佈局、UECAP Protobuf 解碼與頻段組合編碼邏輯啟發並源自於該專案。

---

## ⚠️ 免責聲明

本工具僅供通訊技術研究與基帶架構診斷使用。修改網絡配置可能違反當地電信商規範或無線電法規，請自行承擔由此帶來的硬體與法律風險。
