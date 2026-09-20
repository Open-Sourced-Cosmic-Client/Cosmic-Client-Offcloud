// ==========================================================================
// COSMIC CLIENT X64 - FRONTEND APPLICATION CONTROLLER
// ==========================================================================

// Application State
let appState = {
    installInfo: null,
    accounts: [],
    selectedAccount: null,
    settings: {},
    jvmPresets: [],
    isLaunching: false,
    isRunning: false,
    selectedVersionId: '1.8.9',
    selectedServer: '',
    logEntries: [],
    activeFilter: 'all'
};

// ================= DOM ELEMENTS =================
const titlebarMin = document.getElementById('btn-minimize');
const titlebarMax = document.getElementById('btn-maximize');
const titlebarClose = document.getElementById('btn-close');
const statusPill = document.getElementById('status-pill');
const statusText = document.getElementById('status-text');

// Tabs
const navItems = document.querySelectorAll('.nav-item');
const tabViews = document.querySelectorAll('.tab-view');

// Launch View
const versionCardsContainer = document.getElementById('version-cards-container');
const serverChips = document.querySelectorAll('.server-chip');
const customServerInput = document.getElementById('custom-server-input');
const deckAccountSelect = document.getElementById('deck-account-select');
const deckAvatarImg = document.getElementById('deck-avatar-img');
const deckRamText = document.getElementById('deck-ram-text');
const btnPlay = document.getElementById('btn-play');
const playBtnText = document.getElementById('play-btn-text');
const playBtnSpinner = document.getElementById('play-btn-spinner');
const btnQuickAddAccount = document.getElementById('btn-quick-add-account');

// Launch Progress Deck Elements
const launchProgressDeck = document.getElementById('launch-progress-deck');
const progressStatusTitle = document.getElementById('progress-status-title');
const progressPercentBadge = document.getElementById('progress-percent-badge');
const progressBarFill = document.getElementById('progress-bar-fill');
const progressDetailSubtitle = document.getElementById('progress-detail-subtitle');
const progressJumpConsole = document.getElementById('progress-jump-console');

// PC Resources Deck Elements
const btnOpenPcRp = document.getElementById('btn-open-pc-rp');
const btnSyncPcResources = document.getElementById('btn-sync-pc-resources');
const btnImportPcOptions = document.getElementById('btn-import-pc-options');
const btnSyncPcConfigs = document.getElementById('btn-sync-pc-configs');
const pcResPacksCount = document.getElementById('pc-res-packs-count');
const pcResAssetsStatus = document.getElementById('pc-res-assets-status');
const pcResDesc = document.getElementById('pc-res-desc');

// Accounts View
const accountsListContainer = document.getElementById('accounts-list-container');
const btnOpenMsModal = document.getElementById('btn-open-ms-modal');
const btnOpenOfflineModal = document.getElementById('btn-open-offline-modal');

// Settings View
const sliderMinRam = document.getElementById('slider-min-ram');
const sliderMaxRam = document.getElementById('slider-max-ram');
const labelMinRam = document.getElementById('label-min-ram');
const labelMaxRam = document.getElementById('label-max-ram');
const settingWidth = document.getElementById('setting-width');
const settingHeight = document.getElementById('setting-height');
const settingFullscreen = document.getElementById('setting-fullscreen');
const settingJvmPreset = document.getElementById('setting-jvm-preset');
const jvmPresetDesc = document.getElementById('jvm-preset-desc');
const settingCustomJvmArgs = document.getElementById('setting-custom-jvm-args');
const settingJavaPath = document.getElementById('setting-java-path');
const btnBrowseJava = document.getElementById('btn-browse-java');
const detectedJavaText = document.getElementById('detected-java-text');
const settingUseAgent = document.getElementById('setting-use-agent');
const btnSaveSettings = document.getElementById('btn-save-settings');
const resPresetButtons = document.querySelectorAll('.preset-btn');

// Console View
const terminalContent = document.getElementById('terminal-content');
const terminalBox = document.getElementById('terminal-box');
const filterChips = document.querySelectorAll('.filter-chip');
const consoleSearch = document.getElementById('console-search');
const btnClearConsole = document.getElementById('btn-clear-console');
const btnCopyConsole = document.getElementById('btn-copy-console');
const btnExportConsole = document.getElementById('btn-export-console');
const btnKillGame = document.getElementById('btn-kill-game');

// Modals
const modalMsLogin = document.getElementById('modal-ms-login');
const modalOfflineLogin = document.getElementById('modal-offline-login');
const btnStartMsAuth = document.getElementById('btn-start-ms-auth');
const msStepInit = document.getElementById('ms-step-init');
const msStepCode = document.getElementById('ms-step-code');
const msStepDone = document.getElementById('ms-step-done');
const msStepError = document.getElementById('ms-step-error');
const msUserCode = document.getElementById('ms-user-code');
const msAuthUrl = document.getElementById('ms-auth-url');
const btnCopyCode = document.getElementById('btn-copy-code');
const msSuccessName = document.getElementById('ms-success-name');
const msErrorMsg = document.getElementById('ms-error-msg');
const offlineUsernameInput = document.getElementById('offline-username-input');
const btnSubmitOfflineAccount = document.getElementById('btn-submit-offline-account');

// Footer
const footerVersionTag = document.getElementById('footer-version-tag');
const footerAccountTag = document.getElementById('footer-account-tag');

