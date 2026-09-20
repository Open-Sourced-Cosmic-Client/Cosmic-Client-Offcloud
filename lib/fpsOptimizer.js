const fs = require('fs');
const path = require('path');

/**
 * Apply competitive Lunar / Badlion Client style FPS performance optimizations
 * to optionsof.txt (OptiFine) and optionscosmic.txt.
 */
function applyFpsBoost(installDir) {
    const cosmicDir = path.join(installDir, 'cosmic');
    if (!fs.existsSync(cosmicDir)) {
        fs.mkdirSync(cosmicDir, { recursive: true });
    }

    // 1. OptiFine Ultra FPS Configuration
    const ofSettingsPath = path.join(cosmicDir, 'optionsof.txt');
    const ofOptimizations = {
        ofFastRender: 'true',
        ofFastMath: 'true',
        ofChunkUpdates: '1',
        ofLazyChunkLoading: 'true',
        ofDynamicUpdates: 'false',
        ofSmoothFps: 'false',
        ofSmoothWorld: 'false',
        ofPreloadedChunks: '0',
        ofChunkUpdatesDynamic: 'false',
        ofMipmapType: '0',
        ofAaLevel: '0',
        ofAfLevel: '1',
        ofFogType: '3',           // 3 = OFF (huge FPS boost)
        ofFogStart: '0.8',
        ofBetterGrass: '3',       // 3 = OFF
        ofBetterSnow: 'false',
        ofCustomFonts: 'true',
        ofCustomColors: 'true',
        ofCustomSky: 'false',     // Custom Sky OFF for PvP stability
        ofShowCapes: 'true',
        ofConnectedTextures: '3', // 3 = OFF (eliminates multi-pass glass/block texture lag)
        ofNaturalTextures: 'false',
        ofVignette: '0',          // 0 = Fast/Off
        ofSmartAnimations: 'true',
        ofShowFps: 'true',
        ofAnimatedWater: '0',     // 0 = OFF
        ofAnimatedLava: '0',      // 0 = OFF
        ofAnimatedFire: 'false',
        ofAnimatedPortal: 'false',
        ofAnimatedRedstone: 'false',
        ofAnimatedExplosion: 'true',
        ofAnimatedFlame: 'false',
        ofAnimatedSmoke: 'false',
        ofVoidParticles: 'false',
        ofWaterParticles: 'false',
        ofRainSplash: 'false',
        ofPortalParticles: 'false',
        ofPotionParticles: 'true',
        ofDrippingWaterLava: 'false',
        ofAnimatedTerrain: 'false',
        ofAnimatedTextures: 'false'
    };

    let currentOfLines = [];
    if (fs.existsSync(ofSettingsPath)) {
        try {
            currentOfLines = fs.readFileSync(ofSettingsPath, 'utf-8').split(/\r?\n/).filter(Boolean);
        } catch (e) {}
    }

    const ofMap = {};
    for (const line of currentOfLines) {
        const idx = line.indexOf(':');
        if (idx !== -1) {
            const key = line.slice(0, idx).trim();
            const val = line.slice(idx + 1).trim();
            ofMap[key] = val;
        }
    }

    // Merge optimizations
    for (const [k, v] of Object.entries(ofOptimizations)) {
        ofMap[k] = v;
    }

    const newOfContent = Object.entries(ofMap)
        .map(([k, v]) => `${k}:${v}`)
        .join('\r\n');

    try {
        fs.writeFileSync(ofSettingsPath, newOfContent, 'utf-8');
        console.log('[FpsOptimizer] Applied Cosmic Pro OptiFine FPS boost settings.');
    } catch (e) {
        console.warn('[FpsOptimizer] Could not write optionsof.txt:', e.message);
    }

    // 2. Cosmic Client General Settings Optimization (optionscosmic.txt & options.txt)
    const targetPaths = [
        path.join(installDir, 'optionscosmic.txt'),
        path.join(cosmicDir, 'optionscosmic.txt'),
        path.join(installDir, 'options.txt'),
        path.join(cosmicDir, 'options.txt')
    ];

    const cosmicOptimizations = {
        'gamma': '100.0',
        'particles': '2',
        'advancedOpengl': '1',
        'fboEnable': 'true',
        'entityShadows': 'false',
        'anaglyph3d': 'false',
        'ambientOcclusion': '0',
        'ao': '0',
        'clouds': 'false',
        'renderClouds': 'false',
        'fancyGraphics': 'false',
        'enableVsync': 'false',
        'useVbo': 'true',
        'bobView': 'false',
        // In Minecraft 1.8.9, 260 is the exact internal engine value for 'Unlimited' FPS
        // (Options.FRAMERATE_LIMIT.getValueMax() == 260 disables Display.sync frame limiter)
        'maxFps': '260',
        'mipmapLevels': '0',
        'fullscreen': 'false'
    };

    for (const cosmicSettingsPath of targetPaths) {
        let currentCosmicLines = [];
        if (fs.existsSync(cosmicSettingsPath)) {
            try {
                currentCosmicLines = fs.readFileSync(cosmicSettingsPath, 'utf-8').split(/\r?\n/).filter(Boolean);
            } catch (e) {}
        }

        const cosmicMap = {};
        for (const line of currentCosmicLines) {
            const idx = line.indexOf(':');
            if (idx !== -1) {
                const key = line.slice(0, idx).trim();
                const val = line.slice(idx + 1).trim();
                cosmicMap[key] = val;
            }
        }

        if (cosmicMap['guiScale'] && (cosmicMap['guiScale'].includes('.') || isNaN(parseInt(cosmicMap['guiScale'])))) {
            cosmicMap['guiScale'] = '2';
        }

        for (const [k, v] of Object.entries(cosmicOptimizations)) {
            if (cosmicMap[k] === undefined || k === 'gamma' || k === 'entityShadows' || k === 'fullscreen' || k === 'maxFps' || k === 'enableVsync') {
                cosmicMap[k] = v;
            }
        }

        const newCosmicContent = Object.entries(cosmicMap)
            .map(([k, v]) => `${k}:${v}`)
            .join('\r\n');

        try {
            fs.writeFileSync(cosmicSettingsPath, newCosmicContent, 'utf-8');
            console.log(`[FpsOptimizer] Applied Cosmic Pro settings to ${path.basename(cosmicSettingsPath)} (Windowed, Fullbright, EntityShadows off).`);
        } catch (e) {
            console.warn(`[FpsOptimizer] Could not write ${cosmicSettingsPath}:`, e.message);
        }
    }
}

module.exports = {
    applyFpsBoost
};
