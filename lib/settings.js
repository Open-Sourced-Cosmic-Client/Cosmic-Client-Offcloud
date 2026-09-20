const path = require('path');
const fs = require('fs');

const DEFAULTS = {
    selectedVersion: '1.8.9',
    ramMB: 2048,
    maxRamMB: 4096,
    width: 1280,
    height: 720,
    fullscreen: false,
    customJavaPath: '',
    jvmPreset: 'cosmic_pro_fps',
    customJvmArgs: '',
    useAgent: true,
    hideAgent: true,
    autoCloseLauncher: false,
    serverDirectConnect: ''
};

function defaults() {
    return { ...DEFAULTS };
}

function getConfigPath(installDir) {
    return path.join(installDir, 'launcher-config.json');
}

function getLegacySettingsPath(installDir) {
    return path.join(installDir, 'launcher-settings.txt');
}

/**
 * Load settings merging launcher-config.json, legacy launcher-settings.txt, and defaults.
 */
function load(installDir) {
    const result = { ...DEFAULTS };
    if (!installDir) return result;
    
    const configPath = getConfigPath(installDir);
    const legacyPath = getLegacySettingsPath(installDir);

    // Read modern JSON config
    if (fs.existsSync(configPath)) {
        try {
            const raw = fs.readFileSync(configPath, 'utf-8');
            const data = JSON.parse(raw);
            Object.assign(result, data);
            return result;
        } catch (e) {
            console.warn('[Settings] Failed to parse launcher-config.json:', e.message);
        }
    }

    // Read legacy settings file if modern config not found
    if (fs.existsSync(legacyPath)) {
        try {
            const raw = fs.readFileSync(legacyPath, 'utf-8');
            for (const line of raw.split('\n')) {
                const trimmed = line.trim();
                if (!trimmed || !trimmed.includes(':')) continue;
                const idx = trimmed.indexOf(':');
                const key = trimmed.substring(0, idx).trim();
                const value = trimmed.substring(idx + 1).trim();

                if (key === 'ramMB') result.ramMB = parseInt(value) || DEFAULTS.ramMB;
                else if (key === 'maxRamMB') result.maxRamMB = parseInt(value) || DEFAULTS.maxRamMB;
                else if (key === 'width') result.width = parseInt(value) || DEFAULTS.width;
                else if (key === 'height') result.height = parseInt(value) || DEFAULTS.height;
            }
        } catch (e) {}
    }

    return result;
}

/**
 * Save settings to both launcher-config.json and legacy launcher-settings.txt.
 */
function save(installDir, newSettings) {
    const current = load(installDir);
    const updated = { ...current, ...newSettings };

    try {
        // Save modern JSON
        fs.writeFileSync(getConfigPath(installDir), JSON.stringify(updated, null, 2), 'utf-8');

        // Sync legacy launcher-settings.txt
        const legacyLines = [
            `ramMB:${updated.ramMB || DEFAULTS.ramMB}`,
            `width:${updated.width || DEFAULTS.width}`,
            `height:${updated.height || DEFAULTS.height}`
        ];
        fs.writeFileSync(getLegacySettingsPath(installDir), legacyLines.join('\n') + '\n', 'utf-8');

        return { success: true, settings: updated };
    } catch (err) {
        console.error('[Settings] Failed to save settings:', err.message);
        return { success: false, error: err.message };
    }
}

module.exports = {
    defaults,
    load,
    save,
    getSettings: load,
    get: load
};
