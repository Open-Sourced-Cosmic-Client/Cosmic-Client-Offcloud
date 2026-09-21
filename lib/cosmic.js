const path = require('path');
const fs = require('fs');
const os = require('os');

/**
 * Get platform and architecture info.
 */
function getPlatformInfo() {
    const plat = process.platform;
    const arch = process.arch;
    return {
        isWindows: plat === 'win32',
        isMac: plat === 'darwin',
        isLinux: plat === 'linux',
        isArm64: arch === 'arm64',
        isX64: arch === 'x64',
        platformId: plat === 'win32' ? (arch === 'ia32' ? 'windows-x32' : 'windows-x64')
                   : plat === 'darwin' ? (arch === 'arm64' ? 'mac-aarch64' : 'mac-x64')
                   : `linux-${arch === 'x64' ? 'x64' : arch}`
    };
}

/**
 * Get potential install directory candidates across Windows, macOS, and Linux.
 */
function getInstallDirCandidates() {
    const portDir = process.env.PORTABLE_EXECUTABLE_DIR;
    const portableDir = portDir ? path.join(portDir, 'CosmicClient-x64') : null;
    const portableDirDirect = portDir ? portDir : null;
    const cwdDir = path.join(process.cwd(), 'CosmicClient-x64');
    const localDir = path.join(__dirname, '..', 'CosmicClient-x64');
    const localParent = path.join(__dirname, '..');
    const distOuterDir = process.resourcesPath ? path.join(process.resourcesPath, '..', '..', 'CosmicClient-x64') : null;
    const distOuterDirAlt = process.resourcesPath ? path.join(process.resourcesPath, '..', 'CosmicClient-x64') : null;
    const resourceDir = process.resourcesPath ? path.join(process.resourcesPath, 'CosmicClient-x64') : null;
    const resourceParent = process.resourcesPath ? process.resourcesPath : null;
    
    let userCosmicDir = '';
    if (process.platform === 'win32') {
        userCosmicDir = path.join(process.env.APPDATA || '', '.minecraft', 'cosmic');
    } else if (process.platform === 'darwin') {
        userCosmicDir = path.join(os.homedir(), 'Library', 'Application Support', '.minecraft', 'cosmic');
    } else {
        userCosmicDir = path.join(os.homedir(), '.minecraft', 'cosmic');
    }

    return [
        portableDir,
        portableDirDirect,
        cwdDir,
        distOuterDir,
        distOuterDirAlt,
        localDir,
        resourceDir,
        localParent,
        resourceParent,
        userCosmicDir
    ].filter(Boolean);
}

function findEmbeddedPayload() {
    const portDir = process.env.PORTABLE_EXECUTABLE_DIR;
    const candidates = [
        portDir ? path.join(portDir, 'cosmic-payload.zip') : null,
        process.resourcesPath ? path.join(process.resourcesPath, 'cosmic-payload.zip') : null,
        path.join(__dirname, '..', 'cosmic-payload.zip'),
        path.join(process.cwd(), 'cosmic-payload.zip')
    ].filter(Boolean);

    for (const c of candidates) {
        if (fs.existsSync(c)) return c;
    }
    return null;
}

/**
/**
 * Find valid assets directory candidate.
 */
function getAssetsDir(installDir) {
    const candidates = [
        path.join(installDir, 'assets_18'),
        path.join(installDir, '1.8', 'assets'),
        path.join(installDir, 'assets'),
        path.join(process.cwd(), 'CosmicClient-x64', 'assets_18'),
        path.join(__dirname, '..', 'CosmicClient-x64', 'assets_18')
    ];
    for (const c of candidates) {
        if (fs.existsSync(path.join(c, 'indexes', '1.8.json'))) {
            return c;
        }
    }
    return path.join(installDir, 'assets_18');
}

function extractPayload(payloadZip, targetDir) {
    if (!payloadZip || !fs.existsSync(payloadZip)) return false;
    try {
        fs.mkdirSync(targetDir, { recursive: true });
        const { execSync } = require('child_process');
        if (process.platform === 'win32') {
            try {
                // Built-in bsdtar is 10x faster than PowerShell Expand-Archive
                execSync(`tar -xf "${payloadZip}" -C "${targetDir}"`, { stdio: 'ignore', timeout: 60000 });
                return true;
            } catch (e) {
                execSync(`powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '${payloadZip}' -DestinationPath '${targetDir}' -Force"`, { stdio: 'ignore', timeout: 120000 });
                return true;
            }
        } else {
            execSync(`unzip -o "${payloadZip}" -d "${targetDir}"`, { stdio: 'ignore', timeout: 60000 });
            return true;
        }
    } catch (err) {
        console.error('[CosmicClient] Extraction failed:', err.message);
        return false;
    }
}

