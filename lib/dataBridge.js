const fs = require('fs');
const path = require('path');
const os = require('os');

/**
 * Get standard system Minecraft directory across Windows, macOS, and Linux.
 */
function getSystemMinecraftDir() {
    if (process.platform === 'win32') {
        return path.join(process.env.APPDATA || '', '.minecraft');
    } else if (process.platform === 'darwin') {
        return path.join(os.homedir(), 'Library', 'Application Support', 'minecraft');
    } else {
        return path.join(os.homedir(), '.minecraft');
    }
}

/**
 * Get system Cosmic Client directory in AppData / Application Support.
 */
function getSystemCosmicDir() {
    const mcDir = getSystemMinecraftDir();
    return path.join(mcDir, 'cosmic');
}

/**
 * Discovers additional Minecraft launcher directories on the user's PC
 * (e.g. Lunar Client, Feather, Badlion, Prism, CurseForge).
 */
function getOtherClientPackDirs() {
    const dirs = [];
    const userProfile = os.homedir();
    const appData = process.env.APPDATA || '';

    const candidates = [
        path.join(userProfile, '.lunarclient', 'offline', 'multiver', 'resourcepacks'),
        path.join(appData, '.feather', 'player-data', 'resourcepacks'),
        path.join(appData, '.badlionclient', 'resourcepacks'),
        path.join(appData, 'PrismLauncher', 'instances'),
        path.join(appData, 'curseforge', 'minecraft', 'Instances')
    ];

    for (const c of candidates) {
        try {
            if (fs.existsSync(c)) {
                dirs.push(c);
            }
        } catch (e) {}
    }
    return dirs;
}

/**
 * Safely create a directory junction (Windows) or symlink (macOS/Linux)
 * from targetDir to linkPath so both directories share identical files.
 * If linkPath exists and is empty, removes it and creates the junction.
 * If linkPath contains existing files, merges individual packs instead.
 */
function bridgeFolder(targetDir, linkPath) {
    try {
        if (!fs.existsSync(targetDir)) {
            return false;
        }

        const symlinkType = process.platform === 'win32' ? 'junction' : 'dir';

        // Check if linkPath already exists
        if (fs.existsSync(linkPath)) {
            try {
                const stat = fs.lstatSync(linkPath);
                if (stat.isSymbolicLink()) {
                    return true; // Already a junction/symlink
                }
            } catch (e) {}

            // If it is an empty directory, replace it with the junction
            try {
                const files = fs.readdirSync(linkPath);
                if (files.length === 0) {
                    fs.rmdirSync(linkPath);
                    fs.symlinkSync(targetDir, linkPath, symlinkType);
                    return true;
                }
            } catch (e) {}

            // Directory has existing files: merge items from targetDir into linkPath
            try {
                const targetEntries = fs.readdirSync(targetDir, { withFileTypes: true });
                for (const entry of targetEntries) {
                    const srcItem = path.join(targetDir, entry.name);
                    const dstItem = path.join(linkPath, entry.name);
                    if (!fs.existsSync(dstItem)) {
                        try {
                            if (entry.isDirectory()) {
                                fs.symlinkSync(srcItem, dstItem, symlinkType);
                            } else {
                                try {
                                    fs.linkSync(srcItem, dstItem); // Hard link
                                } catch (err) {
                                    fs.copyFileSync(srcItem, dstItem);
                                }
                            }
                        } catch (itemErr) {}
                    }
                }
                return true;
            } catch (e) {
                return false;
            }
        }

        // Link path does not exist: create junction directly
        fs.mkdirSync(path.dirname(linkPath), { recursive: true });
        fs.symlinkSync(targetDir, linkPath, symlinkType);
        return true;
    } catch (err) {
        console.warn(`[DataBridge] Warning bridging folder ${targetDir} -> ${linkPath}:`, err.message);
        return false;
    }
}

/**
 * Bridge player's PC texture packs from .minecraft and other clients into Cosmic Client.
 */
