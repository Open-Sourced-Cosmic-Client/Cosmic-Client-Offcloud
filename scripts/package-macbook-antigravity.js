/**
 * Cosmic Client Offcloud - Complete MacBook & Antigravity Suite Packager
 * Packages: Full Source Code + Standalone macOS App + Build Scripts + Assets
 */

const fs = require('fs');
const path = require('path');
const archiver = require('archiver');

const ROOT = path.join(__dirname, '..');
const OUT_ZIP = path.join(ROOT, 'Cosmic-Client-MacBook-Complete.zip');
const OUT_ZIP_SHORT = path.join(ROOT, 'Cosmic-Client-Mac-Source-Full.zip');

// Load essential asset hashes from 1.8.json to keep asset folder slim (<50 MB instead of 2.5 GB)
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

function shouldExcludeFromVault(rel, isDir = false) {
    const norm = rel.replace(/\\/g, '/');
    if (norm.startsWith('resourcepacks/') && norm !== 'resourcepacks') return true;
    if (norm.startsWith('schematics/') && norm !== 'schematics') return true;
    if (norm.startsWith('screenshots/') && norm !== 'screenshots') return true;
    if (norm.startsWith('shaderpacks/') && norm !== 'shaderpacks') return true;
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

async function packageMacbookFullZip() {
    console.log('========================================================');
    console.log('=== PACKAGING COMPLETE MACBOOK & ANTIGRAVITY SUITE ===');
    console.log('========================================================');

    if (fs.existsSync(OUT_ZIP)) {
        try { fs.unlinkSync(OUT_ZIP); } catch(e){}
    }

    const output = fs.createWriteStream(OUT_ZIP);
    const archive = archiver('zip', { zlib: { level: 6 } });

    const TOP_DIR = 'Cosmic-Client-MacBook';

    return new Promise((resolve, reject) => {
        output.on('close', () => {
            const sz = (archive.pointer() / 1024 / 1024).toFixed(2);
            console.log(`[Packager] Successfully built:`);
            console.log(`  -> ${path.basename(OUT_ZIP)} (${sz} MB)`);

            // Also mirror to OUT_ZIP_SHORT
            try {
                fs.copyFileSync(OUT_ZIP, OUT_ZIP_SHORT);
                console.log(`  -> ${path.basename(OUT_ZIP_SHORT)} (${sz} MB)`);
            } catch(e){}

            resolve();
        });

        archive.on('warning', err => console.warn(err));
        archive.on('error', err => reject(err));
        archive.pipe(output);

        // 1. Add Cosmic Client.app
        const appPath = path.join(ROOT, 'Cosmic Client.app');
        if (fs.existsSync(appPath)) {
            console.log('[Packager] Adding Cosmic Client.app...');
            archive.directory(appPath, `${TOP_DIR}/Cosmic Client.app`, (entry) => {
                const rel = entry.name.replace(/\\/g, '/');
                let isExec = false;
                if (rel.endsWith('.sh') || rel.endsWith('.command') || rel.endsWith('CosmicLauncher') || rel.endsWith('.dylib') || rel.endsWith('/java')) {
                    isExec = true;
                }
                if (rel.includes('bin-mac') || rel.includes('Contents/MacOS/')) {
                    isExec = true;
                }
                entry.mode = (entry.stats && entry.stats.isDirectory()) ? 0o755 : (isExec ? 0o755 : 0o644);
                return entry;
            });
        }

        // 2. Add cosmic-agent-src
        const agentSrcPath = path.join(ROOT, 'cosmic-agent-src');
        if (fs.existsSync(agentSrcPath)) {
            console.log('[Packager] Adding cosmic-agent-src (Java Agent source & build tools)...');
            archive.directory(agentSrcPath, `${TOP_DIR}/cosmic-agent-src`, (entry) => {
                const rel = entry.name.replace(/\\/g, '/');
                if (rel.includes('/target/classes/')) return false; // Exclude compiled duplicate classes to save space
                if (rel.endsWith('.sh') || rel.endsWith('.bat')) {
                    entry.mode = 0o755;
                } else {
                    entry.mode = (entry.stats && entry.stats.isDirectory()) ? 0o755 : 0o644;
                }
                return entry;
            });
        }

        // 3. Add src (Electron & UI frontend)
        const srcPath = path.join(ROOT, 'src');
        if (fs.existsSync(srcPath)) {
            console.log('[Packager] Adding src (Launcher frontend UI & styles)...');
            archive.directory(srcPath, `${TOP_DIR}/src`);
        }

        // 4. Add scripts
        const scriptsPath = path.join(ROOT, 'scripts');
        if (fs.existsSync(scriptsPath)) {
            console.log('[Packager] Adding scripts...');
            archive.directory(scriptsPath, `${TOP_DIR}/scripts`, (entry) => {
                if (entry.name.endsWith('.sh') || entry.name.endsWith('.command')) {
                    entry.mode = 0o755;
                }
                return entry;
            });
        }

        // 5. Add reverse-engineering & offline-archive
        for (const extra of ['reverse-engineering', 'offline-archive']) {
            const p = path.join(ROOT, extra);
            if (fs.existsSync(p)) {
                console.log(`[Packager] Adding ${extra}...`);
                archive.directory(p, `${TOP_DIR}/${extra}`);
            }
        }

        // 6. Add CosmicClient-x64 (Pre-filtered, optimized game vault with Mac natives & essential assets)
        const vaultPath = path.join(ROOT, 'Mac', 'CosmicClient-x64');
        if (fs.existsSync(vaultPath)) {
            console.log('[Packager] Adding CosmicClient-x64 (Game vault & Mac natives)...');
            archive.directory(vaultPath, `${TOP_DIR}/CosmicClient-x64`, (entry) => {
                let isExec = false;
                const rel = entry.name.replace(/\\/g, '/');
                if (rel.endsWith('.sh') || rel.endsWith('.command') || rel.endsWith('.dylib') || rel.endsWith('/java')) {
                    isExec = true;
                }
                if (rel.includes('bin-mac')) isExec = true;
                entry.mode = (entry.stats && entry.stats.isDirectory()) ? 0o755 : (isExec ? 0o755 : 0o644);
                return entry;
            });
        }

        // 7. Add root files and scripts
        const rootItems = [
            'START-HERE-MAC.command',
            'Fix-Mac-Gatekeeper.command',
            'Launch-Cosmic-Direct.command',
            'Launch-Cosmic-Direct.sh',
            'Launch-GUI.command',
            'Launch-GUI.sh',
            'Install-To-Applications.command',
            'main.js',
            'preload.js',
            'package.json',
            'package-lock.json',
            'accounts.json',
            'cosmic-agent.jar',
            'README.md',
            'ANTIGRAVITY.md',
            'walkthrough.md',
            'LICENSE',
            'cosmic-icon.png',
            'cosmic.ico',
            'logo.png'
        ];

        console.log('[Packager] Adding root launchers & configuration files...');
        for (const item of rootItems) {
            const fullPath = path.join(ROOT, item);
            if (fs.existsSync(fullPath)) {
                const isExec = item.endsWith('.command') || item.endsWith('.sh');
                archive.file(fullPath, {
                    name: `${TOP_DIR}/${item}`,
                    mode: isExec ? 0o755 : 0o644
                });
            }
        }

        console.log('[Packager] Finalizing archive...');
        archive.finalize();
    });
}

packageMacbookFullZip().catch(err => {
    console.error('[Packager] Error:', err);
    process.exit(1);
});