/**
 * Detect the primary install directory with existing Cosmic Client files.
 * If running on a clean machine without game files or missing asset indexes/java,
 * automatically extracts the embedded zero-bloat vault into %APPDATA%\.minecraft\cosmic.
 */
function getInstallDir() {
    const candidates = getInstallDirCandidates();
    let payloadZip = findEmbeddedPayload();

    for (const candidate of candidates) {
        const jar18 = path.join(candidate, '1.8', 'CosmicClient-1.8.9.jar');
        const jar18Alt = path.join(candidate, '1.8', 'CosmicClient-1.0-SNAPSHOT.jar');
        const hasJar = fs.existsSync(jar18) || fs.existsSync(jar18Alt);
        const hasAssets = fs.existsSync(path.join(candidate, 'assets_18', 'indexes', '1.8.json')) ||
                          fs.existsSync(path.join(candidate, '1.8', 'assets', 'indexes', '1.8.json'));
        const hasJava = fs.existsSync(path.join(candidate, 'bootstrap', 'java', 'bin', 'java.exe')) ||
                        fs.existsSync(path.join(candidate, 'bootstrap', 'java', 'bin', 'java'));
        
        if (hasJar && hasAssets && hasJava) {
            return candidate;
        }

        // If jar exists but assets or java are missing, extract payload into this directory if available
        if (hasJar && (!hasAssets || !hasJava) && payloadZip) {
            console.log('[CosmicClient] Incomplete components in ' + candidate + '. Extracting bundled vault...');
            extractPayload(payloadZip, candidate);
            return candidate;
        }
    }

    // Auto-extract embedded payload if present and candidate files are missing
    if (payloadZip) {
        let targetDir = '';
        if (process.platform === 'win32') {
            targetDir = path.join(process.env.APPDATA || '', '.minecraft', 'cosmic');
        } else if (process.platform === 'darwin') {
            targetDir = path.join(os.homedir(), 'Library', 'Application Support', '.minecraft', 'cosmic');
        } else {
            targetDir = path.join(os.homedir(), '.minecraft', 'cosmic');
        }

        console.log('[CosmicClient] Extracting embedded client vault into ' + targetDir + '...');
        if (extractPayload(payloadZip, targetDir)) {
            if (fs.existsSync(path.join(targetDir, '1.8', 'CosmicClient-1.8.9.jar'))) {
                console.log('[CosmicClient] Embedded client vault successfully extracted!');
                return targetDir;
            }
        }
    }

    return candidates[0];
}

/**
 * Recursively search for java executable in a directory.
 */
function findJavaBinaryInDir(dirPath) {
    if (!dirPath || !fs.existsSync(dirPath)) return null;
    const isWin = process.platform === 'win32';
    const targetName = isWin ? 'java.exe' : 'java';

    try {
        const entries = fs.readdirSync(dirPath, { withFileTypes: true });
        for (const entry of entries) {
            const full = path.join(dirPath, entry.name);
            if (entry.isDirectory()) {
                const found = findJavaBinaryInDir(full);
                if (found) return found;
            } else if (entry.name.toLowerCase() === targetName.toLowerCase()) {
                const parentDirName = path.basename(path.dirname(full)).toLowerCase();
                if (parentDirName === 'bin') {
                    return full;
                }
            }
        }
    } catch (e) {}
    return null;
}

function isRunnableJava(binPath) {
    if (!binPath || !fs.existsSync(binPath)) return false;
    try {
        const { execFileSync } = require('child_process');
        execFileSync(binPath, ['-version'], { stdio: 'ignore', timeout: 3000 });
        return true;
    } catch (e) {
        return false;
    }
}

/**
 * Find the best Java binary for the current OS (Windows, macOS, Linux).
 */