// ================= INITIALIZATION =================
async function init() {
    initNebulaCanvas();
    setupWindowControls();
    setupTabNavigation();
    setupModals();
    setupEventListeners();

    appendLog('[Launcher] Initializing Cosmic Client Offcloud Launcher...', 'launcher');

    try {
        // Load installation detection
        appState.installInfo = await window.cosmicAPI.detectInstall();
        detectedJavaText.textContent = appState.installInfo.javaBin || 'System Default';

        // Load JVM Presets
        appState.jvmPresets = await window.cosmicAPI.getJvmPresets();
        populateJvmPresets();

        // Load Settings
        appState.settings = await window.cosmicAPI.getSettings();
        populateSettings();

        // Render Version Cards
        renderVersionCards();

        // Load Accounts
        await refreshAccounts();

        // Update Folder Statistics
        await updateFolderStats();

        // Refresh PC Resources & Texture Packs Bridge
        await refreshPCResources();

        // Automatically sync old Cosmic Client profiles, HUD layout & schematics if available
        try {
            const syncRes = await window.cosmicAPI.syncOldConfigs(false);
            if (syncRes && syncRes.syncedItems > 0) {
                appendLog(`[ResourceBridge] Automatically synchronized ${syncRes.syncedItems} old Cosmic Client profiles & schematics.`, 'launcher');
            }
        } catch (e) {}

        appendLog('[Launcher] Ready. Built for Cosmonauts with Java Agent & Off-Cloud Engine.', 'launcher');
        setStatus('ready', 'OFF-CLOUD READY');
    } catch (err) {
        appendLog('[Launcher] Initialization Error: ' + err.message, 'error');
        setStatus('error', 'INIT ERROR');
    }
}

// ================= WINDOW CONTROLS =================
function setupWindowControls() {
    titlebarMin.addEventListener('click', () => window.cosmicAPI.windowMinimize());
    titlebarMax.addEventListener('click', () => window.cosmicAPI.windowMaximize());
    titlebarClose.addEventListener('click', () => window.cosmicAPI.windowClose());
}

// ================= TAB NAVIGATION =================
function setupTabNavigation() {
    navItems.forEach(item => {
        item.addEventListener('click', async () => {
            const targetTab = item.getAttribute('data-tab');
            navItems.forEach(n => n.classList.remove('active'));
            tabViews.forEach(v => v.classList.remove('active'));

            item.classList.add('active');
            const targetView = document.getElementById(`view-${targetTab}`);
            if (targetView) targetView.classList.add('active');

            if (targetTab === 'accounts') {
                await refreshAccounts();
            }
        });
    });
}

function setStatus(type, text) {
    statusPill.className = `status-pill ${type}`;
    statusText.textContent = text;
}

// ================= VERSION CARDS =================
function renderVersionCards() {
    versionCardsContainer.innerHTML = '';
    const versions = appState.installInfo?.versions || [
        { id: '1.8.9', name: 'Cosmic Client 1.8.9 (PvP / Factions / Prisons)', recommendedRam: 2048 }
    ];

    versions.forEach(ver => {
        const card = document.createElement('div');
        card.className = `version-card ${ver.id === appState.selectedVersionId ? 'active' : ''}`;
        card.setAttribute('data-id', ver.id);

        const iconSvg = ver.id === '1.8.9'
            ? `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><polyline points="14.5 17.5 3 6 3 3 6 3 17.5 14.5"/><line x1="13" y1="19" x2="19" y2="13"/><line x1="16" y1="16" x2="20" y2="20"/><line x1="19" y1="21" x2="21" y2="19"/><polyline points="14.5 6.5 18 3 21 3 21 6 17.5 9.5"/><line x1="5" y1="14" x2="9" y2="18"/><line x1="7" y1="17" x2="4" y2="20"/><line x1="3" y1="19" x2="5" y2="21"/></svg>`
            : `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>`;

        card.innerHTML = `
            <div class="version-icon">${iconSvg}</div>
            <div class="version-info">
                <h4>${ver.name}</h4>
                <p>Native Off-Cloud Client - Java Agent Enhanced</p>
            </div>
        `;

        card.addEventListener('click', () => {
            document.querySelectorAll('.version-card').forEach(c => c.classList.remove('active'));
            card.classList.add('active');
            appState.selectedVersionId = ver.id;
            footerVersionTag.textContent = `Version: ${ver.name.split(' ')[0]} ${ver.id}`;
            saveCurrentSettings();
        });

        versionCardsContainer.appendChild(card);
    });

    const activeVer = versions.find(v => v.id === appState.selectedVersionId) || versions[0];
    if (activeVer) {
        footerVersionTag.textContent = `Version: ${activeVer.name.split(' ')[0]} ${activeVer.id}`;
    }
}

// ================= ACCOUNTS MANAGEMENT =================
async function refreshAccounts() {
    let data = await window.cosmicAPI.getAccounts();
    if (!data.accounts || data.accounts.length === 0 || (data.accounts.length === 1 && data.accounts[0].isOffline && data.accounts[0].displayName === 'CosmicPlayer')) {
        try {
            await window.cosmicAPI.syncSystemAccounts();
            data = await window.cosmicAPI.getAccounts();
        } catch (e) {}
    }
    appState.accounts = data.accounts || [];
    appState.selectedAccount = data.selected || (appState.accounts.length > 0 ? appState.accounts[0] : null);

    renderDeckAccount();
    renderAccountsList();
}

const DEFAULT_STEVE_SVG = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Crect width='16' height='16' fill='%236b5344'/%3E%3Crect x='4' y='4' width='8' height='8' fill='%23cbb194'/%3E%3Crect x='3' y='2' width='10' height='3' fill='%234a3628'/%3E%3Crect x='4' y='7' width='2' height='1.5' fill='%23384282'/%3E%3Crect x='10' y='7' width='2' height='1.5' fill='%23384282'/%3E%3Crect x='6' y='9' width='4' height='1.5' fill='%23704238'/%3E%3C/svg%3E";

function getAvatarUrl(username) {
    if (!username) return DEFAULT_STEVE_SVG;
    return `https://minotar.net/helm/${encodeURIComponent(username)}/48.png`;
}

function renderDeckAccount() {
    deckAccountSelect.innerHTML = '';

    if (appState.accounts.length === 0) {
        const opt = document.createElement('option');
        opt.value = '';
        opt.textContent = 'No Accounts (Click + to add)';
        deckAccountSelect.appendChild(opt);
        deckAvatarImg.src = getAvatarUrl('Steve');
        footerAccountTag.textContent = 'Account: None';
        return;
    }

    appState.accounts.forEach(acc => {
        const opt = document.createElement('option');
        opt.value = acc.accountId;
        opt.textContent = `${acc.displayName} (${acc.isOffline ? 'Offline' : 'Microsoft'})`;
        if (appState.selectedAccount && acc.accountId === appState.selectedAccount.accountId) {
            opt.selected = true;
        }
        deckAccountSelect.appendChild(opt);
    });

    if (appState.selectedAccount) {
        deckAvatarImg.src = getAvatarUrl(appState.selectedAccount.displayName);
        footerAccountTag.textContent = `Account: ${appState.selectedAccount.displayName}`;
    }
}

