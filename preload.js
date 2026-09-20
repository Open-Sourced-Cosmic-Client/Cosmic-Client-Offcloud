const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('cosmicAPI', {
    // Window controls
    windowMinimize: () => ipcRenderer.invoke('window-minimize'),
    windowMaximize: () => ipcRenderer.invoke('window-maximize'),
    windowClose: () => ipcRenderer.invoke('window-close'),

    // Detection & Folders
    detectInstall: () => ipcRenderer.invoke('detect-install'),
    getFolders: () => ipcRenderer.invoke('get-folders'),
    getFolderStats: () => ipcRenderer.invoke('get-folder-stats'),
    openFolder: (folderKey) => ipcRenderer.invoke('open-folder', folderKey),
    browseJavaPath: () => ipcRenderer.invoke('browse-java-path'),
    openExternal: (url) => ipcRenderer.invoke('open-external', url),

    // Account Management
    getAccounts: () => ipcRenderer.invoke('get-accounts'),
    syncSystemAccounts: () => ipcRenderer.invoke('sync-system-accounts'),
    selectAccount: (accountId) => ipcRenderer.invoke('select-account', accountId),
    addOfflineAccount: (username) => ipcRenderer.invoke('add-offline-account', username),
    removeAccount: (accountId) => ipcRenderer.invoke('remove-account', accountId),
    loginMicrosoft: () => ipcRenderer.invoke('login-microsoft'),
    cancelAuth: () => ipcRenderer.invoke('cancel-auth'),

    // Settings
    getSettings: () => ipcRenderer.invoke('get-settings'),
    saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
    getJvmPresets: () => ipcRenderer.invoke('get-jvm-presets'),

    // Game Launch & Management
    launchGame: (opts) => ipcRenderer.invoke('launch-game', opts),
    killGame: () => ipcRenderer.invoke('kill-game'),
    isGameRunning: () => ipcRenderer.invoke('is-game-running'),

    // Event Subscriptions
    onGameLog: (callback) => ipcRenderer.on('game-log', (event, data) => callback(data)),
    onGameExit: (callback) => ipcRenderer.on('game-exit', (event, data) => callback(data)),
    onDeviceCode: (callback) => ipcRenderer.on('ms-device-code', (event, data) => callback(data)),

    // PC Resources & Texture Packs Bridge
    getPCResourcesSummary: () => ipcRenderer.invoke('get-pc-resources-summary'),
    syncPCResources: () => ipcRenderer.invoke('sync-pc-resources'),
    importPlayerOptions: (force) => ipcRenderer.invoke('import-player-options', force),
    syncOldConfigs: (force) => ipcRenderer.invoke('sync-old-configs', force),
    openResourcePacksFolder: () => ipcRenderer.invoke('open-resource-packs-folder')
});

