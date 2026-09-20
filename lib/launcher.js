const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const cosmic = require('./cosmic');
const jvmPresets = require('./jvmPresets');
const localServer = require('./localServer');
const dataBridge = require('./dataBridge');
const fpsOptimizer = require('./fpsOptimizer');
const javaDownloader = require('./javaDownloader');

let gameProcess = null;
let gameStartTime = null;

/**
 * Launch Cosmic Client off-cloud with pure offline local asset serving.
 *
 * @param {string} installDir - Directory containing game files
 * @param {object} opts - Launch options
 * @param {function} onLog - Callback for real-time console streaming
 * @param {function} onExit - Callback when game process exits
 */
async function launch(installDir, opts = {}, onLog = () => {}, onExit = () => {}) {
    if (gameProcess) {
        return { success: false, error: 'Game is already running (PID: ' + gameProcess.pid + ')' };
    }

    // 1. Bridge user's PC resource packs, game assets, shaders, options, & configs
    try {
        const bridgeRes = dataBridge.syncAll(installDir);
        if (bridgeRes) {
            onLog(`[ResourceBridge] Synced ${bridgeRes.resourcePacksCount} texture packs and ${bridgeRes.totalAssets} game assets from your PC.`, 'info');
        }
    } catch (e) {
        onLog(`[ResourceBridge] Warning bridging PC files: ${e.message}`, 'warn');
    }

    // 2. Apply Cosmic FPS boosting optimizations to game config (Built for Cosmonauts)
    try {
        fpsOptimizer.applyFpsBoost(installDir);
        onLog('[Optimizer] Applied Cosmic FPS boost configuration (Built for Cosmonauts).', 'info');
    } catch (e) {
        // Ignore
    }

    const availableVersions = cosmic.getAvailableVersions();
    if (availableVersions.length === 0) {
        return { success: false, error: 'No Cosmic Client game JARs found in ' + installDir };
    }

    const isMac = process.platform === 'darwin';

    // Select target version
    const targetVersionId = opts.selectedVersion || '1.8.9';
    let versionInfo = availableVersions.find(v => v.id === targetVersionId) || availableVersions[0];

    // Java binary resolution with auto-downloader fallback
    let javaBin = opts.customJavaPath && fs.existsSync(opts.customJavaPath) ? opts.customJavaPath : null;
    if (!javaBin) {
        try {
            javaBin = await javaDownloader.ensureJava(installDir, onLog);
        } catch (e) {
            return { success: false, error: 'Failed to find or auto-download Java runtime: ' + e.message };
        }
    }

    // On Windows, prioritize native Minecraft.exe (with authentic 3-crystals PE icon)
    if (process.platform === 'win32' && !opts.customJavaPath) {
        const mcBin = path.join(installDir, 'bootstrap', 'java', 'bin', 'Minecraft.exe');
        if (fs.existsSync(mcBin)) {
            javaBin = mcBin;
        }
    }

    // Agent jar
    const useAgent = opts.useAgent !== false;
    let agentPath = cosmic.getAgentPath();
    try {
        const destAgent = path.join(installDir, 'cosmic-agent.jar');
        if (agentPath && fs.existsSync(agentPath) && (!fs.existsSync(destAgent) || fs.statSync(destAgent).size !== fs.statSync(agentPath).size || fs.statSync(destAgent).mtimeMs < fs.statSync(agentPath).mtimeMs)) {
            fs.copyFileSync(agentPath, destAgent);
            agentPath = destAgent;
        } else if (fs.existsSync(destAgent)) {
            agentPath = destAgent;
        }

        // Auto-sync old Cosmic Client configs, waypoints, HUD layout and schematics
        try {
            dataBridge.syncOldCosmicConfigs(installDir, false);
        } catch (e) {}

        // Copy authentic 4K background, logo, and cosmic.ico
        const syncPairs = [
            {
                src: path.join(__dirname, '..', 'src', 'assets', 'background.jpg'),
                dests: [
                    path.join(installDir, 'background.jpg'),
                    path.join(installDir, 'cosmic', 'background.jpg'),
                    path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'background.jpg'),
                    path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'mainmenu', 'background.jpg')
                ]
            },
            {
                src: path.join(__dirname, '..', 'logo.png'),
                dests: [
                    path.join(installDir, 'logo.png'),
                    path.join(installDir, 'cosmic', 'logo.png'),
                    path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'logo.png')
                ]
            },
            {
                src: path.join(__dirname, '..', 'cosmic.ico'),
                dests: [
                    path.join(installDir, 'cosmic.ico'),
                    path.join(installDir, 'cosmic_crystals.ico'),
                    path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'cosmic.ico'),
                    path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'cosmic_crystals.ico')
                ]
            }
        ];

        for (const pair of syncPairs) {
            if (fs.existsSync(pair.src)) {
                for (const dst of pair.dests) {
                    try {
                        fs.mkdirSync(path.dirname(dst), { recursive: true });
                        if (!fs.existsSync(dst) || fs.statSync(dst).size !== fs.statSync(pair.src).size) {
                            fs.copyFileSync(pair.src, dst);
                        }
                    } catch (e) {}
                }
            }
        }
    } catch (e) {
        // Ignore copy errors
    }

    // Start embedded local offline asset server
    let localAssetUrl = null;
    let localServerBase = null;
    try {
        localServerBase = await localServer.startServer(installDir);
        if (localServerBase) {
            localAssetUrl = `${localServerBase}/assets/1.8.json?`;
            onLog(`[OfflineVault] Pure offline asset server active at ${localServerBase}`, 'launcher');
        }
    } catch (e) {
        onLog(`[OfflineVault] Warning: local server fallback (${e.message})`, 'warn');
    }

    // Paths
    const clientJar = versionInfo.jarPath;
    const nativesDir = versionInfo.nativesDir;
    const assetsDir = versionInfo.assetsDir;
    const javaDir = path.dirname(path.dirname(javaBin));
    const bootstrapExe = path.join(installDir, 'Launcher.jar');

    // Memory & Window Settings
    const ramMB = parseInt(opts.ramMB) || versionInfo.recommendedRam || 2048;
    const maxRamMB = Math.max(ramMB, parseInt(opts.maxRamMB) || 4096);
    const width = parseInt(opts.width) || 1280;
    const height = parseInt(opts.height) || 720;
    const isFullscreen = !!opts.fullscreen;

    // Resolve Account credentials from accounts.json if not explicitly passed
    let username = opts.username ? opts.username.trim() : null;
    let uuid = opts.uuid || null;
    let accessToken = opts.accessToken || null;
    let isXbox = false;

    const accountsPath = path.join(installDir, 'accounts.json');
    let loadedAccData = null;
    if (fs.existsSync(accountsPath)) {
        try {
            loadedAccData = JSON.parse(fs.readFileSync(accountsPath, 'utf-8'));
            const selectedAccId = loadedAccData.selectedUser?.account;
            if (selectedAccId && loadedAccData.authenticationDatabase?.[selectedAccId]) {
                const acc = loadedAccData.authenticationDatabase[selectedAccId];
                if (!username) username = acc.username;
                if (!uuid) {
                    const profs = acc.profiles || {};
                    uuid = Object.keys(profs)[0] || loadedAccData.selectedUser?.profile || selectedAccId;
                }
                if (!accessToken || accessToken === 'null' || accessToken === 'microsoft_imported_token' || accessToken.startsWith('offline_token_') || accessToken.length < 50) {
                    if (acc.accessToken && acc.accessToken.length > 50 && acc.accessToken.startsWith('eyJ')) {
                        accessToken = acc.accessToken;
                    }
                }
                if (acc.type === 'Xbox' || acc.type === 'Microsoft') {
                    isXbox = true;
                }
            } else if (loadedAccData.authenticationDatabase && Object.keys(loadedAccData.authenticationDatabase).length > 0) {
                // Fallback to first available account if selectedUser is missing
                const firstId = Object.keys(loadedAccData.authenticationDatabase)[0];
                const acc = loadedAccData.authenticationDatabase[firstId];
                if (!username) username = acc.username;
                if (!uuid) {
                    const profs = acc.profiles || {};
                    uuid = Object.keys(profs)[0] || firstId;
                }
                if (!accessToken || accessToken === 'null' || accessToken === 'microsoft_imported_token' || accessToken.startsWith('offline_token_') || accessToken.length < 50) {
                    if (acc.accessToken && acc.accessToken.length > 50 && acc.accessToken.startsWith('eyJ')) {
                        accessToken = acc.accessToken;
                    }
                }
                loadedAccData.selectedUser = { account: firstId, profile: uuid };
            }
        } catch (e) {}
    }

    username = username || 'CosmicPlayer';
    uuid = uuid || '00000000000000000000000000000000';

    // If token is still a dummy/offline placeholder, scan all candidate accounts.json files for a real JWT
    if (!accessToken || accessToken === 'null' || accessToken === 'microsoft_imported_token' || accessToken.startsWith('offline_token_') || accessToken.length < 50) {
        const candidateFiles = [
            path.join(process.cwd(), 'accounts.json'),
            path.join(process.cwd(), 'cosmic', 'accounts.json'),
            path.join(installDir, 'accounts.json'),
            path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'accounts.json')
        ];
        for (const cf of candidateFiles) {
            if (fs.existsSync(cf)) {
                try {
                    const cData = JSON.parse(fs.readFileSync(cf, 'utf-8'));
                    if (cData.authenticationDatabase) {
                        for (const entry of Object.values(cData.authenticationDatabase)) {
                            const matchUser = entry.username && username && entry.username.toLowerCase() === username.toLowerCase();
                            const matchUuid = entry.profiles && uuid && Object.keys(entry.profiles).some(p => p.replace(/-/g, '').toLowerCase() === uuid.replace(/-/g, '').toLowerCase());
                            if (matchUser || matchUuid) {
                                if (entry.accessToken && entry.accessToken.length > 50 && entry.accessToken.startsWith('eyJ')) {
                                    accessToken = entry.accessToken;
                                    break;
                                }
                            }
                        }
                    }
                } catch (e) {}
            }
            if (accessToken && accessToken.length > 50 && accessToken.startsWith('eyJ')) break;
        }
    }

    const hasOnlineJwt = accessToken && accessToken.length > 50 && accessToken.startsWith('eyJ');
    accessToken = accessToken || 'offline_token_' + Buffer.from(username).toString('hex');
    if (hasOnlineJwt) {
        onLog(`[Launcher] Authenticated player: ${username} (Genuine Mojang JWT Token, len=${accessToken.length})`, 'launcher');
    } else {
        onLog(`[Launcher] Player: ${username} (Offline Mode)`, 'launcher');
    }

    // Minecraft 1.8.9's SessionType enum strictly requires 'mojang' for authenticated online play
    const userType = 'mojang';

    // Ensure accounts.json with selectedUser is synced to all cosmic directories
    try {
        if (loadedAccData) {
            const accJsonStr = JSON.stringify(loadedAccData, null, 2);
            const accSyncDests = [
                path.join(installDir, 'accounts.json'),
                path.join(installDir, 'cosmic', 'accounts.json'),
                path.join(process.env.APPDATA || '', '.minecraft', 'accounts.json'),
                path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'accounts.json'),
                path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'cosmic', 'accounts.json')
            ];
            for (const adst of accSyncDests) {
                try {
                    fs.mkdirSync(path.dirname(adst), { recursive: true });
                    fs.writeFileSync(adst, accJsonStr, 'utf-8');
                } catch (e) {}
            }
        }
    } catch (e) {}

    // JVM Tuning Profile
    const preset = jvmPresets.getPresetById(opts.jvmPreset);
    const presetArgs = preset && preset.args ? preset.args.split(/\s+/).filter(Boolean) : [];
    const customArgs = opts.customJvmArgs ? opts.customJvmArgs.split(/\s+/).filter(Boolean) : [];

    // Construct JVM arguments
    const jvmArgs = [
        `-Dcosmic.java=${javaDir}`,
        `-Dcosmic.installdir=${installDir}`,
        `-Dcosmic.bootstrap=${bootstrapExe}`,
        '-Dcosmic.launcher.load=v1',
        '-Dcosmic.log.plain=true',
        '-Dcosmic.version=2.7.0.b84ff',
        '-Dcosmic.branch=master',
        '-Dapple.awt.application.name=Cosmic Client',
        '-Dapple.awt.application.appearance=system',
        '-Duser.language=en-US',
        `-Djava.library.path=${nativesDir}`,
        `-Xms${ramMB}M`,
        `-Xmx${maxRamMB}M`,
        '-Xmn128M',
        `-XX:MaxDirectMemorySize=${maxRamMB}M`,
        '-XX:+DisableAttachMechanism',
        '-Djdk.attach.allowAttachSelf=false',
        '-Dsun.tools.attach.enable=false',
        '-Dcosmic.hardening=true',
        `-Dcosmic.player.name=${username}`,
        `-Dcosmic.player.uuid=${uuid}`,
        `-Dcosmic.player.token=${accessToken}`,
        '-Xshare:off',
        '--add-opens', 'java.desktop/java.awt.event=ALL-UNNAMED',
        '--add-opens', 'java.desktop/sun.awt=ALL-UNNAMED',
        '--add-opens', 'java.management/sun.management=ALL-UNNAMED'
    ];

    // Point in-game URL routing to our local 100% offline server
    if (localServerBase) {
        jvmArgs.push(`-Dcosmic.local.server=${localServerBase}`);
        jvmArgs.push('-Dcosmic.offline=true');
    }

    // macOS specific flags
    if (isMac) {
        jvmArgs.push('-XstartOnFirstThread');
        jvmArgs.push('-Dapple.laf.useScreenMenuBar=true');
    }

    // Attach agent if enabled
    if (useAgent && fs.existsSync(agentPath)) {
        jvmArgs.push(`-javaagent:${agentPath}`);
    }

    // Add optimizer args and custom JVM flags
    jvmArgs.push(...presetArgs, ...customArgs);

    // Jar and main entry
    jvmArgs.push('-jar', clientJar);

    // Game arguments
    const gameArgs = [
        '--version', versionInfo.assetIndex || '1.8',
        '--assetsDir', assetsDir,
        '--assetIndex', versionInfo.assetIndex || '1.8',
        '--username', username,
        '--uuid', uuid.replace(/-/g, ''),
        '--accessToken', accessToken,
        '--userType', userType,
        '--versionType', 'CosmicClient',
        '--userProperties', '{}',
        '--width', String(width),
        '--height', String(height),
        '--novid'
    ];

    if (isFullscreen) {
        gameArgs.push('--fullscreen');
    }

    if (opts.serverDirectConnect && opts.serverDirectConnect.trim()) {
        const srv = opts.serverDirectConnect.trim();
        const parts = srv.split(':');
        gameArgs.push('--server', parts[0]);
        if (parts[1]) {
            gameArgs.push('--port', parts[1]);
        }
    }

    const fullArgs = [...jvmArgs, ...gameArgs];

    onLog(`[Launcher] ==========================================`, 'launcher');
    onLog(`[Launcher] Starting Cosmic Client (100% Offline Mode)...`, 'launcher');
    onLog(`[Launcher] Platform: ${process.platform} (${process.arch})`, 'launcher');
    onLog(`[Launcher] Version: ${versionInfo.name}`, 'launcher');
    onLog(`[Launcher] Player: ${username} (${uuid})`, 'launcher');
    onLog(`[Launcher] Java Runtime: ${javaBin}`, 'launcher');
    onLog(`[Launcher] Natives Path: ${nativesDir}`, 'launcher');
    onLog(`[Launcher] Agent: ${useAgent ? agentPath : 'Disabled'}`, 'agent');
    onLog(`[Launcher] Memory: ${ramMB}MB min / ${maxRamMB}MB max`, 'launcher');
    onLog(`[Launcher] Resolution: ${width}x${height}${isFullscreen ? ' (Fullscreen)' : ''}`, 'launcher');
    onLog(`[Launcher] Executable Command:`, 'launcher');
    onLog(`[Launcher] "${javaBin}" ${fullArgs.join(' ')}\n`, 'launcher');

    try {
        const cleanEnv = { ...process.env };
        delete cleanEnv._JAVA_OPTIONS;
        delete cleanEnv.JAVA_TOOL_OPTIONS;
        delete cleanEnv.JAVA_OPTIONS;
        delete cleanEnv.IBM_JAVA_OPTIONS;

        gameStartTime = Date.now();
        gameProcess = spawn(javaBin, fullArgs, {
            cwd: installDir,
            env: cleanEnv,
            stdio: ['ignore', 'pipe', 'pipe']
        });

        onLog(`[Launcher] Process spawned successfully with PID: ${gameProcess.pid}`, 'launcher');

        let stdoutBuffer = '';
        let stderrBuffer = '';
        let lastModelPct = -1;
        let lastSoundPct = -1;

        function processLine(rawLine, isStderr = false) {
            const line = rawLine.trim();
            if (!line) return;

            // Filter and consolidate high-frequency OptiFine model & sound loading ticks
            if (line.startsWith('[*Load]')) {
                const loadMatch = line.match(/^\[\*Load\]\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s*(.*)/);
                if (loadMatch) {
                    const cur = parseFloat(loadMatch[1]);
                    const max = parseFloat(loadMatch[2]);
                    const desc = (loadMatch[3] || '').trim();

                    if (desc.startsWith('Loading model')) {
                        // Throttling: only emit every 10% or at 100% completion
                        const pctStep = Math.floor((cur / max) * 10);
                        if (pctStep !== lastModelPct || cur >= max) {
                            lastModelPct = pctStep;
                            const pct = Math.min(100, Math.round((cur / max) * 100));
                            onLog(`[*Load] ${Math.round(cur)} ${Math.round(max)} Loading block & item models (${pct}%)`, 'game');
                        }
                        return;
                    }

                    if (desc.startsWith('Loading sound file')) {
                        // Throttling: only emit every 25% or at 100% completion
                        const pctStep = Math.floor((cur / max) * 4);
                        if (pctStep !== lastSoundPct || cur >= max) {
                            lastSoundPct = pctStep;
                            const pct = Math.min(100, Math.round((cur / max) * 100));
                            onLog(`[*Load] ${Math.round(cur)} ${Math.round(max)} Loading audio sounds (${pct}%)`, 'game');
                        }
                        return;
                    }
                }
            }

            onLog(line, classifyOutput(line, isStderr));
        }

        gameProcess.stdout.on('data', (data) => {
            stdoutBuffer += data.toString();
            const lines = stdoutBuffer.split(/\r?\n/);
            stdoutBuffer = lines.pop();
            for (const line of lines) {
                processLine(line, false);
            }
        });

        gameProcess.stderr.on('data', (data) => {
            stderrBuffer += data.toString();
            const lines = stderrBuffer.split(/\r?\n/);
            stderrBuffer = lines.pop();
            for (const line of lines) {
                processLine(line, true);
            }
        });

        gameProcess.on('close', (code, signal) => {
            if (stdoutBuffer.trim()) {
                processLine(stdoutBuffer, false);
                stdoutBuffer = '';
            }
            if (stderrBuffer.trim()) {
                processLine(stderrBuffer, true);
                stderrBuffer = '';
            }
            const elapsed = Math.round((Date.now() - (gameStartTime || Date.now())) / 1000);
            const statusMsg = `[Launcher] Game process closed (Exit Code: ${code !== null ? code : 'Killed by ' + signal}, Duration: ${elapsed}s)`;
            onLog(`\n${statusMsg}\n`, code === 0 ? 'launcher' : 'error');
            gameProcess = null;
            gameStartTime = null;
            onExit({ code, signal, elapsed });
        });

        gameProcess.on('error', (err) => {
            onLog(`[Launcher] Process Error: ${err.message}`, 'error');
            gameProcess = null;
            gameStartTime = null;
            onExit({ error: err.message });
        });

        return {
            success: true,
            pid: gameProcess.pid,
            version: versionInfo.name,
            username
        };

    } catch (err) {
        gameProcess = null;
        gameStartTime = null;
        return { success: false, error: err.message };
    }
}