function renderAccountsList() {
    accountsListContainer.innerHTML = '';

    if (appState.accounts.length === 0) {
        accountsListContainer.innerHTML = `
            <div class="empty-accounts-box" style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--text-muted);">
                <p>No accounts configured yet.</p>
                <p style="font-size: 12px; margin-top: 6px;">Add a Microsoft account or an Offline profile to begin playing.</p>
            </div>
        `;
        return;
    }

    appState.accounts.forEach(acc => {
        const isSelected = appState.selectedAccount && acc.accountId === appState.selectedAccount.accountId;
        const card = document.createElement('div');
        card.className = `account-card ${isSelected ? 'active' : ''}`;

        card.innerHTML = `
            <div class="account-left">
                <img class="account-avatar" src="${getAvatarUrl(acc.displayName)}" onerror="this.src='${DEFAULT_STEVE_SVG}'" alt="${acc.displayName}">
                <div>
                    <div class="account-name">${acc.displayName}</div>
                    <div class="account-type ${acc.isOffline ? 'offline' : 'ms'}">
                        ${acc.isOffline ? '<svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: middle; margin-right: 4px;"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>Offline / Custom Player' : '<svg viewBox="0 0 16 16" width="12" height="12" style="vertical-align: middle; margin-right: 4px;"><rect x="0" y="0" width="7" height="7" fill="#F25022"/><rect x="8.5" y="0" width="7" height="7" fill="#7FBA00"/><rect x="0" y="8.5" width="7" height="7" fill="#00A4EF"/><rect x="8.5" y="8.5" width="7" height="7" fill="#FFB900"/></svg>Microsoft Xbox Live'}
                    </div>
                </div>
            </div>
            <div class="account-actions">
                ${!isSelected ? `<button class="btn-secondary btn-sm btn-select-acc" data-id="${acc.accountId}">Select</button>` : `<span class="footer-badge purple">Active</span>`}
                <button class="btn-danger btn-sm btn-remove-acc" data-id="${acc.accountId}">Remove</button>
            </div>
        `;

        const btnSelect = card.querySelector('.btn-select-acc');
        if (btnSelect) {
            btnSelect.addEventListener('click', async () => {
                await window.cosmicAPI.selectAccount(acc.accountId);
                await refreshAccounts();
            });
        }

        const btnRemove = card.querySelector('.btn-remove-acc');
        if (btnRemove) {
            btnRemove.addEventListener('click', async () => {
                if (confirm(`Remove account "${acc.displayName}"?`)) {
                    await window.cosmicAPI.removeAccount(acc.accountId);
                    await refreshAccounts();
                }
            });
        }

        accountsListContainer.appendChild(card);
    });
}

// ================= SETTINGS & JVM =================
function populateJvmPresets() {
    settingJvmPreset.innerHTML = '';
    appState.jvmPresets.forEach(preset => {
        const opt = document.createElement('option');
        opt.value = preset.id;
        opt.textContent = preset.name;
        settingJvmPreset.appendChild(opt);
    });

    settingJvmPreset.addEventListener('change', () => {
        const selected = appState.jvmPresets.find(p => p.id === settingJvmPreset.value);
        if (selected) {
            jvmPresetDesc.textContent = selected.description;
        }
    });
}

function formatRam(mb) {
    const gb = (mb / 1024).toFixed(1);
    return `${mb} MB (${gb} GB)`;
}

function populateSettings() {
    const s = appState.settings;

    appState.selectedVersionId = s.selectedVersion || '1.8.9';
    sliderMinRam.value = s.ramMB || 2048;
    sliderMaxRam.value = s.maxRamMB || 4096;
    labelMinRam.textContent = formatRam(sliderMinRam.value);
    labelMaxRam.textContent = formatRam(sliderMaxRam.value);
    deckRamText.textContent = `${sliderMinRam.value} MB - ${sliderMaxRam.value} MB`;

    settingWidth.value = s.width || 1280;
    settingHeight.value = s.height || 720;
    settingFullscreen.checked = !!s.fullscreen;

    if (s.jvmPreset) settingJvmPreset.value = s.jvmPreset;
    const selectedPreset = appState.jvmPresets.find(p => p.id === settingJvmPreset.value);
    if (selectedPreset) jvmPresetDesc.textContent = selectedPreset.description;

    settingCustomJvmArgs.value = s.customJvmArgs || '';
    settingJavaPath.value = s.customJavaPath || '';
    settingUseAgent.checked = s.useAgent !== false;
    customServerInput.value = s.serverDirectConnect || '';
}

function saveCurrentSettings() {
    const updated = {
        selectedVersion: appState.selectedVersionId,
        ramMB: parseInt(sliderMinRam.value),
        maxRamMB: parseInt(sliderMaxRam.value),
        width: parseInt(settingWidth.value),
        height: parseInt(settingHeight.value),
        fullscreen: settingFullscreen.checked,
        jvmPreset: settingJvmPreset.value,
        customJvmArgs: settingCustomJvmArgs.value.trim(),
        customJavaPath: settingJavaPath.value.trim(),
        useAgent: settingUseAgent.checked,
        serverDirectConnect: customServerInput.value.trim()
    };

    appState.settings = updated;
    window.cosmicAPI.saveSettings(updated);
}

