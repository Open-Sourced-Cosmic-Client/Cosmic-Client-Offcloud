const { spawn } = require('child_process');
const path = require('path');

const installDir = path.resolve('CosmicClient-x64');
const javaExe = path.join(installDir, 'bootstrap', 'java', 'bin', 'java.exe');
const agentJar = path.join(installDir, 'cosmic-agent.jar');
const clientJar = path.join(installDir, '1.8', 'CosmicClient-1.8.9.jar');
const nativesDir = path.join(installDir, '1.8', 'bin-1.8');
const assetsDir = path.join(installDir, 'assets_18');

const args = [
    `-Dcosmic.java=${path.join(installDir, 'bootstrap', 'java')}`,
    `-Dcosmic.installdir=${installDir}`,
    `-Dcosmic.bootstrap=${path.join(installDir, 'Launcher.jar')}`,
    '-Dcosmic.launcher.load=v1',
    '-Dcosmic.log.plain=true',
    `-Djava.library.path=${nativesDir}`,
    '-Xms1024M',
    '-Xmx2048M',
    '-XX:+DisableAttachMechanism',
    '-Xshare:off',
    '--add-opens', 'java.desktop/java.awt.event=ALL-UNNAMED',
    '--add-opens', 'java.desktop/sun.awt=ALL-UNNAMED',
    '--add-opens', 'java.management/sun.management=ALL-UNNAMED',
    '-Dcosmic.offline=true',
    `-javaagent:${agentJar}`,
    '-jar', clientJar,
    '--version', '1.8',
    '--assetsDir', assetsDir,
    '--assetIndex', '1.8',
    '--username', 'CosmicPlayer',
    '--uuid', '00000000-0000-0000-0000-000000000000',
    '--accessToken', 'offline',
    '--userProperties', '{}',
    '--width', '1280',
    '--height', '720',
    '--novid'
];

console.log('Testing launch from CosmicClient-x64 directory...');
const proc = spawn(javaExe, args, { cwd: installDir, stdio: ['ignore', 'pipe', 'pipe'] });

let agentSeen = false;
proc.stdout.on('data', (d) => {
    const s = d.toString();
    if (s.includes('[CosmicAgent]')) {
        agentSeen = true;
        console.log('AGENT:', s.trim());
    }
});

proc.stderr.on('data', (d) => {
    const err = d.toString().trim();
    if (err && !err.includes('Picked up _JAVA_OPTIONS')) {
        console.error('STDERR:', err);
    }
});

setTimeout(() => {
    console.log('==============================================');
    console.log('=== TEST RESULT FROM CosmicClient-x64 ===');
    console.log('CosmicAgent Hooked in CosmicClient-x64:', agentSeen);
    console.log('==============================================');
    proc.kill();
    process.exit(0);
}, 6000);
