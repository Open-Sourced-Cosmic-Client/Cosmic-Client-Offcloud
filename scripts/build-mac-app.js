const fs = require('fs');
const path = require('path');
const archiver = require('archiver');

const ROOT = path.resolve(__dirname, '..');
const MAC_DIR = path.join(ROOT, 'Mac');
const APP_DIR = path.join(ROOT, 'Cosmic Client.app');
const MAC_APP_DIR = path.join(MAC_DIR, 'Cosmic Client.app');
const OUT_ZIP_MAIN = path.join(ROOT, 'Cosmic-Client-Mac.zip');
const OUT_ZIP_MIRROR = path.join(ROOT, 'Mac.zip');
const OUT_ZIP_APP_ONLY = path.join(ROOT, 'Cosmic-Client.app.zip');

console.log('========================================================');
console.log('=== BUILDING 100% SELF-CONTAINED macOS COSMIC CLIENT.APP ===');
console.log('========================================================');

// 1. Load 1.8.json to filter essential assets
const validAssetHashes = new Set();
try {
    const idxPath = path.join(ROOT, 'CosmicClient-x64', '1.8.json');
    if (fs.existsSync(idxPath)) {
        const idxData = JSON.parse(fs.readFileSync(idxPath, 'utf8'));
        if (idxData && idxData.objects) {
            for (const [key, obj] of Object.entries(idxData.objects)) {
                const lower = key.toLowerCase();
                if (lower.startsWith('minecraft/sounds/music/') ||
                    lower.startsWith('minecraft/sounds/records/') ||
                    lower.startsWith('minecraft/mapwriter/prebaked/')) {
                    continue;
                }
                if (obj && obj.hash) {
                    validAssetHashes.add(obj.hash.toLowerCase());
                }
            }
        }
    }
    console.log(`[MacApp] Indexed ${validAssetHashes.size} essential 1.8.9 gameplay assets.`);
} catch (e) {
    console.warn('[MacApp] Could not index 1.8.json:', e.message);
}

function copyRecursiveSync(src, dest, filterFn = () => true) {
    if (!fs.existsSync(src)) return;
    const stat = fs.statSync(src);
    if (stat.isDirectory()) {
        if (!filterFn(src, true)) return;
        if (!fs.existsSync(dest)) {
            fs.mkdirSync(dest, { recursive: true });
        }
        for (const child of fs.readdirSync(src)) {
            const childSrc = path.join(src, child);
            const childDest = path.join(dest, child);
            copyRecursiveSync(childSrc, childDest, filterFn);
        }
    } else {
        if (filterFn(src, false)) {
            fs.mkdirSync(path.dirname(dest), { recursive: true });
            try {
                if (fs.existsSync(dest)) {
                    try { fs.chmodSync(dest, 0o666); } catch(e){}
                }
                fs.copyFileSync(src, dest);
            } catch (err) {
                try {
                    fs.unlinkSync(dest);
                    fs.copyFileSync(src, dest);
                } catch (e2) {}
            }
        }
    }
}

function isExcludedFile(rel, isDir = false) {
    const norm = rel.replace(/\\/g, '/');
    if (norm.startsWith('resourcepacks/') && norm !== 'resourcepacks') return true;
    if (norm.startsWith('schematics/') && norm !== 'schematics') return true;
    if (norm.startsWith('screenshots/') && norm !== 'screenshots') return true;
    if (norm.startsWith('shaderpacks/') && norm !== 'shaderpacks') return true;
    if (norm.startsWith('cosmic/screenshots/')) return true;
    if (norm.startsWith('cosmic/schematics/')) return true;
    if (norm.startsWith('scratch') || norm.includes('/scratch')) return true;
    if (norm.startsWith('assets_18/skins') || norm.includes('/assets_18/skins')) return true;
    if (norm.startsWith('logs/') || norm === 'logs') return true;
    if (norm.startsWith('crash-reports/') || norm === 'crash-reports') return true;
    if (norm.startsWith('saves/') || norm === 'saves') return true;
    if (norm.startsWith('stats/') || norm === 'stats') return true;
    if (norm === 'servers.dat' || norm.startsWith('servers.dat')) return true;
    if (norm.startsWith('1.12') || norm.startsWith('assets_112')) return true;
    if (norm.endsWith('.DS_Store') || norm.includes('/._') || norm.startsWith('._')) return true;
    if (norm.startsWith('bootstrap/java') && !norm.startsWith('bootstrap/java-mac')) return true;
    if (norm.startsWith('1.8/bin-1.8') || norm.startsWith('1.8/bin-linux')) return true;
    if (norm === 'Launch-Offline.bat') return true;

    // Filter unindexed objects in assets_18/objects
    if (norm.startsWith('assets_18/objects/')) {
        const parts = norm.split('/');
        if (!isDir && parts.length >= 4) {
            const hash = parts[parts.length - 1].toLowerCase();
            if (validAssetHashes.size > 0 && !validAssetHashes.has(hash)) {
                return true;
            }
        }
    }
    return false;
}