function bridgeResourcePacks(installDir) {
    const mcDir = getSystemMinecraftDir();
    const systemRP = path.join(mcDir, 'resourcepacks');
    const localRP = path.join(installDir, 'resourcepacks');

    let bridged = false;
    if (fs.existsSync(systemRP)) {
        bridged = bridgeFolder(systemRP, localRP);
    }

    // Also scan other installed clients (e.g. Lunar, Feather) for additional packs
    const otherDirs = getOtherClientPackDirs();
    for (const oDir of otherDirs) {
        try {
            if (fs.existsSync(oDir) && fs.existsSync(localRP)) {
                const entries = fs.readdirSync(oDir);
                for (const item of entries) {
                    const src = path.join(oDir, item);
                    const dst = path.join(localRP, item);
                    if (!fs.existsSync(dst)) {
                        try {
                            const isDir = fs.statSync(src).isDirectory();
                            if (isDir) {
                                fs.symlinkSync(src, dst, process.platform === 'win32' ? 'junction' : 'dir');
                            } else {
                                try { fs.linkSync(src, dst); } catch (e) { fs.copyFileSync(src, dst); }
                            }
                        } catch (ignored) {}
                    }
                }
            }
        } catch (e) {}
    }

    let count = 0;
    let packNames = [];
    if (fs.existsSync(localRP)) {
        try {
            packNames = fs.readdirSync(localRP);
            count = packNames.length;
        } catch (e) {}
    }

    return { bridged, count, packs: packNames, path: localRP };
}

/**
 * Import missing game assets (sounds, ambient music, jukebox records, translations)
 * from the player's PC (.minecraft/assets) into Cosmic Client's asset directory.
 */
function importGameAssets(installDir) {
    const mcDir = getSystemMinecraftDir();
    const mcAssets = path.join(mcDir, 'assets');
    const targetAssets = path.join(installDir, 'assets_18');
    const targetObjects = path.join(targetAssets, 'objects');

    let imported = 0;
    let total = 0;

    if (!fs.existsSync(path.join(mcAssets, 'objects'))) {
        return { imported: 0, total: 0, hasAssets: false };
    }

    try {
        fs.mkdirSync(targetObjects, { recursive: true });

        const prefixes = fs.readdirSync(path.join(mcAssets, 'objects'));
        for (const prefix of prefixes) {
            const mcPrefixDir = path.join(mcAssets, 'objects', prefix);
            try {
                if (!fs.statSync(mcPrefixDir).isDirectory()) continue;
                const objects = fs.readdirSync(mcPrefixDir);
                total += objects.length;

                const targetPrefixDir = path.join(targetObjects, prefix);

                for (const obj of objects) {
                    const srcObj = path.join(mcPrefixDir, obj);
                    const dstObj = path.join(targetPrefixDir, obj);

                    if (!fs.existsSync(dstObj)) {
                        if (!fs.existsSync(targetPrefixDir)) {
                            fs.mkdirSync(targetPrefixDir, { recursive: true });
                        }
                        try {
                            // Hard link takes 0 bytes extra disk space and 0 network traffic
                            fs.linkSync(srcObj, dstObj);
                            imported++;
                        } catch (linkErr) {
                            try {
                                fs.copyFileSync(srcObj, dstObj);
                                imported++;
                            } catch (copyErr) {}
                        }
                    }
                }
            } catch (pErr) {}
        }
    } catch (err) {
        console.warn('[DataBridge] Asset import warning:', err.message);
    }

    return { imported, total, hasAssets: true };
}

/**
 * Import player's options, keybinds, controls, FOV, and sensitivity from .minecraft/options.txt.
 */
function importPlayerOptions(installDir, force = false) {
    const mcDir = getSystemMinecraftDir();
    const mcOptions = path.join(mcDir, 'options.txt');
    const cosmicOptions = path.join(installDir, 'options.txt');
    const cosmicSubOptions = path.join(installDir, 'cosmic', 'options.txt');

    if (!fs.existsSync(mcOptions)) {
        return false;
    }

    let imported = false;
    try {
        // If cosmic options does not exist, or force is requested
        if (!fs.existsSync(cosmicOptions) || force) {
            fs.copyFileSync(mcOptions, cosmicOptions);
            imported = true;
        }

        // Also copy into nested cosmic/ folder if needed
        if (fs.existsSync(path.dirname(cosmicSubOptions)) && (!fs.existsSync(cosmicSubOptions) || force)) {
            fs.copyFileSync(mcOptions, cosmicSubOptions);
            imported = true;
        }
    } catch (e) {
        console.warn('[DataBridge] Options import warning:', e.message);
    }

    return imported;
}

/**
 * Import multiplayer server bookmarks from .minecraft/servers.dat.
 */
function importServers(installDir, force = false) {
    const mcDir = getSystemMinecraftDir();
    const sysServers = path.join(mcDir, 'servers.dat');
    const locServers = path.join(installDir, 'servers.dat');

    if (fs.existsSync(sysServers) && (!fs.existsSync(locServers) || force)) {
        try {
            fs.copyFileSync(sysServers, locServers);
            return true;
        } catch (e) {
            return false;
        }
    }
    return false;
}

/**
 * Bridge shader packs from .minecraft/shaderpacks into Cosmic Client.
 */