// ================= EVENT LISTENERS =================
function setupEventListeners() {
    // RAM Sliders
    sliderMinRam.addEventListener('input', () => {
        if (parseInt(sliderMinRam.value) > parseInt(sliderMaxRam.value)) {
            sliderMaxRam.value = sliderMinRam.value;
            labelMaxRam.textContent = formatRam(sliderMaxRam.value);
        }
        labelMinRam.textContent = formatRam(sliderMinRam.value);
        deckRamText.textContent = `${sliderMinRam.value} MB - ${sliderMaxRam.value} MB`;
    });

    sliderMaxRam.addEventListener('input', () => {
        if (parseInt(sliderMaxRam.value) < parseInt(sliderMinRam.value)) {
            sliderMinRam.value = sliderMaxRam.value;
            labelMinRam.textContent = formatRam(sliderMinRam.value);
        }
        labelMaxRam.textContent = formatRam(sliderMaxRam.value);
        deckRamText.textContent = `${sliderMinRam.value} MB - ${sliderMaxRam.value} MB`;
    });

    // Resolution Presets
    resPresetButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            resPresetButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            settingWidth.value = btn.getAttribute('data-w');
            settingHeight.value = btn.getAttribute('data-h');
        });
    });

    // Server Chips
    serverChips.forEach(chip => {
        chip.addEventListener('click', () => {
            serverChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            const server = chip.getAttribute('data-server');
            customServerInput.value = server;
            appState.selectedServer = server;
        });
    });

    customServerInput.addEventListener('input', () => {
        serverChips.forEach(c => c.classList.remove('active'));
        appState.selectedServer = customServerInput.value.trim();
    });

    // Deck Account Change
    deckAccountSelect.addEventListener('change', async () => {
        const id = deckAccountSelect.value;
        if (id) {
            await window.cosmicAPI.selectAccount(id);
            await refreshAccounts();
        }
    });

    // Browse Java
    btnBrowseJava.addEventListener('click', async () => {
        const p = await window.cosmicAPI.browseJavaPath();
        if (p) {
            settingJavaPath.value = p;
        }
    });

    // Save Settings Button
    btnSaveSettings.addEventListener('click', () => {
        saveCurrentSettings();
        btnSaveSettings.textContent = 'Saved!';
        setTimeout(() => { btnSaveSettings.textContent = 'Save Changes'; }, 1500);
    });

    // Folders Tab Clickable Cards
    document.querySelectorAll('.folder-card').forEach(card => {
        card.addEventListener('click', () => {
            const folderKey = card.getAttribute('data-folder');
            window.cosmicAPI.openFolder(folderKey);
        });
    });

    // Play Button
    btnPlay.addEventListener('click', handleLaunchGame);

    // Force Kill Button
    btnKillGame.addEventListener('click', async () => {
        if (confirm('Force terminate the running Cosmic Client process?')) {
            await window.cosmicAPI.killGame();
            appendLog('[Launcher] Kill signal dispatched.', 'launcher');
        }
    });

    // Console Controls
    btnClearConsole.addEventListener('click', () => {
        terminalContent.innerHTML = '';
        appState.logEntries = [];
    });

    btnCopyConsole.addEventListener('click', () => {
        const allText = appState.logEntries.map(e => e.text).join('\n');
        navigator.clipboard.writeText(allText);
        btnClearConsole.textContent = 'Copied!';
        setTimeout(() => { btnClearConsole.textContent = 'Clear'; }, 1500);
    });

    btnExportConsole.addEventListener('click', () => {
        const allText = appState.logEntries.map(e => e.text).join('\n');
        const blob = new Blob([allText], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `cosmic-client-${Date.now()}.log`;
        a.click();
        URL.revokeObjectURL(url);
    });

    filterChips.forEach(chip => {
        chip.addEventListener('click', () => {
            filterChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            appState.activeFilter = chip.getAttribute('data-filter');
            applyConsoleFilter();
        });
    });

    consoleSearch.addEventListener('input', applyConsoleFilter);

    // Mods Suite Category Filter
    document.querySelectorAll('.mod-cat-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.mod-cat-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const cat = btn.getAttribute('data-cat');

            // Toggle sections
            const pvpSection = document.getElementById('section-pvp-hud');
            const renderSection = document.getElementById('section-render-visuals');

            if (pvpSection) {
                pvpSection.style.display = (cat === 'all' || cat === 'pvp-hud') ? 'block' : 'none';
            }
            if (renderSection) {
                renderSection.style.display = (cat === 'all' || cat === 'render-visuals') ? 'block' : 'none';
            }

            document.querySelectorAll('.mod-card').forEach(card => {
                const cardCats = card.getAttribute('data-category') || '';
                if (cat === 'all' || cardCats.includes(cat)) {
                    card.style.display = 'flex';
                } else {
                    card.style.display = 'none';
                }
            });
        });
    });

    // Mod Suite Toggle Change Events
    document.querySelectorAll('.mod-card input[type="checkbox"]').forEach(input => {
        input.addEventListener('change', () => {
            const card = input.closest('.mod-card');
            const statusText = card.querySelector('.mod-status-text');
            if (statusText) {
                statusText.textContent = input.checked ? 'Active' : 'Disabled';
                statusText.style.color = input.checked ? 'var(--cosmic-success)' : 'var(--text-muted)';
            }
            saveModPreferences();
        });
    });

    const btnResetMods = document.getElementById('btn-reset-mods');
    if (btnResetMods) {
        btnResetMods.addEventListener('click', () => {
            document.querySelectorAll('.mod-card input[type="checkbox"]').forEach(i => {
                i.checked = true;
                const card = i.closest('.mod-card');
                const statusText = card.querySelector('.mod-status-text');
                if (statusText) {
                    statusText.textContent = 'Active';
                    statusText.style.color = 'var(--cosmic-success)';
                }
            });
            saveModPreferences();
            btnResetMods.textContent = 'Reset!';
            setTimeout(() => { btnResetMods.textContent = 'Reset to Defaults'; }, 1500);
        });
    }

    const btnSaveMods = document.getElementById('btn-save-mods');
    if (btnSaveMods) {
        btnSaveMods.addEventListener('click', () => {
            saveModPreferences();
            btnSaveMods.textContent = 'Applied!';
            setTimeout(() => { btnSaveMods.textContent = 'Apply Visual Profile'; }, 1500);
        });
    }

    if (progressJumpConsole) {
        progressJumpConsole.addEventListener('click', () => {
            const consoleTab = document.querySelector('.nav-item[data-tab="console"]');
            if (consoleTab) consoleTab.click();
        });
    }

    // PC Resources & Texture Packs Actions
    if (btnOpenPcRp) {
        btnOpenPcRp.addEventListener('click', async () => {
            await window.cosmicAPI.openResourcePacksFolder();
        });
    }

    if (btnSyncPcResources) {
        btnSyncPcResources.addEventListener('click', async () => {
            btnSyncPcResources.classList.add('loading');
            appendLog('[ResourceBridge] Synchronizing texture packs and game assets from PC...', 'launcher');
            try {
                const res = await window.cosmicAPI.syncPCResources();
                appendLog(`[ResourceBridge] Sync complete: ${res.resourcePacksCount} texture packs & ${res.totalAssets || 0} game assets linked.`, 'launcher');
                await refreshPCResources();
                await updateFolderStats();
                const origSpan = btnSyncPcResources.querySelector('span');
                if (origSpan) origSpan.textContent = 'Synced!';
                setTimeout(() => {
                    if (origSpan) origSpan.textContent = 'Resync All';
                    btnSyncPcResources.classList.remove('loading');
                }, 1500);
            } catch (err) {
                appendLog(`[ResourceBridge] Sync error: ${err.message}`, 'error');
                btnSyncPcResources.classList.remove('loading');
            }
        });
    }

    if (btnImportPcOptions) {
        btnImportPcOptions.addEventListener('click', async () => {
            btnImportPcOptions.disabled = true;
            btnImportPcOptions.textContent = 'Importing...';
            appendLog('[ResourceBridge] Importing controls, sensitivity & FOV from PC (.minecraft/options.txt)...', 'launcher');
            try {
                const res = await window.cosmicAPI.importPlayerOptions(true);
                if (res && res.success) {
                    appendLog('[ResourceBridge] Successfully imported options & keybinds from PC!', 'launcher');
                    btnImportPcOptions.textContent = 'Imported!';
                } else {
                    appendLog('[ResourceBridge] PC options.txt not found or identical.', 'launcher');
                    btnImportPcOptions.textContent = 'Up to Date';
                }
                setTimeout(() => {
                    btnImportPcOptions.textContent = 'Import Keybinds';
                    btnImportPcOptions.disabled = false;
                }, 1800);
            } catch (err) {
                appendLog(`[ResourceBridge] Import options error: ${err.message}`, 'error');
                btnImportPcOptions.textContent = 'Error';
                setTimeout(() => {
                    btnImportPcOptions.textContent = 'Import Keybinds';
                    btnImportPcOptions.disabled = false;
                }, 2000);
            }
        });
    }

    if (btnSyncPcConfigs) {
        btnSyncPcConfigs.addEventListener('click', async () => {
            btnSyncPcConfigs.disabled = true;
            btnSyncPcConfigs.textContent = 'Syncing...';
            appendLog('[ResourceBridge] Synchronizing old Cosmic Client configs, HUD, waypoints & schematics from PC...', 'launcher');
            try {
                const res = await window.cosmicAPI.syncOldConfigs(true);
                if (res && res.success && res.syncedItems > 0) {
                    appendLog(`[ResourceBridge] Successfully synced ${res.syncedItems} Cosmic Client configurations & schematics!`, 'launcher');
                    btnSyncPcConfigs.textContent = 'Synced!';
                } else {
                    appendLog('[ResourceBridge] Old Cosmic Client configs already up to date.', 'launcher');
                    btnSyncPcConfigs.textContent = 'Up to Date';
                }
                setTimeout(() => {
                    btnSyncPcConfigs.textContent = 'Sync Configs & HUD';
                    btnSyncPcConfigs.disabled = false;
                }, 1800);
            } catch (err) {
                appendLog(`[ResourceBridge] Sync configs error: ${err.message}`, 'error');
                btnSyncPcConfigs.textContent = 'Error';
                setTimeout(() => {
                    btnSyncPcConfigs.textContent = 'Sync Configs & HUD';
                    btnSyncPcConfigs.disabled = false;
                }, 2000);
            }
        });
    }

    // Game Log Streaming Event from Main Process
    window.cosmicAPI.onGameLog((logObj) => {
        appendLog(logObj.text, logObj.type);
        handleGameLogProgress(logObj.text);
    });

    // Game Exit Event
    window.cosmicAPI.onGameExit((exitInfo) => {
        resetPlayButton();
        btnKillGame.classList.add('hidden');
        appState.isRunning = false;
        setStatus('ready', 'OFF-CLOUD READY');
        appendLog(`[Launcher] Game session ended (${exitInfo.elapsed || 0}s).`, 'launcher');
        if (launchProgressDeck) {
            updateLaunchProgress(100, 'Game Session Ended', `Process closed (Exit code: ${exitInfo.code ?? 0})`);
            setTimeout(() => {
                if (!appState.isRunning && !appState.isLaunching) {
                    launchProgressDeck.classList.add('hidden');
                }
            }, 4000);
        }
    });

    // Device Code Callback
    window.cosmicAPI.onDeviceCode((codeInfo) => {
        msUserCode.textContent = codeInfo.userCode;
        const targetUrl = codeInfo.directUrl || `https://www.microsoft.com/link?otc=${codeInfo.userCode}`;
        msAuthUrl.href = targetUrl;
        msAuthUrl.textContent = targetUrl;
        showMsStep('code');

        // Automatically copy code to clipboard
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(codeInfo.userCode).catch(() => {});
        }

        // Automatically open default system browser
        if (window.cosmicAPI && window.cosmicAPI.openExternal) {
            window.cosmicAPI.openExternal(targetUrl);
        }
    });

    if (msAuthUrl) {
        msAuthUrl.addEventListener('click', (e) => {
            e.preventDefault();
            const targetUrl = msAuthUrl.href || `https://www.microsoft.com/link?otc=${msUserCode.textContent}`;
            if (window.cosmicAPI && window.cosmicAPI.openExternal) {
                window.cosmicAPI.openExternal(targetUrl);
            } else {
                window.open(targetUrl, '_blank');
            }
        });
    }
}

