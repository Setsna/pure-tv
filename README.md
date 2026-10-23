<p align="center">
  <img src="docs/stock-vs-new-side.jpg" alt="MiVibe TV Launcher - Apple TV Experience on Xiaomi TVs" width="800" style="border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.5);">
</p>

<h1 align="center">MiVibe TV</h1>
<p align="center">
  <strong>The ultra-clean, Apple TV-inspired launcher built exclusively for Xiaomi Smart TVs.</strong><br>
  Zero ads, zero background telemetry, luxury glass aesthetics, real-time Chanakyapuri weather intelligence, and a 1-minute plug-and-play setup that takes over as the native home screen while preserving 100% of your hardware features.
</p>

<p align="center">
  <a href="release/CleanYourTV-v2.2.0.apk"><img src="https://img.shields.io/badge/Download_APK-v2.2.0_(901_KB)-brightgreen?style=for-the-badge&logo=android" alt="Download Release APK"></a>
  <a href="ui-audit/UI_AUDIT_REPORT.md"><img src="https://img.shields.io/badge/UI_Audit-Verified_1080p-blueviolet?style=for-the-badge" alt="UI Audit Report"></a>
  <img src="https://img.shields.io/badge/Android_TV-9_·_10_·_11_·_12-blue?style=for-the-badge&logo=google" alt="Android TV">
  <img src="https://img.shields.io/badge/License-MIT-orange?style=for-the-badge" alt="MIT License">
</p>

---

> *"Tired of 30-second boot ads, sluggish memory hogs, and PatchWall banners on your Xiaomi TV? **MiVibe** transforms any Xiaomi Smart TV into a responsive, beautiful, ad-free Apple TV experience in under 2 minutes. No root required, no firmware flashing, 100% reversible, and built specifically to respect your hardware's 2 GB RAM."*

---

## ⚡ 1-Minute Plug-and-Play Setup

No manual ADB commands or developer setup headaches. Connect your computer to the same Wi-Fi as your TV and run the 1-click automated installer:

### macOS & Linux
```bash
git clone https://github.com/vardan/mivibe-tv.git
cd mivibe-tv
./tools/install_mivibe.sh
```

### Windows (PowerShell)
```powershell
git clone https://github.com/vardan/mivibe-tv.git
cd mivibe-tv
.\tools\install_mivibe.ps1
```

The installer will:
1. Automatically pair with your Xiaomi TV over Wi-Fi (`port 5555`).
2. Safely back up your package configuration to `backups/`.
3. Reversibly disable Xiaomi ad services, telemetry daemons, and PatchWall.
4. Install the lightweight **MiVibe** release APK and register it as your default native home.
5. Apply hardware animation scales (`0.5x`) and AOT ARM64 compilation for zero-stutter 60 fps navigation.

---

## 💎 Feature Breakdown: What's In vs. What's Out

| Feature Category | Included & Optimized (In MiVibe) | Excluded & Permanently Removed (Out) |
| :--- | :--- | :--- |
| **Launcher UX** | Progressive 4+4 Apple TV Dock (4 primary apps + 4 secondary on D-pad Down), spring-animated focus lens (`GlassView`), minimalist negative space layout. | Stock Xiaomi PatchWall UI, large promotional banners, recommendation carousels, floating hero app titles. |
| **Screensaver & Idle** | Custom Apple TV Weather + Time + Date HUD over rotating Apple MacBook/TV nature wallpapers with anti-burn-in Ken Burns pan and alternating corner protection. | The temporary `"Happy Birthday Papa"` countdown screen (fully retired). |
| **Weather Engine** | High-legibility 2-Tier weather card: Live temp (`52sp`), conditions (`14sp`), Feels Like (`14sp`), AQI & rain forecast, + 4 hourly forecast columns (+3h/+6h/+12h) with Open-Meteo primary + `wttr.in` fallback. | Heavy multi-row widget cards, redundant header temperature text, font overlap. |
| **Hardware & Inputs** | 100% preserved: Live TV tuner (`com.mitv.livetv`), HDMI switching (Airtel Set-Top Box on HDMI 2), Chromecast (`backdrop`), Miracast/AirPlay (`smartshare`), Google Assistant voice search (`katniss`), USB Media Player (`mediaexplorer`), Play Store auto-updates. | Xiaomi firmware auto-updater (`com.xiaomi.mitv.upgrade`) to prevent OS patches from overwriting your custom setup. |
| **Performance & RAM** | 3-minute background app memory reaper (`killBackgroundProcesses` + GC), zero-window overdraw (`setBackgroundDrawable(null)`), AOT native ARM64 compilation (`speed`), 18 bloatware/ad packages disabled. | QR-code Wi-Fi sharing module (`QrUtils`, ZXing library — removed to shrink APK to **277 KB**). |