function bridgeShaderPacks(installDir) {
    const mcDir = getSystemMinecraftDir();
    const sysShaders = path.join(mcDir, 'shaderpacks');
    const locShaders = path.join(installDir, 'shaderpacks');

    if (fs.existsSync(sysShaders)) {
        return bridgeFolder(sysShaders, locShaders);
    }
    return false;
}

/**
 * Get comprehensive overview of PC resources available for import.
 */
function getPCResourcesSummary(installDir) {
    const mcDir = getSystemMinecraftDir();
    const hasMinecraft = fs.existsSync(mcDir);

    const rpDir = path.join(mcDir, 'resourcepacks');
    let rpCount = 0;
    let rpList = [];
    if (fs.existsSync(rpDir)) {
        try {
            rpList = fs.readdirSync(rpDir);
            rpCount = rpList.length;
        } catch (e) {}
    }

    const localRP = path.join(installDir, 'resourcepacks');
    let localRPCount = 0;
    let isBridged = false;
    if (fs.existsSync(localRP)) {
        try {
            localRPCount = fs.readdirSync(localRP).length;
            const stat = fs.lstatSync(localRP);
            isBridged = stat.isSymbolicLink();
        } catch (e) {}
    }

    const shadersDir = path.join(mcDir, 'shaderpacks');
    let shadersCount = 0;
    if (fs.existsSync(shadersDir)) {
        try {
            shadersCount = fs.readdirSync(shadersDir).length;
        } catch (e) {}
    }

    const assetsDir = path.join(mcDir, 'assets', 'objects');
    const hasAssets = fs.existsSync(assetsDir);

    const hasOptions = fs.existsSync(path.join(mcDir, 'options.txt'));
    const hasServers = fs.existsSync(path.join(mcDir, 'servers.dat'));

    return {
        hasMinecraft,
        mcDir,
        resourcePacksCount: rpCount,
        resourcePacks: rpList,
        localResourcePacksCount: localRPCount,
        isResourcePacksBridged: isBridged,
        shaderPacksCount: shadersCount,
        hasAssets,
        hasOptions,
        hasServers,
        resourcePacksPath: localRP
    };
}

/**
 * Recursively copy a directory tree from src to dst.
 * If force is false, only files that don't already exist in dst are copied.
 */
function copyDirRecursive(src, dst, force = false) {
    if (!fs.existsSync(src)) return 0;
    fs.mkdirSync(dst, { recursive: true });
    let count = 0;
    try {
        const entries = fs.readdirSync(src, { withFileTypes: true });
        for (const entry of entries) {
            const srcPath = path.join(src, entry.name);
            const dstPath = path.join(dst, entry.name);
            if (entry.isDirectory()) {
                count += copyDirRecursive(srcPath, dstPath, force);
            } else if (entry.isFile()) {
                if (!fs.existsSync(dstPath) || force) {
                    try {
                        fs.copyFileSync(srcPath, dstPath);
                        count++;
                    } catch (e) {}
                }
            }
        }
    } catch (e) {}
    return count;
}

/**
 * Deep bidirectional synchronization of Cosmic Client configs, profiles (HUD layouts like default/hud.json),
 * waypoints, factions, schematics, keybinds, servers, and TabbyChat settings.
 * If running on a fresh build or clean PC, automatically seeds authentic old Cosmic Client configurations.
 */