// 2. Prepare Cosmic Client.app directory layout
const contentsDir = path.join(APP_DIR, 'Contents');
const macosDir = path.join(contentsDir, 'MacOS');
const resourcesDir = path.join(contentsDir, 'Resources');
const embeddedClientDir = path.join(resourcesDir, 'CosmicClient-x64');

fs.mkdirSync(macosDir, { recursive: true });
fs.mkdirSync(resourcesDir, { recursive: true });

// 3. Write Info.plist
const plistContent = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>CosmicLauncher</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>com.cosmic.offcloud.client</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Cosmic Client</string>
    <key>CFBundleDisplayName</key>
    <string>Cosmic Client</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>2.7.0</string>
    <key>CFBundleVersion</key>
    <string>2.7.0</string>
    <key>LSMinimumSystemVersion</key>
    <string>10.13</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key>
    <true/>
</dict>
</plist>`;
fs.writeFileSync(path.join(contentsDir, 'Info.plist'), plistContent, 'utf-8');
fs.writeFileSync(path.join(contentsDir, 'PkgInfo'), 'APPL????', 'utf-8');

// 4. Install AppIcon.icns
const iconSrc = path.join(ROOT, 'CosmicClient-x64', 'assets_18', 'objects', '99', '991b421dfd401f115241601b2b373140a8d78572');
if (fs.existsSync(iconSrc)) {
    fs.copyFileSync(iconSrc, path.join(resourcesDir, 'AppIcon.icns'));
    console.log('[MacApp] Installed AppIcon.icns -> Resources');
}

// 5. Write CosmicLauncher script with universal standalone launch capability
const launcherScript = `#!/bin/bash
# ==============================================================================
# Cosmic Client Offcloud - Native macOS Standalone Application Launcher
# Compatible with: Apple Silicon (M1/M2/M3/M4/M5) & Intel MacBooks
# ==============================================================================

export PATH="/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:/usr/local/sbin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
unset _JAVA_OPTIONS JAVA_TOOL_OPTIONS JAVA_OPTIONS IBM_JAVA_OPTIONS

CONTENTS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_BUNDLE="$(cd "$CONTENTS_DIR/.." && pwd)"
RESOURCES_DIR="$CONTENTS_DIR/Resources"

if [ -d "$RESOURCES_DIR/CosmicClient-x64" ]; then
    INSTALL_DIR="$RESOURCES_DIR/CosmicClient-x64"
elif [ -d "$APP_BUNDLE/CosmicClient-x64" ]; then
    INSTALL_DIR="$APP_BUNDLE/CosmicClient-x64"
elif [ -d "$(dirname "$APP_BUNDLE")/CosmicClient-x64" ]; then
    INSTALL_DIR="$(dirname "$APP_BUNDLE")/CosmicClient-x64"
else
    INSTALL_DIR="$RESOURCES_DIR/CosmicClient-x64"
fi

# 1. Strip Gatekeeper quarantine flags automatically
if [ "$(uname -s)" = "Darwin" ]; then
    xattr -cr "$APP_BUNDLE" 2>/dev/null || true
    xattr -dr com.apple.quarantine "$APP_BUNDLE" 2>/dev/null || true
    codesign --force --deep -s - "$APP_BUNDLE" 2>/dev/null || true
fi

# 2. Permissions on executable and dylibs
chmod 755 "$0" 2>/dev/null || true
find "$INSTALL_DIR" -type f -name "*.dylib" -exec chmod 755 {} + 2>/dev/null || true

# 3. Writable User Data Directory in Application Support
USER_DATA_DIR="$HOME/Library/Application Support/CosmicClient"
mkdir -p "$USER_DATA_DIR" "$USER_DATA_DIR/screenshots" "$USER_DATA_DIR/resourcepacks" "$USER_DATA_DIR/saves" "$USER_DATA_DIR/logs" 2>/dev/null || true

