const { Authflow, Titles } = require('prismarine-auth');
const path = require('path');
const fs = require('fs');
const { ensureCCStructure, formatUuid } = require('./accounts');

const AUTH_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
let currentAuthController = null;

let timeCorrectionMs = 0;
try {
    const XboxTokenManager = require('prismarine-auth/src/TokenManagers/XboxTokenManager');
    if (XboxTokenManager && XboxTokenManager.prototype && XboxTokenManager.prototype.sign) {
        const origSign = XboxTokenManager.prototype.sign;
        XboxTokenManager.prototype.sign = function (url, authorizationToken, payload) {
            const origNow = Date.now;
            try {
                if (timeCorrectionMs !== 0) {
                    Date.now = () => origNow() + timeCorrectionMs;
                }
                return origSign.call(this, url, authorizationToken, payload);
            } finally {
                Date.now = origNow;
            }
        };
    }
} catch (e) {
    console.warn('[Auth] Note: XboxTokenManager hook bypassed:', e.message);
}

async function syncClockWithServer() {
    try {
        const https = require('https');
        return await new Promise((resolve) => {
            const req = https.request('https://login.live.com', { method: 'HEAD', timeout: 4000 }, (res) => {
                const dateHeader = res.headers['date'];
                if (dateHeader) {
                    const serverTime = Date.parse(dateHeader);
                    if (!isNaN(serverTime)) {
                        timeCorrectionMs = serverTime - Date.now();
                        if (Math.abs(timeCorrectionMs) > 1000) {
                            console.log(`[Auth] Detected PC clock drift: ${timeCorrectionMs}ms. Applying automatic time correction.`);
                        }
                    }
                }
                resolve();
            });
            req.on('error', () => resolve());
            req.on('timeout', () => { req.destroy(); resolve(); });
            req.end();
        });
    } catch (e) {
        // Ignore
    }
}

/**
 * Start Microsoft OAuth device code flow.
 */
async function loginMicrosoft(installDir, onCode) {
    cancelAuth();

    const controller = { cancelled: false };
    currentAuthController = controller;

    await syncClockWithServer();

    const cacheDir = path.join(installDir, 'auth_cache');
    if (!fs.existsSync(cacheDir)) {
        fs.mkdirSync(cacheDir, { recursive: true });
    }
    clearCacheDir(cacheDir);

    try {
        console.log('[Auth] Starting Microsoft device code flow...');

        const javaFlow = new Authflow('cosmic-launcher', cacheDir, {
            flow: 'live',
            authTitle: Titles.MinecraftJava,
            deviceType: 'Win32'
        }, (code) => {
            console.log('[Auth] Device code received:', code.user_code);
            onCode({
                userCode: code.user_code,
                verificationUri: code.verification_uri,
                directUrl: code.message ? code.message.match(/http\S+otc=\S+/)?.[0] : `https://www.microsoft.com/link?otc=${code.user_code}`
            });
        });

        let javaResult = null;
        try {
            javaResult = await javaFlow.getMinecraftJavaToken({ fetchProfile: true });
        } catch (err) {
            if (controller.cancelled) {
                throw new Error('Authentication was cancelled.');
            }
            console.log('[Auth] Java flow notice, attempting alternative authentication flow:', err.message);
        }

        const javaRefreshToken = readRefreshTokenFromCache(cacheDir);

        if (javaResult && javaResult.profile) {
            cleanupController(controller);
            currentAuthController = null;

            const accountCacheDir = path.join(installDir, 'auth_cache', 'accounts', formatUuid(javaResult.profile.id));
            saveCacheForAccount(cacheDir, accountCacheDir, 'MinecraftJava');

            const { accountId } = saveAccount(installDir, javaResult.profile, javaResult.token, javaRefreshToken);
            return {
                success: true,
                accountId,
                displayName: javaResult.profile.name,
                token: javaResult.token,
                profile: javaResult.profile
            };
        }

        // Fallback to Sisu flow with onCode callback
        if (controller.cancelled) {
            throw new Error('Authentication was cancelled.');
        }

        renameCacheForFlow(cacheDir, 'live', 'sisu');

        const sisuFlow = new Authflow('cosmic-launcher', cacheDir, {
            flow: 'sisu',
            authTitle: Titles.MinecraftJava,
            deviceType: 'Win32'
        }, (code) => {
            console.log('[Auth] Sisu Device code received:', code.user_code);
            onCode({
                userCode: code.user_code,
                verificationUri: code.verification_uri,
                directUrl: code.message ? code.message.match(/http\S+otc=\S+/)?.[0] : `https://www.microsoft.com/link?otc=${code.user_code}`
            });
        });

        const sisuResult = await sisuFlow.getMinecraftJavaToken({ fetchProfile: true });

        if (!sisuResult || !sisuResult.profile) {
            throw new Error('Failed to retrieve Minecraft profile from Microsoft.');
        }

        cleanupController(controller);
        currentAuthController = null;

        const accountCacheDir = path.join(installDir, 'auth_cache', 'accounts', formatUuid(sisuResult.profile.id));
        saveCacheForAccount(cacheDir, accountCacheDir, 'MinecraftJava-sisu');

        const { accountId } = saveAccount(installDir, sisuResult.profile, sisuResult.token, javaRefreshToken);

        return {
            success: true,
            accountId,
            displayName: sisuResult.profile.name,
            token: sisuResult.token,
            profile: sisuResult.profile
        };

    } catch (err) {
        cleanupController(controller);
        currentAuthController = null;
        console.error('[Auth] Login error:', err.message);
        return { success: false, error: err.message };
    }
}