function syncOldCosmicConfigs(installDir, force = false) {
    const systemCosmic = getSystemCosmicDir();
    const mcDir = getSystemMinecraftDir();
    let syncedItems = 0;

    // Target destinations: both installDir and installDir/cosmic
    const targets = [installDir, path.join(installDir, 'cosmic')];
    for (const t of targets) {
        fs.mkdirSync(t, { recursive: true });
    }

    // Bundled fallback sources for fresh builds
    const bundledSources = [
        path.join(__dirname, '..', 'cosmic'),
        path.join(__dirname, '..', 'CosmicClient-x64'),
        path.join(installDir, 'cosmic')
    ].filter(s => fs.existsSync(s));

    // 1. Schematics: Deep bidirectional synchronization across all schematic locations
    const schemDirs = [
        path.join(mcDir, 'schematics'),
        path.join(systemCosmic, 'schematics'),
        path.join(installDir, 'schematics'),
        path.join(installDir, 'cosmic', 'schematics'),
        path.join(__dirname, '..', 'CosmicClient-x64', 'schematics')
    ];

    // Ensure directories exist
    for (const sd of schemDirs) {
        try { fs.mkdirSync(sd, { recursive: true }); } catch (e) {}
    }

    // Try creating junctions between main schematics folders if possible
    bridgeFolder(path.join(mcDir, 'schematics'), path.join(installDir, 'schematics'));
    bridgeFolder(path.join(systemCosmic, 'schematics'), path.join(installDir, 'cosmic', 'schematics'));

    // Gather all .schematic files across all sources
    const allSchematics = new Map(); // fileName -> sourceFilePath
    for (const sDir of schemDirs) {
        if (fs.existsSync(sDir)) {
            try {
                const files = fs.readdirSync(sDir);
                for (const f of files) {
                    if (f.toLowerCase().endsWith('.schematic') && !allSchematics.has(f)) {
                        allSchematics.set(f, path.join(sDir, f));
                    }
                }
            } catch (e) {}
        }
    }

    // Distribute every schematic to all target directories
    for (const [sName, sPath] of allSchematics.entries()) {
        for (const sDir of schemDirs) {
            const destFile = path.join(sDir, sName);
            if (!fs.existsSync(destFile) || force) {
                try {
                    fs.linkSync(sPath, destFile);
                    syncedItems++;
                } catch (err) {
                    try {
                        fs.copyFileSync(sPath, destFile);
                        syncedItems++;
                    } catch (e) {}
                }
            }
        }
    }

    // 2. Fresh Build Detection: If system Cosmic directory is missing or lacks profiles,
    // automatically seed authentic old configs from bundled templates into systemCosmic & targets!
    const sysProfiles = path.join(systemCosmic, 'profiles');
    const isFreshBuild = !fs.existsSync(systemCosmic) || !fs.existsSync(sysProfiles) || fs.readdirSync(sysProfiles).length === 0;

    if (isFreshBuild) {
        console.log('[DataBridge] Fresh build detected! Seeding authentic Cosmic Client configs and HUD profiles...');
        fs.mkdirSync(systemCosmic, { recursive: true });

        for (const bSrc of bundledSources) {
            const bProfiles = path.join(bSrc, 'profiles');
            if (fs.existsSync(bProfiles)) {
                syncedItems += copyDirRecursive(bProfiles, sysProfiles, false);
                for (const t of targets) {
                    syncedItems += copyDirRecursive(bProfiles, path.join(t, 'profiles'), false);
                }
            }
            const bTabby = path.join(bSrc, 'TabbyChat2');
            if (fs.existsSync(bTabby)) {
                copyDirRecursive(bTabby, path.join(systemCosmic, 'TabbyChat2'), true);
                for (const t of targets) {
                    copyDirRecursive(bTabby, path.join(t, 'TabbyChat2'), true);
                }
            }
            const bConfig = path.join(bSrc, 'config');
            if (fs.existsSync(bConfig)) {
                copyDirRecursive(bConfig, path.join(systemCosmic, 'config'), true);
                for (const t of targets) {
                    copyDirRecursive(bConfig, path.join(t, 'config'), true);
                }
            }

            const templateFiles = [
                'optionscosmic.txt',
                'optionsof.txt',
                'options.txt',
                'keybinds.json',
                'waypoints.json',
                'factions.json',
                'accounts.json',
                'servers.dat',
                'profile.json',
                'profiles.json',
                'lastProfiles.json'
            ];

            for (const tf of templateFiles) {
                const sFile = path.join(bSrc, tf);
                if (fs.existsSync(sFile)) {
                    const sysFile = path.join(systemCosmic, tf);
                    if (!fs.existsSync(sysFile) || force) {
                        try { fs.copyFileSync(sFile, sysFile); syncedItems++; } catch (e) {}
                    }
                    for (const t of targets) {
                        const dstFile = path.join(t, tf);
                        if (!fs.existsSync(dstFile) || force) {
                            try { fs.copyFileSync(sFile, dstFile); syncedItems++; } catch (e) {}
                        }
                    }
                }
            }
        }
    }

    // 3. Profiles (HUD configurations like default/hud.json, armorhud, compass, etc.)
    if (fs.existsSync(sysProfiles)) {
        for (const t of targets) {
            syncedItems += copyDirRecursive(sysProfiles, path.join(t, 'profiles'), force);
        }
    }

    // 4. TabbyChat2
    const sysTabby = path.join(systemCosmic, 'TabbyChat2');
    if (fs.existsSync(sysTabby)) {
        for (const t of targets) {
            syncedItems += copyDirRecursive(sysTabby, path.join(t, 'TabbyChat2'), force);
        }
    }

    // 5. Configs (MapWriter, WECUI, etc.)
    const sysConfigDir = path.join(systemCosmic, 'config');
    if (fs.existsSync(sysConfigDir)) {
        for (const t of targets) {
            syncedItems += copyDirRecursive(sysConfigDir, path.join(t, 'config'), force);
        }
    }

    // 6. Comprehensive Config & Settings Files
    const configFiles = [
        'accounts.json',
        'waypoints.json',
        'keybinds.json',
        'factions.json',
        'optionscosmic.txt',
        'optionsof.txt',
        'options.txt',
        'profiles.json',
        'profile.json',
        'lastProfiles.json',
        'launcher-config.json',
        'launcher-settings.txt',
        'launcher_settings.txt',
        'servers.dat',
        'usercache.json'
    ];

    for (const file of configFiles) {
        const sysFile = path.join(systemCosmic, file);
        if (fs.existsSync(sysFile)) {
            for (const t of targets) {
                const dstFile = path.join(t, file);
                if (!fs.existsSync(dstFile) || force) {
                    try {
                        fs.copyFileSync(sysFile, dstFile);
                        syncedItems++;
                    } catch (e) {}
                }
            }
        }
    }

    // Also check .minecraft/optionscosmic.txt if it exists outside cosmic folder
    const altCosmicOpt = path.join(mcDir, 'optionscosmic.txt');
    if (fs.existsSync(altCosmicOpt)) {
        for (const t of targets) {
            const dstFile = path.join(t, 'optionscosmic.txt');
            if (!fs.existsSync(dstFile) || force) {
                try { fs.copyFileSync(altCosmicOpt, dstFile); syncedItems++; } catch (e) {}
            }
        }
    }

    // 7. Screenshots
    const sysScreenshots = path.join(systemCosmic, 'screenshots');
    if (fs.existsSync(sysScreenshots)) {
        for (const t of targets) {
            bridgeFolder(sysScreenshots, path.join(t, 'screenshots'));
        }
    }

    // 8. Ensure active profile defaults to default (OG Pink) only if no profile is set
    for (const t of [systemCosmic, ...targets]) {
        const profPath = path.join(t, 'profile.json');
        try {
            if (!fs.existsSync(profPath)) {
                fs.writeFileSync(profPath, JSON.stringify({ profilename: 'default', name: 'default' }, null, 2), 'utf-8');
            }
            // If user has chosen Custom CosmicClient or any other profile, NEVER overwrite it!
        } catch (e) {}
    }

    return { success: true, syncedItems, hasOldConfigs: true, isFreshBuild };
}

