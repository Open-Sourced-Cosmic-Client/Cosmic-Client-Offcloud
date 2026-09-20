const { app, BrowserWindow, ipcMain, shell, dialog, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');

const cosmic = require('./lib/cosmic');
const accounts = require('./lib/accounts');
const auth = require('./lib/auth');
const settings = require('./lib/settings');
const launcher = require('./lib/launcher');
const jvmPresets = require('./lib/jvmPresets');
const dataBridge = require('./lib/dataBridge');

let mainWindow = null;
let splashWindow = null;

// Single instance lock to prevent duplicate conflicting processes
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
    app.quit();
} else {
    app.on('second-instance', () => {
        if (mainWindow) {
            if (mainWindow.isMinimized()) mainWindow.restore();
            mainWindow.focus();
        }
    });
}

// Ensure Windows taskbar groups windows under Cosmic Client and displays the authentic launcher icon
if (process.platform === 'win32') {
    app.setAppUserModelId('com.cosmic.launcher');
}

function getAppIcon() {
    const candidates = [
        path.join(__dirname, 'cosmic.ico'),
        path.join(__dirname, 'src', 'assets', 'cosmic.ico'),
        path.join(__dirname, 'cosmic-icon.png'),
        path.join(__dirname, 'src', 'assets', 'cosmic-icon.png')
    ];
    for (const c of candidates) {
        if (fs.existsSync(c)) {
            try {
                const img = nativeImage.createFromPath(c);
                if (!img.isEmpty()) return img;
            } catch (e) {}
        }
    }
    return undefined;
}

function getAppIconPath() {
    const candidates = [
        path.join(__dirname, 'cosmic.ico'),
        path.join(__dirname, 'src', 'assets', 'cosmic.ico'),
        path.join(__dirname, 'cosmic-icon.png'),
        path.join(__dirname, 'src', 'assets', 'cosmic-icon.png')
    ];
    for (const c of candidates) {
        if (fs.existsSync(c)) return c;
    }
    return undefined;
}

function createSplashWindow() {
    const appIcon = getAppIcon();
    const iconPath = getAppIconPath();
    splashWindow = new BrowserWindow({
        title: 'Cosmic Client',
        icon: appIcon || iconPath,
        width: 480,
        height: 320,
        frame: false,
        resizable: false,
        alwaysOnTop: true,
        backgroundColor: '#06070d',
        center: true,
        show: true,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        }
    });

    if (appIcon && typeof splashWindow.setIcon === 'function') {
        splashWindow.setIcon(appIcon);
    }

    splashWindow.loadFile(path.join(__dirname, 'src', 'splash.html'));

    splashWindow.on('closed', () => {
        splashWindow = null;
    });
}

function createWindow() {
    const appIcon = getAppIcon();
    const iconPath = getAppIconPath();
    mainWindow = new BrowserWindow({
        title: 'Cosmic Client Offcloud',
        icon: appIcon || iconPath,
        width: 980,
        height: 640,
        minWidth: 840,
        minHeight: 560,
        frame: false,
        backgroundColor: '#07080e',
        show: false, // Keep hidden until ready and splash finishes
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            contextIsolation: true,
            nodeIntegration: false
        }
    });

    mainWindow.loadFile(path.join(__dirname, 'src', 'index.html'));

    if (appIcon && typeof mainWindow.setIcon === 'function') {
        mainWindow.setIcon(appIcon);
    }

    let windowShown = false;
    const revealMainWindow = () => {
        if (windowShown) return;
        windowShown = true;
        if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.show();
            mainWindow.focus();
        }
        if (splashWindow && !splashWindow.isDestroyed()) {
            splashWindow.destroy();
            splashWindow = null;
        }
    };

    mainWindow.once('ready-to-show', () => {
        // Wait 1.4s for splash progress animation to complete smoothly
        setTimeout(revealMainWindow, 1400);
    });

    // Safety fallback: reveal main window after 3s max
    setTimeout(revealMainWindow, 3000);

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

app.whenReady().then(() => {
    if (gotTheLock) {
        createSplashWindow();
        createWindow();

        app.on('activate', () => {
            if (BrowserWindow.getAllWindows().length === 0) createWindow();
        });
    }
});

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit();
    }
});

// --- Window Controls ---
ipcMain.handle('window-minimize', () => {
    if (mainWindow) mainWindow.minimize();
});

ipcMain.handle('window-maximize', () => {
    if (mainWindow) {
        if (mainWindow.isMaximized()) {
            mainWindow.unmaximize();
        } else {
            mainWindow.maximize();
        }
    }
});

ipcMain.handle('window-close', () => {
    if (mainWindow) mainWindow.close();
});

// --- Installation & Version Detection ---
ipcMain.handle('detect-install', async () => {
    return cosmic.detectInstall();
});

ipcMain.handle('get-folders', async () => {
    return cosmic.getFolders();
});

ipcMain.handle('get-folder-stats', async () => {
    return cosmic.getFolderStats();
});

// --- Account Management ---
ipcMain.handle('get-accounts', async () => {
    const installDir = cosmic.getInstallDir();
    return accounts.getAccounts(installDir);
});

