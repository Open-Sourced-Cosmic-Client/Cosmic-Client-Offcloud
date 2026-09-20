const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const iconSrc = path.join(ROOT, 'CosmicClient-x64', 'assets_18', 'objects', '99', '991b421dfd401f115241601b2b373140a8d78572');

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

for (const baseName of ['Cosmic Client.app', path.join('Mac', 'Cosmic Client.app')]) {
    const base = path.join(ROOT, baseName);
    const contents = path.join(base, 'Contents');
    const macos = path.join(contents, 'MacOS');
    const resources = path.join(contents, 'Resources');

    fs.mkdirSync(macos, { recursive: true });
    fs.mkdirSync(resources, { recursive: true });

    // 1. Copy high-res Apple ICNS icon
    if (fs.existsSync(iconSrc)) {
        fs.copyFileSync(iconSrc, path.join(resources, 'AppIcon.icns'));
        console.log('[SetupMacBundle] Installed AppIcon.icns ->', resources);
    }

    // 2. Write macOS PkgInfo
    fs.writeFileSync(path.join(contents, 'PkgInfo'), 'APPL????');

    // 3. Write macOS Info.plist
    fs.writeFileSync(path.join(contents, 'Info.plist'), plistContent);
}

console.log('[SetupMacBundle] Native macOS app bundle structures built successfully.');