/**
 * Complete synchronized import of all PC game resources, texture packs,
 * game assets, shaders, options, keybinds, and servers.
 */
function syncAll(installDir) {
    console.log(`[DataBridge] Synchronizing PC resources into Cosmic Client (${installDir})...`);
    
    // 1. Texture Packs
    const rpRes = bridgeResourcePacks(installDir);
    console.log(`[DataBridge] Resource Packs: ${rpRes.count} available (${rpRes.bridged ? 'Bridged/Linked' : 'Ready'})`);

    // 2. Game Assets (Sounds, Music, Languages)
    const assetRes = importGameAssets(installDir);
    if (assetRes.imported > 0) {
        console.log(`[DataBridge] Game Assets: Imported ${assetRes.imported} missing sound/music objects from PC.`);
    }

    // 3. Shaders
    bridgeShaderPacks(installDir);

    // 4. Options & Keybinds from .minecraft
    importPlayerOptions(installDir, false);

    // 5. Servers
    importServers(installDir, false);

    // 6. Schematics & Screenshots
    const mcDir = getSystemMinecraftDir();
    bridgeFolder(path.join(mcDir, 'schematics'), path.join(installDir, 'schematics'));
    bridgeFolder(path.join(mcDir, 'screenshots'), path.join(installDir, 'screenshots'));

    // 7. Deep sync of old Cosmic Client configs, HUD, profiles, waypoints, factions
    const configRes = syncOldCosmicConfigs(installDir, false);
    if (configRes.syncedItems > 0) {
        console.log(`[DataBridge] Configs & HUD: Synchronized ${configRes.syncedItems} old Cosmic Client settings & profiles.`);
    }

    return {
        success: true,
        resourcePacksCount: rpRes.count,
        assetsImported: assetRes.imported,
        totalAssets: assetRes.total,
        syncedConfigs: configRes.syncedItems
    };
}

module.exports = {
    getSystemMinecraftDir,
    getSystemCosmicDir,
    getOtherClientPackDirs,
    bridgeFolder,
    bridgeResourcePacks,
    importGameAssets,
    importPlayerOptions,
    importServers,
    bridgeShaderPacks,
    syncOldCosmicConfigs,
    getPCResourcesSummary,
    syncAll,
    syncPlayerData: syncAll
};