// ================= PROGRESS BAR CONTROLLER =================
let currentLaunchProgress = 0;

function updateLaunchProgress(percent, title, detail) {
    if (launchProgressDeck) {
        launchProgressDeck.classList.remove('hidden');
    }
    if (percent !== null && percent !== undefined) {
        currentLaunchProgress = Math.max(currentLaunchProgress, Math.min(100, Math.round(percent)));
        if (progressBarFill) {
            progressBarFill.style.width = `${currentLaunchProgress}%`;
        }
        if (progressPercentBadge) {
            progressPercentBadge.textContent = `${currentLaunchProgress}%`;
        }
    }
    if (title && progressStatusTitle) {
        progressStatusTitle.textContent = title;
    }
    if (detail && progressDetailSubtitle) {
        progressDetailSubtitle.textContent = detail;
    }
}

function handleGameLogProgress(rawText) {
    if (!rawText) return;
    const text = rawText.trim();

    if (text.includes('[Launcher] Starting Cosmic Client')) {
        updateLaunchProgress(12, 'Starting Cosmic Client...', 'Bootstrapping JVM runtime environment...');
    } else if (text.includes('[CosmicAgent] Loaded') || text.includes('[CosmicAgent] FpsBooster')) {
        updateLaunchProgress(24, 'Bytecode Agent Active', 'Injecting 2026 PvP FastMath suite...');
    } else if (text.includes('Starting client from Main') || text.includes('Setting user:')) {
        updateLaunchProgress(36, 'Starting Minecraft 1.8.9 Core...', 'Spawning Minecraft client thread...');
    } else if (text.includes('LWJGL Version:')) {
        updateLaunchProgress(46, 'Initializing Display Engine...', 'Creating OpenGL window and display context...');
    } else if (text.includes('Reloading ResourceManager') || text.includes('Reloading textures')) {
        updateLaunchProgress(55, 'Loading Textures & Audio...', 'Loading authentic Cosmic fonts, sounds & shaders...');
    } else if (text.includes('Starting up SoundSystem') || text.includes('OpenAL initialized')) {
        updateLaunchProgress(62, 'Sound System Active', 'OpenAL audio device initialized...');
    } else if (text.includes('textures-atlas')) {
        updateLaunchProgress(68, 'Building Texture Atlas...', 'Compiling custom PvP block & item textures...');
    } else if (text.includes('[*Load]')) {
        const match = text.match(/\[\*Load\]\s+(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s*(.*)/);
        if (match) {
            const cur = parseFloat(match[1]);
            const max = parseFloat(match[2]);
            const stepName = match[3].trim();
            if (stepName.toLowerCase().includes('finishing') || (max > 0 && cur >= max && cur >= 5)) {
                updateLaunchProgress(100, 'Cosmic Client 1.8.9 Ready!', 'Game window active - Enjoy playing!');
                setTimeout(() => {
                    if (launchProgressDeck) launchProgressDeck.classList.add('hidden');
                }, 2500);
            } else if (max > 0) {
                const pct = 70 + (cur / max) * 28;
                const title = stepName.startsWith('Loading') ? `${stepName}...` : `Loading ${stepName || 'Game Resources'}...`;
                updateLaunchProgress(pct, title, `Step ${Math.round(cur)}/${Math.round(max)}: ${stepName}`);
            }
        }
    } else if (text.includes('Loaded Dedicated Menus') || text.includes('Finishing up') || text.includes('Main Menu active')) {
        updateLaunchProgress(100, 'Cosmic Client 1.8.9 Ready!', 'Game window active - Enjoy playing!');
        setTimeout(() => {
            if (launchProgressDeck) launchProgressDeck.classList.add('hidden');
        }, 2500);
    } else if (text.includes('FATAL') || text.includes('Crash Report') || text.includes('UnsatisfiedLinkError')) {
        if (progressBarFill) progressBarFill.style.background = '#ef4444';
        updateLaunchProgress(null, 'Launch Error Detected', text.slice(0, 100));
    }
}

// ================= LAUNCH ACTION =================
async function handleLaunchGame() {
    if (appState.isLaunching || appState.isRunning) return;

    saveCurrentSettings();

    appState.isLaunching = true;
    btnPlay.classList.add('launching');
    playBtnText.classList.add('hidden');
    playBtnSpinner.classList.remove('hidden');
    setStatus('running', 'LAUNCHING GAME...');

    currentLaunchProgress = 0;
    if (progressBarFill) {
        progressBarFill.style.background = 'linear-gradient(90deg, #7c3aed 0%, #06b6d4 50%, #10b981 100%)';
    }
    updateLaunchProgress(5, 'Preparing Launch Sequence...', 'Checking client files and Java 19 runtime...');

    appendLog(`[Launcher] Preparing launch sequence...`, 'launcher');

    const opts = {
        selectedVersion: appState.selectedVersionId,
        ramMB: parseInt(sliderMinRam.value),
        maxRamMB: parseInt(sliderMaxRam.value),
        width: parseInt(settingWidth.value),
        height: parseInt(settingHeight.value),
        fullscreen: settingFullscreen.checked,
        jvmPreset: settingJvmPreset.value,
        customJvmArgs: settingCustomJvmArgs.value,
        customJavaPath: settingJavaPath.value,
        useAgent: settingUseAgent.checked,
        serverDirectConnect: customServerInput.value.trim()
    };

    if (appState.selectedAccount) {
        opts.username = appState.selectedAccount.displayName;
        opts.uuid = appState.selectedAccount.profileId;
        opts.accessToken = appState.selectedAccount.accessToken;
        opts.isOffline = appState.selectedAccount.isOffline;
    } else {
        opts.username = 'CosmicPlayer';
    }

    const result = await window.cosmicAPI.launchGame(opts);

    if (result.success) {
        appState.isLaunching = false;
        appState.isRunning = true;
        setStatus('running', `PLAYING (PID: ${result.pid})`);
        btnKillGame.classList.remove('hidden');
        updateLaunchProgress(15, 'Process Spawned', `PID ${result.pid} spawned successfully`);

        // Safety fallback: ensure progress reaches 100% and deck auto-hides once game is running smoothly
        setTimeout(() => {
            if (appState.isRunning && currentLaunchProgress > 50) {
                updateLaunchProgress(100, 'Cosmic Client 1.8.9 Ready!', 'Game window active - Enjoy playing!');
                setTimeout(() => {
                    if (launchProgressDeck && appState.isRunning) launchProgressDeck.classList.add('hidden');
                }, 2500);
            }
        }, 12000);
    } else {
        resetPlayButton();
        setStatus('error', 'LAUNCH FAILED');
        appendLog(`[Launcher] Launch Failed: ${result.error}`, 'error');
        updateLaunchProgress(null, 'Launch Failed', result.error);
        if (progressBarFill) progressBarFill.style.background = '#ef4444';
    }
}

function resetPlayButton() {
    appState.isLaunching = false;
    btnPlay.classList.remove('launching');
    playBtnText.classList.remove('hidden');
    playBtnSpinner.classList.add('hidden');
}

// ================= CONSOLE STREAMING =================
function appendLog(text, type = 'game') {
    const entry = { text, type, time: new Date() };
    appState.logEntries.push(entry);

    if (matchesFilter(entry)) {
        renderLogEntry(entry);
    }
}

function matchesFilter(entry) {
    const filter = appState.activeFilter;
    const query = consoleSearch.value.toLowerCase().trim();

    if (query && !entry.text.toLowerCase().includes(query)) {
        return false;
    }

    if (filter === 'all') return true;
    if (filter === 'agent' && entry.type === 'agent') return true;
    if (filter === 'game' && entry.type === 'game') return true;
    if (filter === 'launcher' && entry.type === 'launcher') return true;
    if (filter === 'error' && (entry.type === 'error' || entry.type === 'warn')) return true;

    return false;
}

function renderLogEntry(entry) {
    const span = document.createElement('div');
    span.className = `log-line log-${entry.type}`;
    span.textContent = entry.text;
    terminalContent.appendChild(span);
    while (terminalContent.childElementCount > 600) {
        terminalContent.removeChild(terminalContent.firstElementChild);
    }
    terminalBox.scrollTop = terminalBox.scrollHeight;
}

function applyConsoleFilter() {
    terminalContent.innerHTML = '';
    appState.logEntries.forEach(entry => {
        if (matchesFilter(entry)) {
            renderLogEntry(entry);
        }
    });
}

// ================= MODAL LOGIC =================
function setupModals() {
    // Open Modals
    btnOpenMsModal.addEventListener('click', () => {
        showMsStep('init');
        modalMsLogin.classList.remove('hidden');
    });

    const btnSyncSystemAccounts = document.getElementById('btn-sync-system-accounts');
    if (btnSyncSystemAccounts) {
        btnSyncSystemAccounts.addEventListener('click', async () => {
            btnSyncSystemAccounts.textContent = 'Syncing...';
            await window.cosmicAPI.syncSystemAccounts();
            await refreshAccounts();
            btnSyncSystemAccounts.textContent = 'Accounts Synced!';
            setTimeout(() => {
                btnSyncSystemAccounts.textContent = 'Sync Minecraft Accounts';
            }, 2500);
        });
    }

    btnOpenOfflineModal.addEventListener('click', () => {
        offlineUsernameInput.value = '';
        modalOfflineLogin.classList.remove('hidden');
        offlineUsernameInput.focus();
    });

    btnQuickAddAccount.addEventListener('click', () => {
        offlineUsernameInput.value = '';
        modalOfflineLogin.classList.remove('hidden');
        offlineUsernameInput.focus();
    });

    // Close Modals
    document.querySelectorAll('[data-close]').forEach(btn => {
        btn.addEventListener('click', () => {
            const targetModalId = btn.getAttribute('data-close');
            const targetModal = document.getElementById(targetModalId);
            if (targetModal) targetModal.classList.add('hidden');
            window.cosmicAPI.cancelAuth();
        });
    });

    // Start MS Auth
    btnStartMsAuth.addEventListener('click', async () => {
        btnStartMsAuth.disabled = true;
        btnStartMsAuth.textContent = 'Opening Microsoft Login...';

        try {
            const result = await window.cosmicAPI.loginMicrosoft();
            btnStartMsAuth.disabled = false;
            btnStartMsAuth.textContent = 'Sign in with Microsoft';

            if (result.success) {
                msSuccessName.textContent = `Welcome, ${result.displayName}!`;
                showMsStep('done');
                await refreshAccounts();
            } else if (result.error && !result.error.includes('cancelled')) {
                msErrorMsg.textContent = result.error;
                showMsStep('error');
            }
        } catch (err) {
            btnStartMsAuth.disabled = false;
            btnStartMsAuth.textContent = 'Sign in with Microsoft';
            msErrorMsg.textContent = err.message;
            showMsStep('error');
        }
    });

    // Copy Code Button
    btnCopyCode.addEventListener('click', () => {
        navigator.clipboard.writeText(msUserCode.textContent);
        btnCopyCode.textContent = 'Copied!';
        setTimeout(() => { btnCopyCode.textContent = 'Copy'; }, 1500);
    });

    // Submit Offline Account
    btnSubmitOfflineAccount.addEventListener('click', async () => {
        const username = offlineUsernameInput.value.trim();
        if (!username || username.length < 2) {
            alert('Please enter a valid Minecraft username (2-16 characters).');
            return;
        }

        const res = await window.cosmicAPI.addOfflineAccount(username);
        if (res.success) {
            modalOfflineLogin.classList.add('hidden');
            await refreshAccounts();
        } else {
            alert('Failed to add account: ' + res.error);
        }
    });

    offlineUsernameInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            btnSubmitOfflineAccount.click();
        }
    });
}