function getJavaBinary(customPath = null) {
    if (customPath && isRunnableJava(customPath)) {
        return customPath;
    }

    const installDir = getInstallDir();
    const isWin = process.platform === 'win32';
    const isMac = process.platform === 'darwin';
    const isArm = process.arch === 'arm64';

    // 1. Direct bootstrap dynamic search
    const bootstrapDir = path.join(installDir, 'bootstrap');
    const discoveredInBootstrap = findJavaBinaryInDir(bootstrapDir);
    if (discoveredInBootstrap && isRunnableJava(discoveredInBootstrap)) {
        return discoveredInBootstrap;
    }

    const candidatePaths = [
        // Windows bundled Zulu 19
        isWin ? path.join(installDir, 'bootstrap', 'java', 'bin', 'java.exe') : null,

        // macOS extracted .jre bundles in bootstrap
        isMac && isArm ? path.join(installDir, 'bootstrap', 'java-mac-arm64', 'bin', 'java') : null,
        isMac && isArm ? path.join(installDir, 'bootstrap', 'java-mac-arm64', 'zulu-19.jre', 'Contents', 'Home', 'bin', 'java') : null,
        isMac && !isArm ? path.join(installDir, 'bootstrap', 'java-mac-x64', 'bin', 'java') : null,
        isMac && !isArm ? path.join(installDir, 'bootstrap', 'java-mac-x64', 'zulu-19.jre', 'Contents', 'Home', 'bin', 'java') : null,
        isMac ? path.join(installDir, 'bootstrap', 'java-mac', 'bin', 'java') : null,
        isMac ? path.join(installDir, 'bootstrap', 'java-mac', 'zulu-19.jre', 'Contents', 'Home', 'bin', 'java') : null,
        isMac ? path.join(installDir, 'bootstrap', 'java', 'bin', 'java') : null,

        // macOS system locations
        isMac ? path.join(os.homedir(), 'Library', 'Application Support', '.minecraft', 'cosmic', 'bootstrap', 'java', 'bin', 'java') : null,
        isMac ? '/Library/Java/JavaVirtualMachines/zulu-19.jdk/Contents/Home/bin/java' : null,
        isMac ? '/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home/bin/java' : null,
        isMac ? '/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home/bin/java' : null,
        isMac ? '/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java' : null,
        isMac ? '/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java' : null,
        isMac ? '/opt/homebrew/opt/openjdk/bin/java' : null,
        isMac ? '/opt/homebrew/opt/openjdk@17/bin/java' : null,
        isMac ? '/opt/homebrew/opt/openjdk@21/bin/java' : null,
        isMac ? '/usr/local/opt/openjdk/bin/java' : null,

        // Windows system locations
        isWin ? path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'bootstrap', 'java', 'bin', 'java.exe') : null,
        isWin ? 'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe' : null,
        isWin ? 'C:\\Program Files\\Java\\jdk-17\\bin\\java.exe' : null,
        isWin ? 'C:\\Program Files\\Java\\jdk-22\\bin\\java.exe' : null,
        isWin ? 'C:\\Program Files\\Java\\jdk-24\\bin\\java.exe' : null,
        isWin ? 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.10.7-hotspot\\bin\\java.exe' : null,
        isWin ? 'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.2.13-hotspot\\bin\\java.exe' : null,
        isWin ? path.join(os.homedir(), '.jdks', 'corretto-21.0.11', 'bin', 'java.exe') : null,
        isWin ? path.join(os.homedir(), '.jdks', 'corretto-17.0.9', 'bin', 'java.exe') : null,

        // Linux locations
        !isWin && !isMac ? path.join(installDir, 'bootstrap', 'java-linux', 'bin', 'java') : null,
        !isWin && !isMac ? '/usr/bin/java' : null,
        !isWin && !isMac ? '/usr/lib/jvm/default-java/bin/java' : null
    ].filter(Boolean);

    for (const p of candidatePaths) {
        if (isRunnableJava(p)) {
            return p;
        }
    }

    // Dynamic search across macOS /Library/Java/JavaVirtualMachines if available
    if (isMac && fs.existsSync('/Library/Java/JavaVirtualMachines')) {
        const jvmFound = findJavaBinaryInDir('/Library/Java/JavaVirtualMachines');
        if (jvmFound && isRunnableJava(jvmFound)) return jvmFound;
    }

    return isRunnableJava('java') ? 'java' : null;
}

/**
 * Get path to the Java Agent jar.
 */
function getAgentPath() {
    const portDir = process.env.PORTABLE_EXECUTABLE_DIR;
    // Authoritative bundled agent candidates
    const bundledCandidates = [
        process.resourcesPath ? path.join(process.resourcesPath, 'cosmic-agent.jar') : null,
        portDir ? path.join(portDir, 'cosmic-agent.jar') : null,
        portDir ? path.join(portDir, 'CosmicClient-x64', 'cosmic-agent.jar') : null,
        path.join(__dirname, '..', 'cosmic-agent.jar'),
        path.join(process.cwd(), 'cosmic-agent.jar')
    ].filter(Boolean);

    for (const c of bundledCandidates) {
        if (fs.existsSync(c)) {
            return c;
        }
    }

    const installPath = path.join(getInstallDir(), 'cosmic-agent.jar');
    if (fs.existsSync(installPath)) {
        return installPath;
    }

    return bundledCandidates[0] || installPath;
}