function cancelAuth() {
    if (currentAuthController) {
        currentAuthController.cancelled = true;
        cleanupController(currentAuthController);
        currentAuthController = null;
        console.log('[Auth] Auth operation cancelled');
    }
}

function cleanupController(controller) {
    if (controller.timer) clearTimeout(controller.timer);
    if (controller.cancelInterval) clearInterval(controller.cancelInterval);
    if (controller.sisuTimer) clearTimeout(controller.sisuTimer);
}

function clearCacheDir(cacheDir) {
    try {
        const files = fs.readdirSync(cacheDir).filter(f => f.endsWith('.json'));
        for (const file of files) {
            fs.unlinkSync(path.join(cacheDir, file));
        }
    } catch {}
}

function renameCacheForFlow(cacheDir, fromFlow, toFlow) {
    try {
        const files = fs.readdirSync(cacheDir).filter(f => f.includes(`_${fromFlow}-cache.json`));
        for (const file of files) {
            const newName = file.replace(`_${fromFlow}-cache.json`, `_${toFlow}-cache.json`);
            fs.copyFileSync(path.join(cacheDir, file), path.join(cacheDir, newName));
        }
    } catch {}
}

async function authWithTimeoutAndCancel(flow, controller) {
    const result = await Promise.race([
        flow.getMinecraftJavaToken({ fetchProfile: true }),
        new Promise((_, reject) => {
            controller.timer = setTimeout(() => reject(new Error('Authentication timed out')), AUTH_TIMEOUT_MS);
        }),
        new Promise((_, reject) => {
            controller.cancelInterval = setInterval(() => {
                if (controller.cancelled) {
                    clearInterval(controller.cancelInterval);
                    reject(new Error('Authentication was cancelled.'));
                }
            }, 250);
        })
    ]);

    cleanupController(controller);
    if (controller.cancelled) {
        throw new Error('Authentication was cancelled.');
    }
    return result;
}

function saveCacheForAccount(srcDir, dstDir, authConfigName) {
    try {
        fs.mkdirSync(dstDir, { recursive: true });
        const files = fs.readdirSync(srcDir).filter(f => f.endsWith('.json'));
        for (const file of files) {
            fs.copyFileSync(path.join(srcDir, file), path.join(dstDir, file));
        }
        fs.writeFileSync(path.join(dstDir, '_meta.json'), JSON.stringify({
            authConfig: authConfigName,
            savedAt: new Date().toISOString()
        }), 'utf-8');
    } catch (err) {
        console.error('[Auth] Failed to save account cache:', err.message);
    }
}