for cfg in options.txt optionscosmic.txt optionsof.txt accounts.json launcher-config.json servers.dat; do
    if [ ! -f "$USER_DATA_DIR/$cfg" ] && [ -f "$INSTALL_DIR/$cfg" ]; then
        cp "$INSTALL_DIR/$cfg" "$USER_DATA_DIR/$cfg" 2>/dev/null || true
    fi
done

ARCH="$(uname -m)"

# 4. Resolve Natives Directory
if [ "$ARCH" = "arm64" ]; then
    if [ -d "$INSTALL_DIR/1.8/bin-mac-arm64" ]; then
        NATIVES_DIR="$INSTALL_DIR/1.8/bin-mac-arm64"
    elif [ -d "$INSTALL_DIR/1.8/bin-mac" ]; then
        NATIVES_DIR="$INSTALL_DIR/1.8/bin-mac"
    else
        NATIVES_DIR="$INSTALL_DIR/1.8/bin-1.8"
    fi
else
    if [ -d "$INSTALL_DIR/1.8/bin-mac-x64" ]; then
        NATIVES_DIR="$INSTALL_DIR/1.8/bin-mac-x64"
    elif [ -d "$INSTALL_DIR/1.8/bin-mac" ]; then
        NATIVES_DIR="$INSTALL_DIR/1.8/bin-mac"
    else
        NATIVES_DIR="$INSTALL_DIR/1.8/bin-1.8"
    fi
fi
find "$NATIVES_DIR" -type f -name "*.dylib" -exec chmod 755 {} + 2>/dev/null || true

CLIENT_JAR="$INSTALL_DIR/1.8/CosmicClient-1.8.9.jar"
AGENT_JAR="$INSTALL_DIR/cosmic-agent.jar"
ASSETS_DIR="$INSTALL_DIR/assets_18"
BOOTSTRAP_JAR="$INSTALL_DIR/Launcher.jar"

# 5. Java Resolution
is_runnable_java() {
    local bin="$1"
    if [ -n "$bin" ] && [ -f "$bin" ] && [ -x "$bin" ]; then
        if "$bin" -version >/dev/null 2>&1; then
            return 0
        fi
    fi
    return 1
}

JAVA_EXE=""

BOOTSTRAP_CANDIDATES=(
    "$USER_DATA_DIR/bootstrap/java-mac-arm64/bin/java"
    "$USER_DATA_DIR/bootstrap/java-mac-arm64/zulu-19.jre/Contents/Home/bin/java"
    "$USER_DATA_DIR/bootstrap/java-mac-x64/bin/java"
    "$USER_DATA_DIR/bootstrap/java-mac-x64/zulu-19.jre/Contents/Home/bin/java"
    "$USER_DATA_DIR/bootstrap/java/bin/java"
    "$INSTALL_DIR/bootstrap/java-mac-arm64/bin/java"
    "$INSTALL_DIR/bootstrap/java-mac-x64/bin/java"
    "$INSTALL_DIR/bootstrap/java/bin/java"
)
for cand in "\${BOOTSTRAP_CANDIDATES[@]}"; do
    if is_runnable_java "$cand"; then
        JAVA_EXE="$cand"
        break
    fi
done

if [ -z "$JAVA_EXE" ] && [ -x "/usr/libexec/java_home" ]; then
    SYS_JH="$(/usr/libexec/java_home 2>/dev/null)"
    if [ -n "$SYS_JH" ] && is_runnable_java "$SYS_JH/bin/java"; then
        JAVA_EXE="$SYS_JH/bin/java"
    fi
fi

if [ -z "$JAVA_EXE" ]; then
    MAC_JAVA_CANDIDATES=(
        "/Library/Java/JavaVirtualMachines/zulu-19.jdk/Contents/Home/bin/java"
        "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java"
        "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java"
        "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java"
        "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java"
        "/opt/homebrew/opt/openjdk/bin/java"
        "/opt/homebrew/opt/openjdk@17/bin/java"
        "/opt/homebrew/opt/openjdk@21/bin/java"
        "/usr/local/opt/openjdk/bin/java"
        "/usr/local/opt/openjdk@17/bin/java"
        "$HOME/.minecraft/cosmic/bootstrap/java/bin/java"
    )
    for cand in "\${MAC_JAVA_CANDIDATES[@]}"; do
        if is_runnable_java "$cand"; then
            JAVA_EXE="$cand"
            break
        fi
    done
