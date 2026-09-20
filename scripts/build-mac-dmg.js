const { execSync, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const ROOT = path.resolve(__dirname, '..');
const MAC_DIR = path.join(ROOT, 'Mac');
const DMG_SCRIPT = path.join(ROOT, 'scripts', 'create-mac-dmg.sh');

console.log('========================================================');
console.log('=== COSMIC CLIENT macOS DMG BUILDER ===');
console.log('========================================================');

// Ensure Mac folder exists
if (!fs.existsSync(MAC_DIR)) {
    console.log('[BuildDMG] Mac directory missing. Running split package generator...');
    execSync('node "' + path.join(ROOT, 'scripts', 'package-split-releases.js') + '"', { stdio: 'inherit' });
}

if (process.platform === 'darwin') {
    console.log('[BuildDMG] Detected macOS environment. Building native .dmg with hdiutil...');
    try {
        fs.chmodSync(DMG_SCRIPT, 0o755);
        execSync(`bash "${DMG_SCRIPT}"`, { stdio: 'inherit' });
    } catch (e) {
        console.error('[BuildDMG] Failed to run create-mac-dmg.sh:', e.message);
        process.exit(1);
    }
} else {
    console.log('[BuildDMG] Current operating system: ' + process.platform);
    console.log('[BuildDMG] Native Apple Disk Images (.dmg) require macOS hdiutil or Mac build runner.');
    console.log('[BuildDMG] Packaging high-compatibility macOS ZIP release with POSIX flags...');
    try {
        execSync('node "' + path.join(ROOT, 'scripts', 'zip-mac-edition.js') + '"', { stdio: 'inherit' });
        console.log('\n[BuildDMG] Complete! Both Cosmic-Client-Mac.zip and Mac/ folder are fully prepared.');
        console.log('[BuildDMG] To build the final .dmg on your MacBook, simply run:');
        console.log('           ./scripts/create-mac-dmg.sh');
    } catch (e) {
        console.error('[BuildDMG] Error during zip packaging:', e.message);
        process.exit(1);
    }
}
