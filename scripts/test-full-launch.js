const launcher = require('../lib/launcher');
const cosmic = require('../lib/cosmic');

async function main() {
    console.log('--- Diagnostic Full Launch Test ---');
    const install = cosmic.detectInstall();
    console.log('Install status:', install);

    const opts = {
        selectedVersion: '1.8.9',
        ramMB: 2048,
        maxRamMB: 4096,
        width: 1280,
        height: 720,
        fullscreen: false,
        jvmPreset: 'ultra_performance',
        username: 'DiagnosticPlayer',
        uuid: '00000000-0000-0000-0000-000000000000',
        useAgent: true
    };

    let crashed = false;

    const path = require('path');
    const appData = path.join(process.env.APPDATA, '.minecraft', 'cosmic');

    const res = await launcher.launch(
        appData,
        opts,
        (text, type) => {
            console.log(`[${type}] ${text.trim()}`);
            if (text.includes('FATAL') || text.includes('Crash Report') || text.includes('RuntimeException') || text.includes('ClassFormatError') || text.includes('UnsatisfiedLinkError')) {
                crashed = true;
            }
        },
        (exitInfo) => {
            console.log('[Exit Info]:', exitInfo);
        }
    );

    console.log('[Launch Result]:', res);

    setTimeout(() => {
        console.log('==============================================');
        console.log('Test completed after 25 seconds.');
        console.log('Crashed during launch:', crashed);
        console.log('Game process running:', launcher.isRunning());
        console.log('==============================================');
        launcher.kill();
        process.exit(crashed ? 1 : 0);
    }, 25000);
}

main().catch(err => {
    console.error('Fatal launch test error:', err);
    process.exit(1);
});