ipcMain.handle('select-account', async (event, accountId) => {
    const installDir = cosmic.getInstallDir();
    return accounts.selectAccount(installDir, accountId);
});

ipcMain.handle('add-offline-account', async (event, username) => {
    const installDir = cosmic.getInstallDir();
    return accounts.addOfflineAccount(installDir, username);
});

ipcMain.handle('remove-account', async (event, accountId) => {
    const installDir = cosmic.getInstallDir();
    return accounts.removeAccount(installDir, accountId);
});

ipcMain.handle('sync-system-accounts', async () => {
    const installDir = cosmic.getInstallDir();
    return accounts.importSystemAccounts(installDir);
});

ipcMain.handle('login-microsoft', async () => {
    const installDir = cosmic.getInstallDir();
    return auth.loginMicrosoft(installDir, (codeInfo) => {
        if (mainWindow) {
            mainWindow.webContents.send('ms-device-code', codeInfo);
        }
    });
});

ipcMain.handle('cancel-auth', async () => {
    auth.cancelAuth();
    return { success: true };
});

// --- Settings & JVM Presets ---
ipcMain.handle('get-settings', async () => {
    const installDir = cosmic.getInstallDir();
    return settings.load(installDir);
});

ipcMain.handle('save-settings', async (event, newSettings) => {
    const installDir = cosmic.getInstallDir();
    return settings.save(installDir, newSettings);
});

ipcMain.handle('get-jvm-presets', async () => {
    return jvmPresets.getPresets();
});

// --- Launch & Process Management ---
ipcMain.handle('launch-game', async (event, opts) => {
    const installDir = cosmic.getInstallDir();
    if (!installDir) {
        return { success: false, error: 'Installation directory not found.' };
    }

    // Refresh token if Microsoft account with UUID and online
    if (opts.uuid && !opts.isOffline && opts.accessToken && opts.accessToken !== 'null' && opts.accessToken !== 'offline') {
        try {
            const refreshed = await auth.refreshAccount(installDir, opts.uuid);
            if (refreshed && refreshed.token) {
                opts.accessToken = refreshed.token;
            }
        } catch (e) {
            console.warn('[Main] Token refresh attempt bypassed:', e.message);
        }
    }

    const result = await launcher.launch(installDir, opts, (logText, type) => {
        if (mainWindow) {
            mainWindow.webContents.send('game-log', { text: logText, type });
        }
    }, (exitInfo) => {
        if (mainWindow) {
            mainWindow.webContents.send('game-exit', exitInfo);
        }
    });

    return result;
});

ipcMain.handle('kill-game', async () => {
    return launcher.kill();
});

ipcMain.handle('is-game-running', async () => {
    return {
        running: launcher.isRunning(),
        pid: launcher.getRunningPid()
    };
});

// --- OS Utilities ---
ipcMain.handle('open-folder', async (event, folderKey) => {
    const folders = cosmic.getFolders();
    const target = folders[folderKey] || folders.gameRoot;
    if (fs.existsSync(target)) {
        shell.openPath(target);
        return { success: true };
    } else {
        // Create if missing
        fs.mkdirSync(target, { recursive: true });
        shell.openPath(target);
        return { success: true };
    }
});

ipcMain.handle('open-external', async (event, url) => {
    if (typeof url === 'string' && (url.startsWith('https://') || url.startsWith('http://'))) {
        shell.openExternal(url);
    }
});

ipcMain.handle('browse-java-path', async () => {
    if (!mainWindow) return null;
    const result = await dialog.showOpenDialog(mainWindow, {
        title: 'Select Java Executable (java.exe)',
        filters: [
            { name: 'Executables', extensions: ['exe', 'bat', 'cmd'] },
            { name: 'All Files', extensions: ['*'] }
        ],
        properties: ['openFile']
    });

    if (!result.canceled && result.filePaths.length > 0) {
        return result.filePaths[0];
    }
    return null;
});

// --- PC Resources & Texture Packs Bridge ---
ipcMain.handle('get-pc-resources-summary', async () => {
    const installDir = cosmic.getInstallDir();
    return dataBridge.getPCResourcesSummary(installDir);
});

ipcMain.handle('sync-pc-resources', async () => {
    const installDir = cosmic.getInstallDir();
    return dataBridge.syncAll(installDir);
});

ipcMain.handle('import-player-options', async (event, force) => {
    const installDir = cosmic.getInstallDir();
    const imported = dataBridge.importPlayerOptions(installDir, force);
    return { success: imported };
});

ipcMain.handle('sync-old-configs', async (event, force) => {
    const installDir = cosmic.getInstallDir();
    return dataBridge.syncOldCosmicConfigs(installDir, force);
});

ipcMain.handle('open-resource-packs-folder', async () => {
    const folders = cosmic.getFolders();
    const rpDir = folders.resourcepacks;
    if (!fs.existsSync(rpDir)) {
        fs.mkdirSync(rpDir, { recursive: true });
    }
    shell.openPath(rpDir);
    return { success: true };
});