---

## 📺 Design & Architecture

### 1. Progressive 4+4 Apple TV Dock
- **Primary Dock:** The 4 apps you actually watch (`Xiaomi TV+`, `JioHotstar`, `YouTube`, `Spotify`) rest on a 30dp translucent glass shelf (`rgba(255, 255, 255, 0.08)`).
- **Secondary App Drawer:** Pressing **Down** on your remote slides up your utility and secondary streaming row (`Netflix`, `Prime Video`, `Miracast`, `USB Player`) with 0ms input lag.

### 2. Dual-Tier Weather Intelligence Card
- **Large 10-Foot Living Room Typography:** Current temperature displayed at **52sp** Outfit font with custom high/low and atmospheric stats (`Feels 28°`, `💧 67%`, `🍃 AQI 84 · Moderate`, `✓ No rain 24h`).
- **Guaranteed Zero-Overlap Clearance:** Constrained with `layout_toEndOf="@+id/clockBlock"` and 36dp physical barrier margins. Never collides with clock numbers even under system accessibility font scaling.

### 3. Xiaomi Hardware Remote Interceptor
- Neutralizes the physical PatchWall button (scancode 401) and hardware OTT buttons via `HomeKeyService`, keeping you in your tranquil launcher instead of being dumped into sponsored ad feeds.

### 4. Zero Memory Pressure (<45 MB PSS)
- Xiaomi TVs typically operate with 2 GB RAM, easily choked by dormant background streaming apps. MiVibe runs a lightweight background reaper that terminates idle video decoders after 3 minutes, maintaining a steady 60 fps across the entire system.

---

## 🔌 Hardware Preservation Matrix

| Hardware Feature | Xiaomi Stock App Package | MiVibe Status |
| :--- | :--- | :--- |
| **Airtel DTH / STB** | `com.mitv.livetv` / `com.xiaomi.mitv.tvplayer` | ✅ **1-Click Direct Launch** from header pill or HDMI dropdown |
| **HDMI 1 / 2 / 3 Ports** | Native Android TV Input Framework (TIF) | ✅ **Live Dropdown** with green connection status dots |
| **Chromecast Built-in** | `com.google.android.apps.mediashell` | ✅ **100% Operational** (Cast from phone/laptop anytime) |
| **Miracast / AirPlay** | `com.xiaomi.mitv.smartshare` | ✅ **Preserved** in Secondary App Drawer |
| **Google Voice Assistant**| `com.google.android.katniss` | ✅ **Preserved** via microphone remote key |
| **USB Media Player** | `com.xiaomi.mitv.mediaexplorer` | ✅ **Preserved** in Secondary App Drawer |
| **Google Play Store** | `com.android.vending` | ✅ **Preserved** for app installs & background updates |

---

## 🔄 100% Reversible: Instant Stock Restore

If you ever decide to sell your TV or revert to factory stock PatchWall, simply run:

```bash
./tools/restore_stock_tv.sh <TV_IP>:5555
```

This single command re-enables all original Xiaomi packages, restores stock animation scales, sets PatchWall back as the default home activity, and leaves your TV exactly as it was out of the box.

---

## 📂 Repository Structure

```text
mivibe-tv/
├── launcher/                  # Core Android TV launcher source
│   ├── app/src/main/          # Java architecture, XML layouts, Apple nature wallpapers
│   └── build.gradle           # R8 minified build configuration (Release APK)
├── release/                   # Pre-compiled standalone release APKs
│   └── CleanYourTV-v2.2.0.apk # Verified 1080p release binary (901 KB)
├── tools/
│   ├── install_mivibe.sh      # 1-Click interactive setup script (Mac & Linux)
│   ├── install_mivibe.ps1     # 1-Click interactive setup script (Windows)
│   ├── tv_tune.sh             # 5-second RAM & animation maintenance harness
│   └── restore_stock_tv.sh    # Instant 1-click factory restore script
├── backups/                   # Package backup snapshots
├── docs/                      # Screenshots, architecture diagrams, and design specs
├── README.md                  # Comprehensive product guide & documentation
└── LICENSE                    # MIT License
```

---

## 📄 License

Distributed under the [MIT License](LICENSE). Built for owners of Xiaomi Smart TVs who want a calm, fast, ad-free television experience.