fi

if [ -z "$JAVA_EXE" ] && [ -d "/Library/Java/JavaVirtualMachines" ]; then
    while IFS= read -r f; do
        if is_runnable_java "$f"; then
            JAVA_EXE="$f"
            break
        fi
    done < <(find /Library/Java/JavaVirtualMachines -type f -name java 2>/dev/null)
fi

if [ -z "$JAVA_EXE" ] && command -v java >/dev/null 2>&1; then
    SYS_PATH_JAVA="$(command -v java)"
    if is_runnable_java "$SYS_PATH_JAVA"; then
        JAVA_EXE="$SYS_PATH_JAVA"
    fi
fi

if [ -z "$JAVA_EXE" ]; then
    mkdir -p "$USER_DATA_DIR/bootstrap"
    TEMP_ARCHIVE="$USER_DATA_DIR/bootstrap/zulu19.tar.gz"
    if [ "$ARCH" = "arm64" ]; then
        JAVA_URL="https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-macosx_aarch64.tar.gz"
        TARGET_DIR="$USER_DATA_DIR/bootstrap/java-mac-arm64"
    else
        JAVA_URL="https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-macosx_x64.tar.gz"
        TARGET_DIR="$USER_DATA_DIR/bootstrap/java-mac-x64"
    fi

    osascript -e 'display notification "Configuring Java runtime for Cosmic Client..." with title "Cosmic Client Setup"' 2>/dev/null || true
    mkdir -p "$TARGET_DIR"
    if curl -s -f -L -o "$TEMP_ARCHIVE" "$JAVA_URL"; then
        tar -xzf "$TEMP_ARCHIVE" -C "$TARGET_DIR" --strip-components=1 2>/dev/null || tar -xzf "$TEMP_ARCHIVE" -C "$TARGET_DIR"
        rm -f "$TEMP_ARCHIVE" 2>/dev/null || true
        xattr -cr "$TARGET_DIR" 2>/dev/null || true
        xattr -dr com.apple.quarantine "$TARGET_DIR" 2>/dev/null || true
        chmod -R 755 "$TARGET_DIR" 2>/dev/null || true
        find "$TARGET_DIR" -type f -name "java" -exec chmod 755 {} + 2>/dev/null || true
        find "$TARGET_DIR" -type f -name "*.dylib" -exec chmod 755 {} + 2>/dev/null || true
        if is_runnable_java "$TARGET_DIR/bin/java"; then
            JAVA_EXE="$TARGET_DIR/bin/java"
        elif is_runnable_java "$TARGET_DIR/zulu-19.jre/Contents/Home/bin/java"; then
            JAVA_EXE="$TARGET_DIR/zulu-19.jre/Contents/Home/bin/java"
        fi
    fi
fi

if [ -z "$JAVA_EXE" ] || ! is_runnable_java "$JAVA_EXE"; then
    osascript -e 'display alert "Java Runtime Required" message "Cosmic Client requires Java 17 or newer.\\n\\nPlease install Java using Homebrew:\\n  brew install openjdk@17\\n\\nOr download Azul Zulu JRE from:\\n  https://azul.com/downloads" as critical' 2>/dev/null || true
    exit 1
fi

JAVA_MAJOR=17
RAW_VER="$("$JAVA_EXE" -version 2>&1)"
if echo "$RAW_VER" | grep -qE '(version "1\.8\.|"8\.)'; then
    JAVA_MAJOR=8
elif echo "$RAW_VER" | grep -qE '(version "19\.|"19)'; then
    JAVA_MAJOR=19
elif echo "$RAW_VER" | grep -qE '(version "21\.|"21)'; then
    JAVA_MAJOR=21
elif echo "$RAW_VER" | grep -qE '(version "22\.|"22|"23\.|"24\.)'; then
    JAVA_MAJOR=21
fi

USERNAME="CosmicPlayer"
UUID="00000000-0000-0000-0000-000000000000"
ACCOUNTS_FILE="$USER_DATA_DIR/accounts.json"
if [ ! -f "$ACCOUNTS_FILE" ]; then
    ACCOUNTS_FILE="$INSTALL_DIR/accounts.json"
