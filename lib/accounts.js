const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

/**
 * Format string as standard 8-4-4-4-12 UUID.
 */
function formatUuid(input) {
    if (!input) return generateUuid();
    const clean = input.replace(/-/g, '').toLowerCase();
    if (clean.length !== 32) return input;
    return `${clean.slice(0, 8)}-${clean.slice(8, 12)}-${clean.slice(12, 16)}-${clean.slice(16, 20)}-${clean.slice(20)}`;
}

/**
 * Generate a random UUID v4 string.
 */
function generateUuid() {
    const bytes = crypto.randomBytes(16);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = bytes.toString('hex');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Generate offline UUID based on username (similar to Minecraft offline player UUID).
 */
function generateOfflineUuid(username) {
    const hash = crypto.createHash('md5').update(`OfflinePlayer:${username}`).digest('hex');
    return `${hash.slice(0, 8)}-${hash.slice(8, 12)}-3${hash.slice(13, 16)}-${((parseInt(hash.slice(16, 18), 16) & 0x3f) | 0x80).toString(16)}${hash.slice(18, 20)}-${hash.slice(20, 32)}`;
}

/**
 * Ensure the Cosmic Client accounts.json data structure conforms to client expectations.
 */
function ensureCCStructure(data) {
    if (!data.profiles) {
        data.profiles = {
            [crypto.randomBytes(16).toString('hex')]: {
                created: '1970-01-01T00:00:00.000Z',
                icon: 'Dirt',
                lastUsed: '1970-01-01T00:00:00.000Z',
                lastVersionId: 'latest-snapshot',
                name: '',
                type: 'latest-snapshot'
            },
            [crypto.randomBytes(16).toString('hex')]: {
                created: '1970-01-02T00:00:00.000Z',
                icon: 'Grass',
                lastUsed: '1970-01-02T00:00:00.000Z',
                lastVersionId: 'latest-release',
                name: '',
                type: 'latest-release'
            }
        };
    }
    if (!data.settings) {
        data.settings = {
            crashAssistance: false,
            enableAdvanced: false,
            enableAnalytics: true,
            enableHistorical: false,
            enableReleases: true,
            enableSnapshots: false,
            keepLauncherOpen: false,
            profileSorting: 'ByLastPlayed',
            showGameLog: false,
            showMenu: false,
            soundOn: false
        };
    }
    if (data.version === undefined) {
        data.version = 4;
    }
    if (!data.authenticationDatabase) {
        data.authenticationDatabase = {};
    }
    if (!data.clientToken) {
        data.clientToken = generateUuid();
    }
    if (!data.selectedUser) {
        data.selectedUser = {};
    }
}

/**
 * Get all candidate accounts.json paths across installDir, cwd, and AppData.
 */
function getAllAccountFilePaths(installDir) {
    const isWin = process.platform === 'win32';
    const appData = process.env.APPDATA || '';
    const userCosmic = isWin
        ? path.join(appData, '.minecraft', 'cosmic')
        : path.join(require('os').homedir(), '.minecraft', 'cosmic');

    const userMc = isWin
        ? path.join(appData, '.minecraft')
        : path.join(require('os').homedir(), '.minecraft');

    const candidates = [
        installDir ? path.join(installDir, 'accounts.json') : null,
        installDir ? path.join(installDir, 'cosmic', 'accounts.json') : null,
        path.join(process.cwd(), 'accounts.json'),
        path.join(process.cwd(), 'cosmic', 'accounts.json'),
        path.join(process.cwd(), 'CosmicClient-x64', 'accounts.json'),
        path.join(userCosmic, 'accounts.json'),
        path.join(userCosmic, 'cosmic', 'accounts.json')
    ].filter(Boolean);

    return [...new Set(candidates)];
}

/**
 * Read all accounts from accounts.json, merging across all known paths and auto-importing system accounts.
 */
function getAccounts(installDir) {
    try {
        const candidatePaths = getAllAccountFilePaths(installDir);
        let masterData = {
            profiles: {},
            settings: {},
            version: 4,
            authenticationDatabase: {},
            clientToken: '',
            selectedUser: {}
        };
        let foundAnyFile = false;

        for (const p of candidatePaths) {
            if (fs.existsSync(p)) {
                try {
                    const raw = fs.readFileSync(p, 'utf-8');
                    const parsed = JSON.parse(raw);
                    if (parsed && typeof parsed === 'object') {
                        foundAnyFile = true;
                        if (parsed.authenticationDatabase && typeof parsed.authenticationDatabase === 'object') {
                            for (const [id, entry] of Object.entries(parsed.authenticationDatabase)) {
                                if (id !== 'cosmic-offline-player' && entry && (entry.username || (entry.profiles && Object.keys(entry.profiles).length > 0))) {
                                    const existing = masterData.authenticationDatabase[id];
                                    const entryToken = entry.accessToken || '';
                                    const entryHasJwt = entryToken.length > 50 && entryToken.startsWith('eyJ');

                                    if (existing) {
                                        const existingToken = existing.accessToken || '';
                                        const existingHasJwt = existingToken.length > 50 && existingToken.startsWith('eyJ');

                                        if (existingHasJwt && !entryHasJwt) {
                                            if (!existing.refreshToken && entry.refreshToken) {
                                                existing.refreshToken = entry.refreshToken;
                                            }
                                            continue;
                                        }
                                        if (entryHasJwt && !existingHasJwt) {
                                            masterData.authenticationDatabase[id] = entry;
                                            continue;
                                        }
                                    } else {
                                        // Check if another key already exists for this username or UUID
                                        let foundExistingId = null;
                                        for (const [eId, eEntry] of Object.entries(masterData.authenticationDatabase)) {
                                            const matchUser = eEntry.username && entry.username && eEntry.username.toLowerCase() === entry.username.toLowerCase();
                                            const matchProfile = eEntry.profiles && entry.profiles && Object.keys(eEntry.profiles).some(k => Object.keys(entry.profiles).includes(k));
                                            if (matchUser || matchProfile) {
                                                foundExistingId = eId;
                                                break;
                                            }
                                        }
                                        if (foundExistingId) {
                                            const existing = masterData.authenticationDatabase[foundExistingId];
                                            const existingToken = existing.accessToken || '';
                                            const existingHasJwt = existingToken.length > 50 && existingToken.startsWith('eyJ');

                                            if (existingHasJwt && !entryHasJwt) {
                                                if (!existing.refreshToken && entry.refreshToken) {
                                                    existing.refreshToken = entry.refreshToken;
                                                }
                                                continue;
                                            }
                                            if (entryHasJwt && !existingHasJwt) {
                                                delete masterData.authenticationDatabase[foundExistingId];
                                                masterData.authenticationDatabase[id] = entry;
                                                continue;
                                            }
                                        }
                                    }

                                    masterData.authenticationDatabase[id] = entry;
                                }
                            }
                        }
                        if (parsed.selectedUser && parsed.selectedUser.account && !masterData.selectedUser.account) {
                            masterData.selectedUser = parsed.selectedUser;
                        }
                        if (parsed.clientToken && !masterData.clientToken) {
                            masterData.clientToken = parsed.clientToken;
                        }
                    }
                } catch (e) {
                    console.warn('[Accounts] Warning reading candidate path ' + p + ':', e.message);
                }
            }
        }

        // If no real accounts found, attempt auto-importing Microsoft store accounts
        if (Object.keys(masterData.authenticationDatabase).length === 0) {
            importSystemAccounts(installDir);
            for (const p of candidatePaths) {
                if (fs.existsSync(p)) {
                    try {
                        const raw = fs.readFileSync(p, 'utf-8');
                        const parsed = JSON.parse(raw);
                        if (parsed && parsed.authenticationDatabase) {
                            for (const [id, entry] of Object.entries(parsed.authenticationDatabase)) {
                                if (id !== 'cosmic-offline-player') {
                                    masterData.authenticationDatabase[id] = entry;
                                }
                            }
                        }
                    } catch (e) {}
                }
            }
        }

        ensureCCStructure(masterData);

        const authDb = masterData.authenticationDatabase || {};
        const accounts = [];
        const seenProfileIds = new Set();
        const seenDisplayNames = new Set();
        const duplicateIdsToDelete = [];

        for (const [accountId, entry] of Object.entries(authDb)) {
            const profiles = entry.profiles || {};
            const profileEntries = Object.entries(profiles);
            const rawProfileId = profileEntries.length > 0 ? profileEntries[0][0] : accountId;
            const profileId = formatUuid(rawProfileId);
            const displayName = profileEntries.length > 0 ? profileEntries[0][1].displayName : entry.username;
            const normUser = (displayName || entry.username || '').toLowerCase();
            const normPid = profileId.toLowerCase();
            const isMs = (entry.type || '').toLowerCase() === 'xbox' || (entry.type || '').toLowerCase() === 'microsoft';

            // Prevent duplicate Microsoft accounts by UUID or username
            if (isMs) {
                if (seenProfileIds.has(normPid) || seenDisplayNames.has(normUser)) {
                    duplicateIdsToDelete.push(accountId);
                    const existingAcc = accounts.find(a => a.profileId.toLowerCase() === normPid || a.displayName.toLowerCase() === normUser);
                    if (existingAcc) {
                        if ((!existingAcc.accessToken || existingAcc.accessToken.length <= 50) && (entry.accessToken && entry.accessToken.length > 50)) {
                            existingAcc.accessToken = entry.accessToken;
                        }
                        if (!existingAcc.refreshToken && entry.refreshToken) {
                            existingAcc.refreshToken = entry.refreshToken;
                        }
                    }
                    continue;
                }
                seenProfileIds.add(normPid);
                seenDisplayNames.add(normUser);
            }

            accounts.push({
                accountId,
                profileId,
                username: entry.username || displayName,
                displayName: displayName || entry.username,
                accessToken: entry.accessToken || 'offline',
                refreshToken: entry.refreshToken || '',
                type: entry.type || 'Offline',
                isOffline: (entry.type || '').toLowerCase() === 'offline' || !entry.accessToken || entry.accessToken === 'null',
                selected: accountId === masterData.selectedUser?.account
            });
        }

        // Remove duplicate entries from authenticationDatabase
        for (const dupId of duplicateIdsToDelete) {
            delete masterData.authenticationDatabase[dupId];
        }

        let selected = accounts.find(a => a.selected);
        if (!selected && accounts.length > 0) {
            selected = accounts[0];
            selected.selected = true;
            masterData.selectedUser = {
                account: selected.accountId,
                profile: selected.profileId
            };
        }

        if (accounts.length === 0) {
            const defaultAcc = {
                accountId: 'cosmic-offline-player',
                profileId: '00000000-0000-0000-0000-000000000000',
                username: 'CosmicPlayer',
                displayName: 'CosmicPlayer',
                accessToken: 'offline',
                refreshToken: '',
                type: 'Offline',
                isOffline: true,
                selected: true
            };
            return { accounts: [defaultAcc], selected: defaultAcc };
        }

        // Sync master accounts data back across all candidate paths
        writeAccountsData(installDir, masterData);

        return { accounts, selected };
    } catch (err) {
        console.error('[Accounts] Error reading accounts:', err.message);
        return { accounts: [], selected: null };
    }
}

/**
 * Auto-import Microsoft accounts from the official Minecraft launcher when requested.
 */
function importSystemAccounts(installDir) {
    const isWin = process.platform === 'win32';
    const mcDir = isWin
        ? path.join(process.env.APPDATA || '', '.minecraft')
        : path.join(require('os').homedir(), process.platform === 'darwin' ? 'Library/Application Support/minecraft' : '.minecraft');

    const storePath = path.join(mcDir, 'launcher_accounts_microsoft_store.json');
    const legacyPath = path.join(mcDir, 'launcher_accounts.json');

    const candidateFiles = [storePath, legacyPath];
    let foundAccounts = [];

    for (const f of candidateFiles) {
        if (fs.existsSync(f)) {
            try {
                const parsed = JSON.parse(fs.readFileSync(f, 'utf-8'));
                for (const [id, acc] of Object.entries(parsed.accounts || {})) {
                    if (acc.minecraftProfile && acc.minecraftProfile.name) {
                        foundAccounts.push({
                            id: acc.minecraftProfile.id || id,
                            name: acc.minecraftProfile.name,
                            type: acc.type === 'Xbox' || acc.type === 'MSA' ? 'Xbox' : 'Offline',
                            accessToken: acc.accessToken || ''
                        });
                    }
                }
            } catch (e) {}
        }
    }

    if (foundAccounts.length === 0) return false;

    const allPaths = getAllAccountFilePaths(installDir);
    let data = {};
    for (const p of allPaths) {
        if (fs.existsSync(p)) {
            try {
                data = JSON.parse(fs.readFileSync(p, 'utf-8'));
                if (data.authenticationDatabase) break;
            } catch (e) {}
        }
    }
    ensureCCStructure(data);

    let changed = false;
    let preferredAccount = null;

    for (const sysAcc of foundAccounts) {
        const uuid = formatUuid(sysAcc.id);
        let exists = false;

        for (const [accId, entry] of Object.entries(data.authenticationDatabase)) {
            if (entry.username === sysAcc.name || (entry.profiles && entry.profiles[uuid])) {
                exists = true;
                if (!preferredAccount) preferredAccount = { accountId: accId, profileId: uuid };
                break;
            }
        }

        if (!exists) {
            let existingToken = sysAcc.accessToken;
            let existingRefresh = '';
            for (const p of allPaths) {
                if (fs.existsSync(p)) {
                    try {
                        const checkData = JSON.parse(fs.readFileSync(p, 'utf-8'));
                        if (checkData.authenticationDatabase) {
                            for (const cEntry of Object.values(checkData.authenticationDatabase)) {
                                const mUser = cEntry.username && cEntry.username.toLowerCase() === sysAcc.name.toLowerCase();
                                const mUuid = cEntry.profiles && Object.keys(cEntry.profiles).some(k => formatUuid(k) === uuid);
                                if (mUser || mUuid) {
                                    if (cEntry.accessToken && cEntry.accessToken.length > 50 && cEntry.accessToken.startsWith('eyJ')) {
                                        existingToken = cEntry.accessToken;
                                    }
                                    if (cEntry.refreshToken) existingRefresh = cEntry.refreshToken;
                                }
                            }
                        }
                    } catch (e) {}
                }
            }

            const accId = generateUuid();
            data.authenticationDatabase[accId] = {
                username: sysAcc.name,
                profiles: {
                    [uuid]: {
                        displayName: sysAcc.name
                    }
                },
                refreshToken: existingRefresh || '',
                type: sysAcc.type,
                accessToken: existingToken || 'microsoft_imported_token'
            };
            changed = true;
            if (!preferredAccount) preferredAccount = { accountId: accId, profileId: uuid };
        }
    }

    if (preferredAccount && !data.selectedUser?.account) {
        data.selectedUser = {
            account: preferredAccount.accountId,
            profile: preferredAccount.profileId
        };
        changed = true;
    }

    if (changed) {
        writeAccountsData(installDir, data);
        console.log('[Accounts] Successfully imported and synchronized system accounts.');
    }

    return true;
}

/**
 * Helper to write accounts data across all candidate accounts.json paths.
 */
function writeAccountsData(installDir, data) {
    const candidatePaths = getAllAccountFilePaths(installDir);
    const serialized = JSON.stringify(data, null, 2);

    for (const p of candidatePaths) {
        try {
            const parent = path.dirname(p);
            if (!fs.existsSync(parent)) {
                fs.mkdirSync(parent, { recursive: true });
            }
            fs.writeFileSync(p, serialized, 'utf-8');
        } catch (e) {
            console.error('[Accounts] Write error at ' + p + ':', e.message);
        }
    }
}

/**
 * Add or update an Offline/Custom-name account.
 */
function addOfflineAccount(installDir, username) {
    const accountsPath = path.join(installDir, 'accounts.json');

    try {
        let data = {};
        if (fs.existsSync(accountsPath)) {
            try {
                data = JSON.parse(fs.readFileSync(accountsPath, 'utf-8'));
            } catch (e) {
                data = {};
            }
        }

        ensureCCStructure(data);

        const cleanUsername = username.trim();
        const uuid = generateOfflineUuid(cleanUsername);
        const accountId = generateUuid();

        // Check if an account with this username already exists
        let existingId = null;
        for (const [id, acc] of Object.entries(data.authenticationDatabase)) {
            if (acc.username && acc.username.toLowerCase() === cleanUsername.toLowerCase()) {
                existingId = id;
                break;
            }
        }

        const targetId = existingId || accountId;

        data.authenticationDatabase[targetId] = {
            username: cleanUsername,
            profiles: {
                [uuid]: {
                    displayName: cleanUsername
                }
            },
            refreshToken: '',
            type: 'Offline',
            accessToken: 'offline_token_' + Buffer.from(cleanUsername).toString('hex')
        };

        // Select the newly added account
        data.selectedUser = {
            account: targetId,
            profile: uuid
        };

        writeAccountsData(installDir, data);
        return { success: true, accountId: targetId, profileId: uuid, displayName: cleanUsername };
    } catch (err) {
        console.error('[Accounts] Failed to add offline account:', err.message);
        return { success: false, error: err.message };
    }
}

/**
 * Remove an account by accountId.
 */
function removeAccount(installDir, accountId) {
    const accountsPath = path.join(installDir, 'accounts.json');

    try {
        if (!fs.existsSync(accountsPath)) return { success: true };
        const raw = fs.readFileSync(accountsPath, 'utf-8');
        const data = JSON.parse(raw);

        if (data.authenticationDatabase && data.authenticationDatabase[accountId]) {
            delete data.authenticationDatabase[accountId];

            if (data.selectedUser && data.selectedUser.account === accountId) {
                const remaining = Object.keys(data.authenticationDatabase);
                if (remaining.length > 0) {
                    const firstId = remaining[0];
                    const profiles = data.authenticationDatabase[firstId].profiles || {};
                    const firstProfileId = Object.keys(profiles)[0] || firstId;
                    data.selectedUser = { account: firstId, profile: firstProfileId };
                } else {
                    data.selectedUser = {};
                }
            }

            writeAccountsData(installDir, data);
        }

        return { success: true };
    } catch (err) {
        console.error('[Accounts] Failed to remove account:', err.message);
        return { success: false, error: err.message };
    }
}

/**
 * Select the active account.
 */
function selectAccount(installDir, accountId) {
    const accountsPath = path.join(installDir, 'accounts.json');

    try {
        if (!fs.existsSync(accountsPath)) return false;
        const raw = fs.readFileSync(accountsPath, 'utf-8');
        const data = JSON.parse(raw);

        const entry = data.authenticationDatabase?.[accountId];
        if (!entry) return false;

        const profileId = Object.keys(entry.profiles || {})[0] || accountId;
        data.selectedUser = {
            account: accountId,
            profile: profileId
        };

        writeAccountsData(installDir, data);
        return true;
    } catch (err) {
        console.error('[Accounts] Failed to select account:', err.message);
        return false;
    }
}

module.exports = {
    getAccounts,
    importSystemAccounts,
    addOfflineAccount,
    removeAccount,
    selectAccount,
    ensureCCStructure,
    formatUuid,
    generateUuid,
    generateOfflineUuid
};