/**
 * Get platform-specific native libraries directory.
 */
function getNativesDir(versionDir = '1.8') {
    const installDir = getInstallDir();
    const isWin = process.platform === 'win32';
    const isMac = process.platform === 'darwin';
    const isLinux = process.platform === 'linux';
    const isArm = process.arch === 'arm64';

    const candidates = [
        isMac && isArm ? path.join(installDir, versionDir, 'bin-mac-arm64') : null,
        isMac && !isArm ? path.join(installDir, versionDir, 'bin-mac-x64') : null,
        isMac ? path.join(installDir, versionDir, 'bin-mac') : null,
        isMac ? path.join(installDir, versionDir, 'bin-mac-arm64') : null,
        isMac ? path.join(installDir, versionDir, 'bin-mac-x64') : null,
        isMac ? path.join(installDir, versionDir, 'natives-mac') : null,
        isLinux ? path.join(installDir, versionDir, 'bin-linux') : null,
        isWin ? path.join(installDir, versionDir, 'bin-1.8') : null,
        path.join(installDir, versionDir, 'natives'),
        path.join(installDir, versionDir, 'bin-1.8')
    ].filter(Boolean);

    for (const c of candidates) {
        if (fs.existsSync(c)) {
            return c;
        }
    }

    return path.join(installDir, versionDir, 'bin-1.8');
}

/**
 * Scan available game versions and client JARs.
 */
function getAvailableVersions() {
    const installDir = getInstallDir();
    const versions = [];

    // 1.8.9 (Pure Competitive 1.8.9 Client)
    const pvp18 = path.join(installDir, '1.8', 'CosmicClient-1.8.9.jar');
    const pvp18Snapshot = path.join(installDir, '1.8', 'CosmicClient-1.0-SNAPSHOT.jar');
    if (fs.existsSync(pvp18) || fs.existsSync(pvp18Snapshot)) {
        versions.push({
            id: '1.8.9',
            name: 'Cosmic Client 1.8.9 (PvP / Factions / Prisons)',
            jarPath: fs.existsSync(pvp18) ? pvp18 : pvp18Snapshot,
            assetsDir: getAssetsDir(installDir),
            assetIndex: '1.8',
            nativesDir: getNativesDir('1.8'),
            librariesDir: path.join(installDir, '1.8', 'libraries'),
            recommendedRam: 2048
        });
    }

    return versions;
}

/**
 * Check full installation status.
 */
function detectInstall() {
    const installDir = getInstallDir();
    const javaBin = getJavaBinary();
    const agentJar = getAgentPath();
    const versions = getAvailableVersions();
    const platformInfo = getPlatformInfo();

    const criticalChecks = {
        installDir: fs.existsSync(installDir),
        java: fs.existsSync(javaBin) || javaBin === 'java',
        agent: fs.existsSync(agentJar),
        versions: versions.length > 0
    };

    const isReady = Object.values(criticalChecks).every(Boolean);

    return {
        isReady,
        installDir,
        javaBin,
        agentJar,
        versions,
        platformInfo,
        checks: criticalChecks
    };
}

/**
 * Directory helper getters for quick-open buttons.
 */
function getFolders() {
    const base = getInstallDir();
    
    // Auto-bridge AppData resource packs, shaders, schematics, and configs if present
    try {
        const dataBridge = require('./dataBridge');
        dataBridge.syncPlayerData(base);
    } catch (e) {
        // Ignore
    }

    const folders = {
        gameRoot: base,
        config: path.join(base, 'cosmic'),
        resourcepacks: path.join(base, 'resourcepacks'),
        shaderpacks: path.join(base, 'shaderpacks'),
        screenshots: path.join(base, 'screenshots'),
        saves: path.join(base, 'saves'),
        schematics: path.join(base, 'schematics'),
        logs: path.join(base, 'logs'),
        offlineArchive: path.join(__dirname, '..', 'offline-archive')
    };

    return folders;
}

/**
 * Return file counts inside each game folder.
 */
function getFolderStats() {
    const folders = getFolders();
    const stats = {};
    for (const [k, p] of Object.entries(folders)) {
        if (p && fs.existsSync(p)) {
            try {
                const files = fs.readdirSync(p);
                stats[k] = files.length;
            } catch (e) {
                stats[k] = 0;
            }
        } else {
            stats[k] = 0;
        }
    }
    return stats;
}

module.exports = {
    getPlatformInfo,
    getInstallDir,
    getJavaBinary,
    getAgentPath,
    getNativesDir,
    getAvailableVersions,
    detectInstall,
    getFolders,
    getFolderStats
};