fi
if [ -f "$ACCOUNTS_FILE" ]; then
    PARSED_USER="$(grep -o '"username": *"[^"]*"' "$ACCOUNTS_FILE" 2>/dev/null | head -n 1 | cut -d'"' -f4)"
    if [ -n "$PARSED_USER" ]; then
        USERNAME="$PARSED_USER"
    fi
fi

RAM_MIN="2048M"
RAM_MAX="4096M"
TOTAL_MEM_BYTES="$(sysctl -n hw.memsize 2>/dev/null || echo 0)"
if [ "$TOTAL_MEM_BYTES" -gt 0 ]; then
    TOTAL_MEM_GB=$((TOTAL_MEM_BYTES / 1024 / 1024 / 1024))
    if [ "$TOTAL_MEM_GB" -le 8 ]; then
        RAM_MIN="1536M"
        RAM_MAX="3072M"
    fi
fi

JAVA_HOME_DIR="$(cd "$(dirname "$JAVA_EXE")/.." 2>/dev/null && pwd)"

cd "$USER_DATA_DIR" || cd "$INSTALL_DIR" || exit 1

JVM_ARGS=(
  "-Dcosmic.java=$JAVA_HOME_DIR"
  "-Dcosmic.installdir=$INSTALL_DIR"
  "-Dcosmic.bootstrap=$BOOTSTRAP_JAR"
  "-Dcosmic.launcher.load=v1"
  "-Dcosmic.log.plain=true"
  "-Dcosmic.version=2.7.0.b84ff"
  "-Dcosmic.branch=master"
  "-Dapple.awt.application.name=Cosmic Client"
  "-Dapple.awt.application.appearance=system"
  "-Dapple.laf.useScreenMenuBar=true"
  "-XstartOnFirstThread"
  "-Duser.language=en-US"
  "-Djava.library.path=$NATIVES_DIR"
  "-Xms$RAM_MIN"
  "-Xmx$RAM_MAX"
  "-Xmn128M"
  "-XX:MaxDirectMemorySize=4096M"
  "-XX:+DisableAttachMechanism"
  "-Djdk.attach.allowAttachSelf=false"
  "-Dsun.tools.attach.enable=false"
  "-Dcosmic.hardening=true"
  "-Xshare:off"
  "-Dcosmic.offline=true"
  "-XX:+UseG1GC"
  "-XX:MaxGCPauseMillis=25"
)

if [ "$JAVA_MAJOR" -ge 9 ]; then
  JVM_ARGS+=(
    "--add-opens" "java.desktop/java.awt.event=ALL-UNNAMED"
    "--add-opens" "java.desktop/sun.awt=ALL-UNNAMED"
    "--add-opens" "java.management/sun.management=ALL-UNNAMED"
  )
fi

if [ -f "$AGENT_JAR" ]; then
  JVM_ARGS+=("-javaagent:$AGENT_JAR")
fi

exec "$JAVA_EXE" "\${JVM_ARGS[@]}" \\
  -jar "$CLIENT_JAR" \\
  --version 1.8 \\
  --assetsDir "$ASSETS_DIR" \\
  --assetIndex 1.8 \\
  --username "$USERNAME" \\
  --uuid "$UUID" \\
  --accessToken "offline" \\
  --userType "msa" \\
  --versionType CosmicClient \\
  --userProperties "{}" \\
  --width 1280 \\
  --height 720 \\
  --novid \\
  "$@"
`;

fs.writeFileSync(path.join(macosDir, 'CosmicLauncher'), launcherScript.replace(/\r\n/g, '\n'), { encoding: 'utf-8', mode: 0o755 });
console.log('[MacApp] Created executable MacOS/CosmicLauncher');

// 6. Embed the game vault inside Resources/CosmicClient-x64
console.log('[MacApp] Embedding offline vault into Cosmic Client.app/Contents/Resources/CosmicClient-x64...');
if (fs.existsSync(embeddedClientDir)) {
    try { fs.rmSync(embeddedClientDir, { recursive: true, force: true }); } catch(e){}
}

copyRecursiveSync(
    path.join(ROOT, 'CosmicClient-x64'),
    embeddedClientDir,
    (filePath, isDir) => {
        const rel = path.relative(path.join(ROOT, 'CosmicClient-x64'), filePath).replace(/\\/g, '/');
        if (isExcludedFile(rel, isDir)) return false;
        return true;
    }
);

// Ensure essential placeholders inside embedded vault
for (const p of ['screenshots', 'schematics', 'resourcepacks', 'shaderpacks']) {
    const full = path.join(embeddedClientDir, p);
    if (!fs.existsSync(full)) fs.mkdirSync(full, { recursive: true });
}

// 7. Sync Cosmic Client.app to Mac/ folder
console.log('[MacApp] Syncing Cosmic Client.app to Mac/ directory...');
if (fs.existsSync(MAC_APP_DIR)) {
    try { fs.rmSync(MAC_APP_DIR, { recursive: true, force: true }); } catch(e){}
}
copyRecursiveSync(APP_DIR, MAC_APP_DIR);

// 8. Package ZIP files with POSIX executable permissions
async function createZip(srcPath, outZip, isSingleDirectory = false) {
    if (fs.existsSync(outZip)) {
        try { fs.unlinkSync(outZip); } catch(e){}
    }
    const output = fs.createWriteStream(outZip);
    const archive = archiver('zip', { zlib: { level: 6 } });

    return new Promise((resolve, reject) => {
        output.on('close', () => {
            const sz = (archive.pointer() / 1024 / 1024).toFixed(2);
            console.log(`[MacApp] Successfully built ${path.basename(outZip)} (${sz} MB)`);
            resolve();
        });
        archive.on('warning', err => console.warn(err));
        archive.on('error', err => reject(err));
        archive.pipe(output);

        if (isSingleDirectory) {
            // Include 'Cosmic Client.app' as the top-level directory in the ZIP
            archive.directory(srcPath, path.basename(srcPath), (data) => {
                const rel = data.name.replace(/\\/g, '/');
                let isExec = false;
                if (rel.endsWith('.sh') || rel.endsWith('.command') || rel.endsWith('CosmicLauncher') || rel.endsWith('.dylib') || rel.endsWith('/java')) {
                    isExec = true;
                }
                if (rel.includes('bin-mac') || rel.includes('Contents/MacOS/')) {
                    isExec = true;
                }
                data.mode = (data.stats && data.stats.isDirectory()) ? 0o755 : (isExec ? 0o755 : 0o644);
                return data;
            });
        } else {
            // Package the full Mac/ folder contents
            archive.directory(srcPath, false, (data) => {
                const rel = data.name.replace(/\\/g, '/');
                let isExec = false;
                if (rel.endsWith('.sh') || rel.endsWith('.command') || rel.endsWith('CosmicLauncher') || rel.endsWith('.dylib') || rel.endsWith('/java')) {
                    isExec = true;
                }
                if (rel.includes('bin-mac') || rel.includes('Contents/MacOS/')) {
                    isExec = true;
                }
                data.mode = (data.stats && data.stats.isDirectory()) ? 0o755 : (isExec ? 0o755 : 0o644);
                return data;
            });
        }
        archive.finalize();
    });
}

async function main() {
    // A. Package Cosmic-Client.app.zip (Direct .app bundle for instant drag-and-drop to /Applications)
    console.log('\n[MacApp] Packaging Cosmic-Client.app.zip (Standalone Application Bundle)...');
    await createZip(APP_DIR, OUT_ZIP_APP_ONLY, true);

    // B. Package Cosmic-Client-Mac.zip (Complete folder with .app and quick launcher scripts)
    console.log('\n[MacApp] Packaging Cosmic-Client-Mac.zip (Complete Mac Suite)...');
    await createZip(MAC_DIR, OUT_ZIP_MAIN, false);

    // C. Mirror to Mac.zip
    try {
        fs.copyFileSync(OUT_ZIP_MAIN, OUT_ZIP_MIRROR);
        console.log(`[MacApp] Mirrored to ${path.basename(OUT_ZIP_MIRROR)}`);
    } catch(e) {
        await createZip(MAC_DIR, OUT_ZIP_MIRROR, false);
    }

    console.log('\n========================================================');
    console.log('=== [SUCCESS] STANDALONE COSMIC CLIENT.APP READY ===');
    console.log('=== 1. Cosmic-Client.app.zip (Pure Drag-and-Drop .app bundle) ===');
    console.log('=== 2. Cosmic-Client-Mac.zip (Complete Mac Suite) ===');
    console.log('========================================================');
}

main().catch(err => {
    console.error('[MacApp] Build Error:', err);
    process.exit(1);
});
