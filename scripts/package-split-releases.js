const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SOURCE_OUT = path.join(ROOT, 'Source Code');
const WIN64_OUT = path.join(ROOT, 'Windows-x64');
const WIN32_OUT = path.join(ROOT, 'Windows-x32');
const MAC_OUT = path.join(ROOT, 'Mac');
const WIN_OUT = path.join(ROOT, 'Windows');

// Load 1.8.json to filter essential gameplay assets and skip bloat
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
    console.log(`[Packager] Indexed ${validAssetHashes.size} essential 1.8.9 gameplay assets.`);
} catch (e) {
    console.warn('[Packager] Could not index 1.8.json:', e.message);
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
            if (fs.existsSync(dest)) {
                try {
                    const destStat = fs.statSync(dest);
                    if (destStat.size === stat.size && destStat.mtimeMs >= stat.mtimeMs) {
                        return; // Already up to date
                    }
                } catch(e) {}
            }
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

function isPersonalData(rel, isDir = false) {
    const norm = rel.replace(/\\/g, '/');
    if (norm.startsWith('resourcepacks/') && norm !== 'resourcepacks') return true;
    if (norm.startsWith('schematics/') && norm !== 'schematics') return true;
    if (norm.startsWith('screenshots/') && norm !== 'screenshots') return true;
    if (norm.startsWith('shaderpacks/') && norm !== 'shaderpacks') return true;
    if (norm.startsWith('cosmic/screenshots/') && norm !== 'cosmic/screenshots') return true;
    if (norm.startsWith('cosmic/schematics/') && norm !== 'cosmic/schematics') return true;
    if (norm.startsWith('scratch') || norm.includes('/scratch')) return true;
    if (norm.startsWith('assets_18/skins') || norm.includes('/assets_18/skins')) return true;
    if (norm.startsWith('logs/') || norm === 'logs') return true;
    if (norm.startsWith('crash-reports/') || norm === 'crash-reports') return true;
    if (norm.startsWith('saves/') || norm === 'saves') return true;
    if (norm.startsWith('stats/') || norm === 'stats') return true;
    if (norm === 'servers.dat' || norm.startsWith('servers.dat')) return true;
    if (norm.startsWith('1.12') || norm.startsWith('assets_112')) return true;
    if (norm.endsWith('.DS_Store') || norm.includes('/._') || norm.startsWith('._')) return true;

    // Filter unindexed objects in assets_18/objects
    if (norm.startsWith('assets_18/objects/')) {
        const parts = norm.split('/');
        if (!isDir && parts.length >= 4) {
            const hash = parts[parts.length - 1].toLowerCase();
            if (validAssetHashes.size > 0 && !validAssetHashes.has(hash)) {
                return true; // Exclude bloat object!
            }
        }
    }
    return false;
}

function ensureEmptyPlaceholders(baseDir) {
    const placeholders = ['resourcepacks', 'schematics', 'screenshots', 'shaderpacks'];
    for (const p of placeholders) {
        const full = path.join(baseDir, 'CosmicClient-x64', p);
        if (!fs.existsSync(full)) fs.mkdirSync(full, { recursive: true });
    }
}

console.log('========================================================');
console.log('=== PACKAGING PURE 1.8.9 COSMIC CLIENT OFFCLOUD EDITIONS ===');
console.log('========================================================');

const commonFiles = [
    'lib',
    'src',
    'scripts',
    'main.js',
    'preload.js',
    'package.json',
    'accounts.json',
    'cosmic-agent.jar',
    'cosmic.ico',
    'offline-archive',
    'LICENSE'
];

// 1. Source Code Distribution
console.log('\n[1/4] Packaging Source Code folder...');
fs.mkdirSync(SOURCE_OUT, { recursive: true });
const sourceItems = [
    ...commonFiles,
    'cosmic-agent-src',
    'reverse-engineering',
    'package-lock.json',
    'START-HERE-WINDOWS.bat',
    'CosmicClient.exe',
    'CosmicClient-Standalone.exe',
    'CosmicClientOffcloud.exe',
    'CosmicClientLauncher.exe',
    'CosmicClient-Direct.exe',
    'Launch-GUI.bat',
    'Launch-GUI.sh',
    'Launch-Cosmic-Direct.bat',
    'Launch-Cosmic-Direct-x32.bat',
    'Launch-Cosmic-Direct.sh',
    'Fix-Mac-Gatekeeper.command',
    'Launch-GUI.command',
    'Launch-Cosmic-Direct.command'
];
for (const item of sourceItems) {
    const src = path.join(ROOT, item);
    if (fs.existsSync(src)) copyRecursiveSync(src, path.join(SOURCE_OUT, item));
}
fs.writeFileSync(path.join(SOURCE_OUT, 'README.md'), `# Cosmic Client Offcloud - Pure 1.8.9 Source Code\n\nFull source code, bytecode transformers, and standalone launchers for Cosmic Client 1.8.9.\n`);
console.log(' -> Source Code packaged.');

// 2. Windows 64-bit Edition
console.log('\n[2/4] Packaging Windows 64-bit edition (Windows-x64 & Windows)...');
for (const target of [WIN64_OUT, WIN_OUT]) {
    fs.mkdirSync(target, { recursive: true });
    for (const item of commonFiles) {
        copyRecursiveSync(path.join(ROOT, item), path.join(target, item));
    }
    const winSpecific = [
        'START-HERE-WINDOWS.bat',
        'CosmicClient.exe',
        'CosmicClient-Standalone.exe',
        'CosmicClientOffcloud.exe',
        'CosmicClientLauncher.exe',
        'CosmicClient-Direct.exe',
        'Launch-GUI.bat',
        'Launch-Cosmic-Direct.bat'
    ];
    for (const f of winSpecific) {
        const src = path.join(ROOT, f);
        if (fs.existsSync(src)) fs.copyFileSync(src, path.join(target, f));
    }
    copyRecursiveSync(
        path.join(ROOT, 'CosmicClient-x64'),
        path.join(target, 'CosmicClient-x64'),
        (filePath, isDir) => {
            const rel = path.relative(path.join(ROOT, 'CosmicClient-x64'), filePath).replace(/\\/g, '/');
            if (isPersonalData(rel, isDir)) return false;
            if (rel.startsWith('1.8/bin-mac') || rel.startsWith('1.8/bin-linux')) return false;
            if (rel === 'Launch-Offline.sh') return false;
            return true;
        }
    );
    ensureEmptyPlaceholders(target);
    const win64Readme = `Cosmic Client Offcloud - Windows 64-bit Pure 1.8.9\n=================================================\n1. Double-click CosmicClientLauncher.exe or START-HERE-WINDOWS.bat for auto-setup & launch.\n2. Double-click CosmicClient-Direct.exe or Launch-Cosmic-Direct.bat for instant 1-click game launch.\n3. Complete 100% offcloud / offline support with official Cosmic icon & bytecode agent.\n\nNOTE: If downloaded as a ZIP file, please make sure to Right-Click -> 'Extract All...' before launching!\n`;
    fs.writeFileSync(path.join(target, 'README.txt'), win64Readme);
    fs.writeFileSync(path.join(target, 'README.md'), `# Cosmic Client Offcloud - Windows 64-bit Pure 1.8.9\n\n${win64Readme}`);

    if (fs.existsSync(path.join(ROOT, 'dist', 'win-unpacked'))) {
        copyRecursiveSync(
            path.join(ROOT, 'dist', 'win-unpacked'),
            path.join(target, 'dist', 'win-unpacked'),
            (p) => !p.replace(/\\/g, '/').endsWith('/resources/CosmicClient-x64')
        );
    }
}
console.log(' -> Windows 64-bit releases packaged.');

// 3. Windows 32-bit Edition
console.log('\n[3/4] Packaging Windows 32-bit edition (Windows-x32)...');
fs.mkdirSync(WIN32_OUT, { recursive: true });
for (const item of commonFiles) {
    copyRecursiveSync(path.join(ROOT, item), path.join(WIN32_OUT, item));
}
const win32Specific = [
    'START-HERE-WINDOWS.bat',
    'CosmicClientOffcloud.exe',
    'CosmicClientLauncher.exe',
    'CosmicClient-Direct.exe',
    'Launch-GUI.bat'
];
for (const f of win32Specific) {
    const src = path.join(ROOT, f);
    if (fs.existsSync(src)) fs.copyFileSync(src, path.join(WIN32_OUT, f));
}
const direct32Src = path.join(ROOT, 'Launch-Cosmic-Direct-x32.bat');
if (fs.existsSync(direct32Src)) {
    fs.copyFileSync(direct32Src, path.join(WIN32_OUT, 'Launch-Cosmic-Direct.bat'));
}
copyRecursiveSync(
    path.join(ROOT, 'CosmicClient-x64'),
    path.join(WIN32_OUT, 'CosmicClient-x64'),
    (filePath, isDir) => {
        const rel = path.relative(path.join(ROOT, 'CosmicClient-x64'), filePath).replace(/\\/g, '/');
        if (isPersonalData(rel, isDir)) return false;
        if (rel.startsWith('bootstrap/java') && !rel.startsWith('bootstrap/java-x32')) return false;
        if (rel.startsWith('1.8/bin-mac') || rel.startsWith('1.8/bin-linux')) return false;
        if (rel === 'Launch-Offline.sh') return false;
        return true;
    }
);
ensureEmptyPlaceholders(WIN32_OUT);
const win32Readme = `Cosmic Client Offcloud - Windows 32-bit Pure 1.8.9\n=================================================\n1. Double-click Launch-Cosmic-Direct.bat for instant 32-bit game launch.\n2. Double-click Launch-GUI.bat for GUI Launcher.\n`;
fs.writeFileSync(path.join(WIN32_OUT, 'README.txt'), win32Readme);
fs.writeFileSync(path.join(WIN32_OUT, 'README.md'), `# Cosmic Client Offcloud - Windows 32-bit Pure 1.8.9\n\n${win32Readme}`);
console.log(' -> Windows 32-bit release packaged.');

// 4. macOS Edition
console.log('\n[4/4] Packaging macOS edition (Mac)...');
fs.mkdirSync(MAC_OUT, { recursive: true });
for (const item of commonFiles) {
    copyRecursiveSync(path.join(ROOT, item), path.join(MAC_OUT, item));
}
const macSpecific = [
    'START-HERE-MAC.command',
    'Fix-Mac-Gatekeeper.command',
    'Install-To-Applications.command',
    'Launch-GUI.sh',
    'Launch-Cosmic-Direct.sh',
    'Launch-GUI.command',
    'Launch-Cosmic-Direct.command'
];
for (const f of macSpecific) {
    const src = path.join(ROOT, f);
    if (fs.existsSync(src)) {
        fs.copyFileSync(src, path.join(MAC_OUT, f));
    }
}

// Copy Cosmic Client.app bundle
if (fs.existsSync(path.join(ROOT, 'Cosmic Client.app'))) {
    copyRecursiveSync(path.join(ROOT, 'Cosmic Client.app'), path.join(MAC_OUT, 'Cosmic Client.app'));
}

const macCosmic = path.join(MAC_OUT, 'CosmicClient-x64');
if (fs.existsSync(macCosmic)) {
    try { fs.rmSync(macCosmic, { recursive: true, force: true }); } catch(e){}
}

copyRecursiveSync(
    path.join(ROOT, 'CosmicClient-x64'),
    path.join(MAC_OUT, 'CosmicClient-x64'),
    (filePath, isDir) => {
        const rel = path.relative(path.join(ROOT, 'CosmicClient-x64'), filePath).replace(/\\/g, '/');
        if (isPersonalData(rel, isDir)) return false;
        if (rel.startsWith('bootstrap/java') && !rel.startsWith('bootstrap/java-mac')) return false;
        if (rel.startsWith('1.8/bin-1.8') || rel.startsWith('1.8/bin-linux')) return false;
        if (rel === 'Launch-Offline.bat') return false;
        return true;
    }
);
ensureEmptyPlaceholders(MAC_OUT);

const macReadme = `# Cosmic Client Offcloud - macOS (MacBook) Edition

Compatible with Apple Silicon (M1 / M2 / M3 / M4 / M5) and Intel MacBooks.

---

### How to Bypass macOS Gatekeeper / Quarantine & AV Warnings

When applications are downloaded from the internet, macOS automatically marks them with a quarantine flag (\`com.apple.quarantine\`). Because this is a free open-source client distributed outside Apple's $99/year App Store developer program, macOS will show a security dialog on first run.

Use **ANY ONE** of these quick methods to unlock and run the client:

#### Method 1: Right-Click -> Open (Quickest & Easiest, No Terminal Required)
1. **Right-click** (or hold **Control** and click) **\`START-HERE-MAC.command\`** or **\`Cosmic Client.app\`**.
2. Click **Open** from the context menu.
3. In the popup dialog, click **Open** (or **Open Anyway**).
4. *macOS will permanently remember your approval and you can double-click normally anytime afterwards!*

#### Method 2: Double-click \`START-HERE-MAC.command\` or \`Fix-Mac-Gatekeeper.command\`
1. Double-click or Right-click -> Open **\`START-HERE-MAC.command\`**.
2. The script will automatically:
   - Strip all \`com.apple.quarantine\` extended attributes
   - Apply local ad-hoc code signing (\`codesign --force --deep -s -\`) so macOS never flags it as damaged
   - Set executable permissions on Java and native LWJGL libraries
   - Launch the game immediately!

#### Method 3: 1-Click Install to Applications
Double-click **\`Install-To-Applications.command\`** to install Cosmic Client directly into your macOS \`/Applications\` folder with all quarantine flags automatically removed.

#### Method 4: 1-Line Terminal Fix
Open **Terminal**, \`cd\` into this folder, and run:
\`\`\`bash
xattr -cr . && xattr -dr com.apple.quarantine . && codesign --force --deep -s - "Cosmic Client.app" && chmod -R +x *.command *.sh "Cosmic Client.app"
\`\`\`

#### Method 5: macOS System Settings
1. Open **System Settings** > **Privacy & Security**.
2. Scroll down to **Security**.
3. Next to the blocked message, click **"Open Anyway"**.

---

### How to Launch on MacBook
1. **Option 1 (Auto-Unlock & Launch)**: Double-click **\`START-HERE-MAC.command\`**
2. **Option 2 (Direct Game Launch)**: Double-click **\`Launch-Cosmic-Direct.command\`**
3. **Option 3 (Native App Bundle)**: Double-click **\`Cosmic Client.app\`**
4. **Option 4 (GUI Launcher)**: Double-click **\`Launch-GUI.command\`**

---

### Building a .dmg Disk Image
If you want to package this folder into a standalone \`.dmg\` file on macOS:
\`\`\`bash
./scripts/create-mac-dmg.sh
\`\`\`
`;

fs.writeFileSync(path.join(MAC_OUT, 'README.txt'), macReadme);
fs.writeFileSync(path.join(MAC_OUT, 'README.md'), macReadme);
console.log(' -> macOS release packaged.');

console.log('\n========================================================');
console.log('=== PLATFORM PACKAGING COMPLETE (PURE 1.8.9) ===');
console.log('========================================================');
