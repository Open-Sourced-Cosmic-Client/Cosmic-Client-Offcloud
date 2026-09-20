const launcher = require('../lib/launcher');
const cosmic = require('../lib/cosmic');

async function testAgent() {
    console.log('Testing Cosmic Agent loading...');
    const installDir = cosmic.getInstallDir();
    const opts = {
        username: 'TestOfflinePlayer',
        uuid: '00000000-0000-0000-0000-000000000000',
        ramMB: 2048,
        server: ''
    };

    let logCount = 0;
    let agentLoaded = false;
    let transformersActive = false;

    const result = await launcher.launch(
        installDir,
        opts,
        (text, type) => {
            logCount++;
            if (text.includes('[CosmicAgent]')) {
                agentLoaded = true;
                console.log('AGENT LOG:', text.trim());
            }
            if (text.includes('Transformer') || text.includes('Injecting') || text.includes('OpenAL') || text.includes('Hooked')) {
                transformersActive = true;
                console.log('AGENT ACTIVITY:', text.trim());
            }
            if (logCount <= 30) {
                console.log(`[${type}] ${text.trim()}`);
            }
        },
        (code) => {
            console.log('Game process exited with code:', code);
        }
    );

    if (!result.success) {
        console.error('Launch failed:', result.error);
        process.exit(1);
    }

    setTimeout(() => {
        console.log('====================================');
        console.log('=== TEST VERIFICATION RESULT ===');
        console.log('CosmicAgent Initialized:', agentLoaded);
        console.log('Transformers Executed:', transformersActive);
        console.log('====================================');
        launcher.kill();
        process.exit(0);
    }, 7000);
}

testAgent();
