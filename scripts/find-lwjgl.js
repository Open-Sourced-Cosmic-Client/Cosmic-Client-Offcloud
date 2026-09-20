const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const jarTool = '"C:\\Program Files\\Java\\jdk-21\\bin\\jar.exe"';

function checkJar(p) {
    try {
        const out = execSync(`${jarTool} tf "${p}"`).toString();
        const hasSys = out.includes('org/lwjgl/Sys.class');
        const hasUtil = out.includes('org/lwjgl/LWJGLUtil.class');
        if (hasSys || hasUtil) {
            console.log('FOUND LWJGL IN:', p, '(Sys:', hasSys, 'Util:', hasUtil, ')');
        }
    } catch(e) {}
}

function walk(d) {
    for (const f of fs.readdirSync(d)) {
        const full = path.join(d, f);
        if (fs.statSync(full).isDirectory()) {
            walk(full);
        } else if (f.endsWith('.jar')) {
            checkJar(full);
        }
    }
}

console.log('Scanning all JAR files in workspace...');
walk('CosmicClient-x64');
if (fs.existsSync('cosmic-agent.jar')) checkJar('cosmic-agent.jar');
console.log('Done scanning.');
