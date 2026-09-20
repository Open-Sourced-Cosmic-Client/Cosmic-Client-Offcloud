const launcher = require('../lib/launcher');
const cosmic = require('../lib/cosmic');
const settings = require('../lib/settings');
const accounts = require('../lib/accounts');

async function main() {
    const installDir = cosmic.getInstallDir();
    const config = settings.getSettings(installDir);
    const accData = accounts.getAccounts(installDir);

    const activeAcc = accData.selected || (accData.accounts.length > 0 ? accData.accounts[0] : null);

    const opts = {
        selectedVersion: config.selectedVersion || '1.8.9',
        ramMB: config.ramMB || 2048,
        maxRamMB: config.maxRamMB || 4096,
        width: config.width || 1280,
        height: config.height || 720,
        fullscreen: !!config.fullscreen,
        jvmPreset: config.jvmPreset || 'pvp',
        customJvmArgs: config.customJvmArgs || '',
        customJavaPath: config.customJavaPath || '',
        useAgent: config.useAgent !== false,
        serverDirectConnect: config.serverDirectConnect || '',
        username: activeAcc ? activeAcc.displayName : 'CosmicPlayer',
        uuid: activeAcc ? activeAcc.profileId : '00000000-0000-0000-0000-000000000000',
        accessToken: activeAcc ? activeAcc.accessToken : 'null'
    };

    console.log('[HeadlessLauncher] Starting Cosmic Client in 100% Offline Mode...');
    const res = await launcher.launch(
        installDir,
        opts,
        (text, type) => process.stdout.write(text),
        (exitInfo) => {
            console.log(`\n[HeadlessLauncher] Process closed (Exit code: ${exitInfo.code}).`);
            process.exit(exitInfo.code || 0);
        }
    );

    if (!res.success) {
        console.error('[HeadlessLauncher] Failed to start:', res.error);
        process.exit(1);
    }
}

main().catch(err => {
    console.error('[HeadlessLauncher] Fatal Error:', err);
    process.exit(1);
});