function showMsStep(step) {
    msStepInit.classList.add('hidden');
    msStepCode.classList.add('hidden');
    msStepDone.classList.add('hidden');
    msStepError.classList.add('hidden');

    if (step === 'init') msStepInit.classList.remove('hidden');
    else if (step === 'code') msStepCode.classList.remove('hidden');
    else if (step === 'done') msStepDone.classList.remove('hidden');
    else if (step === 'error') msStepError.classList.remove('hidden');
}

async function updateFolderStats() {
    try {
        const stats = await window.cosmicAPI.getFolderStats();
        if (stats) {
            const rpEl = document.getElementById('folder-count-resourcepacks');
            if (rpEl) rpEl.textContent = `${stats.resourcepacks || 0} custom PvP texture & sound packs installed`;

            const shEl = document.getElementById('folder-count-shaderpacks');
            if (shEl) shEl.textContent = `${stats.shaderpacks || 0} OptiFine shader packs installed`;

            const scEl = document.getElementById('folder-count-screenshots');
            if (scEl) scEl.textContent = `${stats.screenshots || 0} in-game F2 photos & captures`;

            const cfEl = document.getElementById('folder-count-config');
            if (cfEl) cfEl.textContent = `${stats.config || 0} profile, waypoint & HUD configuration files`;

            const smEl = document.getElementById('folder-count-schematics');
            if (smEl) smEl.textContent = `${stats.schematics || 0} WorldEdit & Schematica blueprint files`;

            const svEl = document.getElementById('folder-count-saves');
            if (svEl) svEl.textContent = `${stats.saves || 0} singleplayer worlds & map saves`;

            const lgEl = document.getElementById('folder-count-logs');
            if (lgEl) lgEl.textContent = `${stats.logs || 0} game crash reports & debug logs`;

            const oaEl = document.getElementById('folder-count-offlineArchive');
            if (oaEl) oaEl.textContent = `${stats.offlineArchive || 17} archived cross-platform assets (Windows, Mac M1/M2/Intel, Linux)`;
        }
    } catch (e) {
        // Ignore
    }
}

