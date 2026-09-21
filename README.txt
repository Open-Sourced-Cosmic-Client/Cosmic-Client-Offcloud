# 🌌 Cosmic Client Offcloud - Release 1.0 (Windows Universal)

Welcome to **Cosmic Client Offcloud Release 1.0** — the complete, authentic, 100% offline distribution of Cosmic Client 1.8.9 with Java bytecode agent injection, ultra FPS optimization, and zero personal data bloat.

---

## 🚀 How to Play Immediately

### Option 1: Standalone All-In-One Launcher (Recommended)
Double-click:
```
Cosmic Client Offcloud-1.0 Launcher.exe
```
- **Zero Configuration**: Contains everything required to launch and play (Client JAR, bytecode agent, Java 19 runtime, clean native DLLs, sound effects, textures).
- **Runs Everywhere**: Even on a fresh Windows installation with zero Java, zero Minecraft, or zero internet connection!
- **Features**: Includes authentic Cosmic Client UI, RAM allocation slider, offline account switcher, JVM optimization presets (Ultra FPS, Competitive, Low End), and console diagnostics.

### Option 2: 1-Click Interactive Menu
Double-click:
```
START-HERE-WINDOWS.bat
```
Offers an automated menu to choose between GUI launcher, instant direct game launch, or auto-starts in 5 seconds.

### Option 3: Instant Fast Game Launch (Bypasses Launcher)
Double-click:
```
CosmicClient-Direct.exe
```
or double-click:
```
Launch-Cosmic-Direct.bat
```
Boots the 1.8.9 client directly with the bytecode agent and maximum FPS optimization flags in under 2 seconds.

### Option 4: Electron Desktop Launcher
```bash
npm install
npm start
```
or double-click:
```
Launch-GUI.bat
```

---

## 🛡️ Privacy & Zero-Bloat Guarantee

This release has been systematically sanitized to ensure **100% privacy**:
- **Personal Accounts**: Purged; generic `CosmicPlayer` offline profile initialized.
- **Server History**: `servers.dat` completely stripped.
- **World Saves & Radar**: `saves/` and map caches completely stripped.
- **User Screenshots & Schematics**: Stripped clean; empty user folders provided.
- **Player Skin Caches**: `assets_18/skins` purged.
- **Session Logs & Crash Reports**: Purged.
- **Dead Bloat**: Discontinued 2015 Twitch streaming DLLs and redundant audio tracks removed.

---

## 📦 What is Included in this Package

```
Cosmic Client Offcloud Release 1.0/
├── Cosmic Client Offcloud-1.0 Launcher.exe   # All-in-one standalone executable (contains embedded vault)
├── CosmicClient-Direct.exe                   # Instant 1-click game launcher
├── START-HERE-WINDOWS.bat                    # Interactive universal starter script
├── Launch-Cosmic-Direct.bat                  # Direct CLI batch launcher
├── Launch-GUI.bat                            # GUI launcher batch runner
├── cosmic-agent.jar                          # Pure compiled Java agent bytecode engine
├── cosmic.ico                                # Authentic vector-perfect Cosmic Client icon
├── accounts.json                             # Default offline player profile
├── README.md & README.txt                    # Complete user & developer documentation
│
├── CosmicClient-x64/                         # Unpacked offline game vault (Windows 64-bit)
│   ├── 1.8/                                  # CosmicClient-1.8.9.jar & clean LWJGL natives
│   ├── assets_18/                            # Essential indexed sound effects, textures, models
│   ├── bootstrap/java/                       # Bundled Zulu OpenJDK 19 JRE
│   ├── Launcher.jar                          # Bootstrap client loader
│   ├── 1.8.json                              # 1.8.9 asset index
│   └── optionscosmic.txt & optionsof.txt     # Clean game presets
│
├── cosmic-agent-src/                         # Full Java Agent Source Code (83 Java classes)
│   ├── src/main/java/com/cosmic/launcher/    # Anti-tamper, AuthHelper, FPS Boost, Class Transformers
│   ├── libs/                                 # Build dependencies (jna-5.13.0.jar)
│   ├── build.js                              # Universal Java 8 bytecode compiler
│   └── build.sh                              # Shell build script
│
├── reverse-engineering/                      # Reverse engineering & class inspection tooling
│   ├── inspect-classes.js                    # ASM / bytecode inspection scripts
│   ├── extract-resources.js                  # JAR resource unpacker
│   └── download-all-offline.js               # Offline sync automation
│
└── src/ & lib/                               # Electron GUI launcher source code
```

---

## 🛠️ Developer & Rebuilding Guide

### Recompiling the Java Agent (`cosmic-agent.jar`)
```bash
node cosmic-agent-src/build.js
```

### Rebuilding the Standalone Executable (`Cosmic Client Offcloud-1.0 Launcher.exe`)
```bash
powershell -ExecutionPolicy Bypass -File scripts/build-standalone-exe.ps1
```

### Re-running Full Packaging
```bash
node scripts/package-release-1.0.js
```

Enjoy Cosmic Client Offcloud Release 1.0!