function readRefreshTokenFromCache(cacheDir) {
    try {
        const files = fs.readdirSync(cacheDir).filter(f => f.endsWith('.json'));
        for (const file of files) {
            const raw = fs.readFileSync(path.join(cacheDir, file), 'utf-8');
            const data = JSON.parse(raw);
            if (data.token && data.token.refresh_token) {
                return data.token.refresh_token;
            }
            if (data.RefreshToken) {
                const entries = Object.values(data.RefreshToken);
                if (entries.length > 0 && entries[0].secret) {
                    return entries[0].secret;
                }
            }
        }
    } catch {}
    return null;
}

/**
 * Save account into accounts.json.
 */
function saveAccount(installDir, profile, accessToken, refreshToken) {
    const accountsPath = path.join(installDir, 'accounts.json');
    const crypto = require('crypto');

    let data = {};
    try {
        data = JSON.parse(fs.readFileSync(accountsPath, 'utf-8'));
    } catch (e) {
        data = {};
    }

    ensureCCStructure(data);

    const uuid = formatUuid(profile.id);

    let accountId = null;
    const toDelete = [];
    for (const [id, entry] of Object.entries(data.authenticationDatabase)) {
        const profiles = entry.profiles || {};
        let matched = false;
        for (const pid of Object.keys(profiles)) {
            if (formatUuid(pid) === uuid) {
                matched = true;
                break;
            }
        }
        if (!matched && entry.username && entry.username.toLowerCase() === profile.name.toLowerCase()) {
            matched = true;
        }
        if (matched) {
            if (!accountId) {
                accountId = id;
            } else {
                toDelete.push(id);
            }
        }
    }

    for (const dId of toDelete) {
        delete data.authenticationDatabase[dId];
    }

    if (!accountId) {
        accountId = crypto.randomUUID ? crypto.randomUUID() : formatUuid(crypto.randomBytes(16).toString('hex'));
    }

    data.authenticationDatabase[accountId] = {
        username: profile.name,
        profiles: {
            [uuid]: {
                displayName: profile.name
            }
        },
        refreshToken: refreshToken || '',
        type: 'Xbox',
        accessToken: accessToken
    };

    data.selectedUser = {
        account: accountId,
        profile: uuid
    };

    fs.writeFileSync(accountsPath, JSON.stringify(data, null, 2), 'utf-8');

    // Also sync to other known candidate paths
    const otherPaths = [
        path.join(process.cwd(), 'accounts.json'),
        path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'accounts.json'),
        path.join(process.env.USERPROFILE || '', '.cosmicclient', 'accounts.json')
    ];
    for (const p of otherPaths) {
        try {
            if (p && p !== accountsPath) {
                const dir = path.dirname(p);
                if (fs.existsSync(dir)) {
                    fs.writeFileSync(p, JSON.stringify(data, null, 2), 'utf-8');
                }
            }
        } catch {}
    }

    return { accountId, profileId: uuid };
}

/**
 * Refresh Minecraft access token using saved auth cache.
 */
async function refreshAccount(installDir, profileId) {
    const uuid = formatUuid(profileId);
    const accountCacheDir = path.join(installDir, 'auth_cache', 'accounts', uuid);
    const metaPath = path.join(accountCacheDir, '_meta.json');

    if (!fs.existsSync(metaPath)) {
        return null;
    }

    try {
        const meta = JSON.parse(fs.readFileSync(metaPath, 'utf-8'));
        const configName = meta.authConfig || 'MinecraftJava-sisu';
        const flow = configName === 'MinecraftJava' ? 'live' : 'sisu';

        const authflow = new Authflow('cosmic-launcher', accountCacheDir, {
            flow,
            authTitle: Titles.MinecraftJava,
            deviceType: 'Win32'
        }, () => {
            // Strictly silent: do not trigger interactive device code or open browser on launch
        });

        const result = await Promise.race([
            authflow.getMinecraftJavaToken({ fetchProfile: true }),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Token refresh timeout')), 20000))
        ]);

        const refreshToken = readRefreshTokenFromCache(accountCacheDir);
        return {
            token: result.token,
            refreshToken: refreshToken,
            profile: result.profile
        };
    } catch (err) {
        console.warn('[Auth] Token refresh skipped or failed:', err.message);
        return null;
    }
}

module.exports = {
    loginMicrosoft,
    cancelAuth,
    refreshAccount,
    saveAccount
};