/**
 * Categorize log message for colorized console output.
 */
function classifyOutput(text, isStderr = false) {
    if (text.includes('[CosmicAgent]') || text.includes('Agent')) return 'agent';
    if (text.includes('[Launcher]') || text.includes('[OfflineVault]')) return 'launcher';
    if (text.includes('FATAL') || text.includes('Exception') || text.includes('Error:') || (isStderr && !text.includes('Picked up _JAVA_OPTIONS'))) {
        return 'error';
    }
    if (text.includes('WARN') || text.includes('warning')) return 'warn';
    if (text.includes('INFO') || text.includes('[Client thread/INFO]')) return 'game';
    return isStderr ? 'error' : 'game';
}

/**
 * Terminate running game process.
 */
function kill() {
    if (!gameProcess) {
        return { success: false, error: 'No game process running' };
    }
    try {
        const pid = gameProcess.pid;
        if (process.platform === 'win32') {
            spawn('taskkill', ['/pid', String(pid), '/f', '/t']);
        } else {
            gameProcess.kill('SIGKILL');
        }
        return { success: true, pid };
    } catch (err) {
        return { success: false, error: err.message };
    }
}

/**
 * Check if game is currently running.
 */
function isRunning() {
    return gameProcess !== null;
}

function getRunningPid() {
    return gameProcess ? gameProcess.pid : null;
}

module.exports = {
    launch,
    kill,
    isRunning,
    getRunningPid
};