// ================= PC RESOURCES & TEXTURE PACKS =================
async function refreshPCResources() {
    try {
        if (!window.cosmicAPI?.getPCResourcesSummary) return;
        const summary = await window.cosmicAPI.getPCResourcesSummary();
        if (summary) {
            if (pcResPacksCount) {
                pcResPacksCount.textContent = `${summary.resourcePacksCount} Packs`;
            }
            if (pcResAssetsStatus) {
                pcResAssetsStatus.textContent = summary.hasAssets ? 'Shared from PC' : 'Offline Vault Active';
            }
            if (pcResDesc) {
                if (summary.hasMinecraft) {
                    pcResDesc.innerHTML = `Connected to <strong>.minecraft</strong> - <strong>${summary.resourcePacksCount}</strong> packs synced live`;
                } else {
                    pcResDesc.textContent = 'Standalone mode - Texture packs directory active';
                }
            }
        }
    } catch (e) {
        // Non-critical fallback
    }
}

// ================= NEBULA STARFIELD CANVAS =================
function initNebulaCanvas() {
    const canvas = document.getElementById('nebula-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    let width = (canvas.width = window.innerWidth);
    let height = (canvas.height = window.innerHeight);

    window.addEventListener('resize', () => {
        width = canvas.width = window.innerWidth;
        height = canvas.height = window.innerHeight;
    });

    const stars = Array.from({ length: 90 }, () => ({
        x: Math.random() * width,
        y: Math.random() * height,
        radius: Math.random() * 1.5 + 0.5,
        alpha: Math.random() * 0.7 + 0.3,
        speed: Math.random() * 0.015 + 0.005,
        phase: Math.random() * Math.PI * 2
    }));

    function draw() {
        ctx.clearRect(0, 0, width, height);

        stars.forEach(star => {
            star.phase += star.speed;
            const currentAlpha = star.alpha * (0.6 + 0.4 * Math.sin(star.phase));

            ctx.beginPath();
            ctx.arc(star.x, star.y, star.radius, 0, Math.PI * 2);
            ctx.fillStyle = `rgba(196, 181, 253, ${currentAlpha})`;
            ctx.shadowBlur = 4;
            ctx.shadowColor = '#8b5cf6';
            ctx.fill();
        });

        requestAnimationFrame(draw);
    }

    draw();
}

// ================= MOD PREFERENCE PERSISTENCE =================
function saveModPreferences() {
    const mods = {
        cps: document.getElementById('mod-cps')?.checked ?? true,
        keystrokes: document.getElementById('mod-keystrokes')?.checked ?? true,
        armor: document.getElementById('mod-armor')?.checked ?? true,
        direction: document.getElementById('mod-direction')?.checked ?? true,
        fullbright: document.getElementById('mod-fullbright')?.checked ?? true,
        animations: document.getElementById('mod-animations')?.checked ?? true,
        fastrender: document.getElementById('mod-fastrender')?.checked ?? true,
        potion: document.getElementById('mod-potion')?.checked ?? true,
        cooldown: document.getElementById('mod-cooldown')?.checked ?? true,
        scoreboard: document.getElementById('mod-scoreboard')?.checked ?? true,
        particles: document.getElementById('mod-particles')?.checked ?? true
    };
    try {
        localStorage.setItem('cosmic_mods_2026', JSON.stringify(mods));
    } catch(e) {}
}

function loadModPreferences() {
    try {
        const raw = localStorage.getItem('cosmic_mods_2026');
        if (!raw) return;
        const mods = JSON.parse(raw);
        for (const [key, val] of Object.entries(mods)) {
            const input = document.getElementById(`mod-${key}`);
            if (input) {
                input.checked = !!val;
                const card = input.closest('.mod-card');
                const statusText = card?.querySelector('.mod-status-text');
                if (statusText) {
                    statusText.textContent = input.checked ? 'Active' : 'Disabled';
                    statusText.style.color = input.checked ? 'var(--cosmic-success)' : 'var(--text-muted)';
                }
            }
        }
    } catch(e) {}
}

// Start application
document.addEventListener('DOMContentLoaded', () => {
    init();
    loadModPreferences();
});

