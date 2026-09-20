const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const auth = require('./auth');

let serverInstance = null;
let activePort = null;
let currentAuthState = {
    status: 'idle',
    userCode: null,
    directUrl: null,
    verificationUri: null,
    account: null,
    error: null,
    timestamp: 0
};

const OFFLINE_DASHBOARD_HTML = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cosmic Client - 100% Pure Offline Mode</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }
        body { background: radial-gradient(circle at top, #141829 0%, #080a10 100%); color: #e2e8f0; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
        .card { background: rgba(18, 22, 38, 0.9); border: 1px solid rgba(99, 102, 241, 0.3); border-radius: 16px; padding: 40px; max-width: 580px; width: 100%; box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6), 0 0 30px rgba(99, 102, 241, 0.15); text-align: center; }
        .badge { display: inline-flex; align-items: center; gap: 8px; background: rgba(34, 197, 94, 0.15); border: 1px solid rgba(34, 197, 94, 0.4); color: #4ade80; padding: 6px 16px; border-radius: 9999px; font-size: 13px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 24px; }
        .badge::before { content: ''; width: 8px; height: 8px; border-radius: 50%; background: #4ade80; box-shadow: 0 0 8px #4ade80; }
        h1 { font-size: 26px; font-weight: 700; color: #ffffff; margin-bottom: 12px; }
        p { color: #94a3b8; font-size: 15px; line-height: 1.6; margin-bottom: 24px; }
        .feature-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 24px; text-align: left; }
        .feature-item { background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.06); border-radius: 10px; padding: 14px; }
        .feature-title { font-size: 13px; font-weight: 600; color: #cbd5e1; margin-bottom: 4px; }
        .feature-desc { font-size: 12px; color: #64748b; }
        .status-box { background: rgba(99, 102, 241, 0.08); border: 1px solid rgba(99, 102, 241, 0.2); border-radius: 10px; padding: 12px; font-family: monospace; font-size: 12px; color: #818cf8; word-break: break-all; }
    </style>
</head>
<body>
    <div class="card">
        <div class="badge">100% Off-Cloud Active</div>
        <h1>Cosmic Client Local Vault</h1>
        <p>All external links, store URLs, and CDN queries are intercepted and served completely off-cloud from your local machine. No external internet connections or telemetry requests are permitted.</p>
        
        <div class="feature-grid">
            <div class="feature-item">
                <div class="feature-title">Local Asset Vault</div>
                <div class="feature-desc">1.8.json & all sounds/textures stored on disk</div>
            </div>
            <div class="feature-item">
                <div class="feature-title">Zero Cloud Telemetry</div>
                <div class="feature-desc">All tracking & checkins blocked or mocked</div>
            </div>
            <div class="feature-item">
                <div class="feature-title">URL Interceptor</div>
                <div class="feature-desc">In-game web links stay airgapped</div>
            </div>
            <div class="feature-item">
                <div class="feature-title">Offline Anti-Cheat</div>
                <div class="feature-desc">Self-contained offline auth and guards</div>
            </div>
        </div>

        <div class="status-box">
            Status: Pure Offline Vault Active on 127.0.0.1
        </div>
    </div>
</body>
</html>`;

function generateOfflineAuthResponse(bodyStr) {
    let req = {};
    try {
        if (bodyStr) req = JSON.parse(bodyStr);
    } catch (e) {}

    const name = req.name || 'CosmicPlayer';
    let rawUuid = (req.uuid || '00000000000000000000000000000000').replace(/-/g, '');
    if (rawUuid.length !== 32) rawUuid = '00000000000000000000000000000000';

    const tokenHash = req.mojang_token_hash || 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
    const version = req.version || '2.7.0.b84ff';
    const clientTime = req.client_time || Date.now();
    const clientRandom = req.client_random || (Math.floor(Math.random() * 1000000000));

    const serverTime = Date.now();
    const serverRandom = Math.floor(Math.random() * 1000000000);
    const lifespan = 86400000000; // 1000 days
    const hmac = 'a1b2c3d4e5f60718293a4b5c6d7e8f901a2b3c4d5e6f708192a3b4c5d6e7f809';

    return {
        name,
        uuid: rawUuid,
        mojang_token_hash: tokenHash,
        version,
        client_time: clientTime,
        client_random: clientRandom,
        server_time: serverTime,
        server_random: serverRandom,
        lifespan,
        hmac,
        status: 'ok',
        authenticated: true,
        valid: true,
        success: true,
        token: 'cosmic_offline_token',
        cape: null,
        cosmetics: [],
        item_skins: {},
        skins: {},
        emotes: []
    };
}

/**
 * Start a lightweight local offline HTTP server to serve all client assets,
 * asset indexes (1.8.json), and CDN requests purely offline with zero internet reliance.
 *
 * @param {string} installDir - Path to CosmicClient-x64
 * @returns {Promise<string>} - The base URL, e.g. "http://127.0.0.1:25560"
 */
function startServer(installDir) {
    return new Promise((resolve, reject) => {
        if (serverInstance && activePort) {
            return resolve(`http://127.0.0.1:${activePort}`);
        }

        const offlineArchiveCandidates = [
            process.resourcesPath ? path.join(process.resourcesPath, 'offline-archive') : null,
            path.join(__dirname, '..', 'offline-archive'),
            path.join(installDir, 'offline-archive')
        ].filter(Boolean);
        let offlineArchiveDir = offlineArchiveCandidates[0];
        for (const cand of offlineArchiveCandidates) {
            if (fs.existsSync(cand)) {
                offlineArchiveDir = cand;
                break;
            }
        }
        const assetIndexFile = fs.existsSync(path.join(offlineArchiveDir, '1.8.json'))
            ? path.join(offlineArchiveDir, '1.8.json')
            : path.join(installDir, 'assets_18', 'indexes', '1.8.json');

        const manifestFile = path.join(offlineArchiveDir, 'manifest.json');

        const server = http.createServer((req, res) => {
            const parsedUrl = new URL(req.url, 'http://127.0.0.1');
            const pathname = parsedUrl.pathname;

            // Enable CORS and cache headers
            res.setHeader('Access-Control-Allow-Origin', '*');
            res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS, HEAD');
            res.setHeader('Access-Control-Allow-Headers', '*');
            res.setHeader('Cache-Control', 'public, max-age=86400');

            if (req.method === 'OPTIONS') {
                res.writeHead(200);
                return res.end();
            }

            // Read request body for POST/PUT
            let bodyChunks = [];
            req.on('data', chunk => bodyChunks.push(chunk));
            req.on('end', () => {
                const bodyStr = Buffer.concat(bodyChunks).toString('utf-8');

                // 1. Offline Landing / Dashboard (for intercepted links)
                if (pathname === '/' || pathname === '/offline' || pathname === '/dashboard' || pathname.startsWith('/store') || pathname.startsWith('/forum')) {
                    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
                    return res.end(OFFLINE_DASHBOARD_HTML);
                }

                // 2. Asset Index 1.8.json (Critical for startup)
                if (pathname.includes('1.8.json') || pathname.endsWith('/1.8')) {
                    if (fs.existsSync(assetIndexFile)) {
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        return fs.createReadStream(assetIndexFile).pipe(res);
                    }
                }

                // 3. Manifest JSON
                if (pathname.includes('manifest.json')) {
                    if (fs.existsSync(manifestFile)) {
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        return fs.createReadStream(manifestFile).pipe(res);
                    }
                }

                // 4. Objects / Sounds / Textures
                if (pathname.startsWith('/objects/') || pathname.startsWith('/assets/objects/')) {
                    const cleanRel = pathname.replace('/assets/objects/', '').replace('/objects/', '');
                    const objPath = path.join(installDir, 'assets_18', 'objects', cleanRel);
                    if (fs.existsSync(objPath)) {
                        res.writeHead(200, { 'Content-Type': 'application/octet-stream' });
                        return fs.createReadStream(objPath).pipe(res);
                    }
                }

                // 5. In-Game Microsoft Authentication Bridge
                if (pathname === '/api/auth/start' || pathname === '/api/auth/device-code') {
                    currentAuthState = {
                        status: 'starting',
                        userCode: null,
                        directUrl: null,
                        verificationUri: null,
                        account: null,
                        error: null,
                        timestamp: Date.now()
                    };

                    auth.cancelAuth();

                    auth.loginMicrosoft(installDir, (codeInfo) => {
                        console.log('[LocalServer] In-game Microsoft auth code received:', codeInfo.userCode);
                        currentAuthState.status = 'code';
                        currentAuthState.userCode = codeInfo.userCode;
                        currentAuthState.directUrl = codeInfo.directUrl;
                        currentAuthState.verificationUri = codeInfo.verificationUri;
                    }).then((result) => {
                        if (result && result.success) {
                            console.log('[LocalServer] In-game Microsoft auth succeeded for:', result.displayName);
                            currentAuthState.status = 'success';
                            currentAuthState.displayName = result.displayName;
                            currentAuthState.token = result.token;
                            currentAuthState.id = (result.profile && result.profile.id) ? result.profile.id : result.accountId;
                            currentAuthState.account = {
                                displayName: result.displayName,
                                token: result.token,
                                accountId: result.accountId,
                                profile: result.profile
                            };
                        } else {
                            console.warn('[LocalServer] In-game Microsoft auth failed:', result ? result.error : 'unknown');
                            currentAuthState.status = 'error';
                            currentAuthState.error = (result && result.error) ? result.error : 'Authentication failed';
                        }
                    }).catch((err) => {
                        console.error('[LocalServer] In-game Microsoft auth exception:', err.message);
                        currentAuthState.status = 'error';
                        currentAuthState.error = err.message;
                    });

                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify({ status: 'starting' }));
                }

                if (pathname === '/api/auth/poll' || pathname === '/api/auth/status') {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify(currentAuthState));
                }

                if (pathname === '/api/auth/cancel') {
                    auth.cancelAuth();
                    currentAuthState = { status: 'idle', timestamp: Date.now() };
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify({ status: 'cancelled' }));
                }

                // 6. Offline Authentication (authentication.php / auth.php / ot token)
                if (pathname.includes('authentication.php') || pathname.includes('auth.php') || pathname.startsWith('/auth')) {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify(generateOfflineAuthResponse(bodyStr)));
                }

                // 6. Mock Skin / Cape / Cosmetic responses
                if (pathname.startsWith('/skins/') || pathname.startsWith('/capes/') || pathname.startsWith('/cosmetics/')) {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify({ exists: false, offline: true }));
                }

                // 7. Mock Session / Telemetry / Metrics JSON responses
                if (pathname.endsWith('.json') || pathname.startsWith('/api/') || pathname.startsWith('/session') || pathname.includes('metrics')) {
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    return res.end(JSON.stringify({ status: 'ok', offline: true, authenticated: true }));
                }

                // 8. Safe Catch-All 200 JSON
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ status: 'ok', offline: true }));
            });
        });

        // Listen on localhost port
        server.listen(0, '127.0.0.1', () => {
            activePort = server.address().port;
            serverInstance = server;
            console.log(`[LocalServer] Pure offline asset server running on http://127.0.0.1:${activePort}`);
            resolve(`http://127.0.0.1:${activePort}`);
        });

        server.on('error', (err) => {
            console.warn('[LocalServer] Server error:', err.message);
            resolve(null);
        });
    });
}

/**
 * Stop local HTTP server instance.
 */
function stopServer() {
    if (serverInstance) {
        try {
            serverInstance.close();
        } catch (e) {
            // Ignore
        }
        serverInstance = null;
        activePort = null;
    }
}

/**
 * Get active server URL if running.
 */
function getServerUrl() {
    return activePort ? `http://127.0.0.1:${activePort}` : null;
}

module.exports = {
    startServer,
    stopServer,
    getServerUrl
};
