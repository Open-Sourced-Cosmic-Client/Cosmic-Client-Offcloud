const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

console.log('========================================================');
console.log('  Compiling Cosmic Agent (Pure Clean Source)');
console.log('========================================================');

const rootDir = __dirname;
const srcDir = path.join(rootDir, 'src', 'main', 'java');
const resDir = path.join(rootDir, 'src', 'main', 'resources');
const libsDir = path.join(rootDir, 'libs');
const outClassesDir = path.join(rootDir, 'target', 'classes');
const targetJar = path.join(rootDir, 'target', 'cosmic-agent.jar');
const outputRootJar = path.join(rootDir, '..', 'cosmic-agent.jar');

// Clean target directory
fs.rmSync(path.join(rootDir, 'target'), { recursive: true, force: true });
fs.mkdirSync(outClassesDir, { recursive: true });

// Collect all .java files
function getAllJavaFiles(dir) {
    let results = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            results = results.concat(getAllJavaFiles(fullPath));
        } else if (entry.name.endsWith('.java')) {
            results.push(fullPath);
        }
    }
    return results;
}

const javaFiles = getAllJavaFiles(srcDir);
console.log(`[Build] Found ${javaFiles.length} Java source files to compile.`);

// Write sources list for javac
const sourcesListFile = path.join(rootDir, 'target', 'sources.txt');
fs.writeFileSync(sourcesListFile, javaFiles.map(f => '"' + f.replace(/\\/g, '/') + '"').join('\n'), 'utf8');

// Locate Java compiler (javac) and jar tool
function findTool(toolName) {
    const isWin = process.platform === 'win32';
    const isMac = process.platform === 'darwin';
    const binary = isWin ? `${toolName}.exe` : toolName;

    const candidates = [
        path.join(rootDir, '..', 'CosmicClient-x64', 'bootstrap', 'java', 'bin', binary),
        isWin ? `C:\\Program Files\\Java\\jdk-21\\bin\\${binary}` : null,
        isWin ? `C:\\Program Files\\Java\\jdk-17\\bin\\${binary}` : null,
        isWin ? `C:\\Program Files\\Java\\jdk-22\\bin\\${binary}` : null,
        isWin ? `C:\\Program Files\\Java\\jdk-24\\bin\\${binary}` : null,
        isWin ? path.join(process.env.USERPROFILE || '', '.jdks', 'corretto-21.0.11', 'bin', binary) : null,
        isMac ? `/Library/Java/JavaVirtualMachines/zulu-19.jdk/Contents/Home/bin/${binary}` : null,
        isMac ? `/opt/homebrew/opt/openjdk/bin/${binary}` : null,
        isMac ? `/usr/local/opt/openjdk/bin/${binary}` : null
    ].filter(Boolean);

    for (const c of candidates) {
        if (fs.existsSync(c)) return `"${c}"`;
    }
    return toolName;
}

const javac = findTool('javac');
const jarTool = findTool('jar');

console.log(`[Build] Using javac: ${javac}`);
console.log(`[Build] Using jar:   ${jarTool}`);

// Classpath with libs
const jnaJar = path.join(libsDir, 'jna-5.13.0.jar');
const cpArg = fs.existsSync(jnaJar) ? `-cp "${jnaJar}"` : '';

console.log(`[Build] Compiling Java classes with --release 8...`);
try {
    execSync(`${javac} --release 8 -encoding UTF-8 ${cpArg} -d "${outClassesDir}" @"${sourcesListFile}"`, { stdio: 'inherit' });
    console.log('[Build] Compilation succeeded!');
} catch (e) {
    console.error('[Build] Compilation failed:', e.message);
    process.exit(1);
}

// Extract library classes into target classes so the output is a standalone fat JAR
if (fs.existsSync(jnaJar)) {
    console.log('[Build] Unpacking JNA library dependencies into bundle...');
    execSync(`${jarTool} xf "${jnaJar}"`, { cwd: outClassesDir });
    // Remove unwanted JNA META-INF signature files
    fs.rmSync(path.join(outClassesDir, 'META-INF', 'MANIFEST.MF'), { force: true });
}

// Copy resources into classes directory
function copyRecursive(src, dest) {
    if (!fs.existsSync(src)) return;
    fs.mkdirSync(dest, { recursive: true });
    for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
        const s = path.join(src, entry.name);
        const d = path.join(dest, entry.name);
        if (entry.isDirectory()) {
            copyRecursive(s, d);
        } else {
            fs.copyFileSync(s, d);
        }
    }
}

console.log('[Build] Packaging resources & natives...');
copyRecursive(resDir, outClassesDir);

const manifestPath = path.join(resDir, 'META-INF', 'MANIFEST.MF');
console.log(`[Build] Creating final JAR with manifest: ${manifestPath}...`);
execSync(`${jarTool} cfm "${targetJar}" "${manifestPath}" -C "${outClassesDir}" .`, { stdio: 'inherit' });

// Copy directly to workspace, CosmicClient-x64, AppData and other platforms
const clientFolderJar = path.join(rootDir, '..', 'CosmicClient-x64', 'cosmic-agent.jar');
const syncDestinations = [

    outputRootJar,
    clientFolderJar,
    path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'dist', 'win-unpacked', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'dist', 'win-unpacked', 'resources', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows', 'CosmicClient-x64', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows', 'dist', 'win-unpacked', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows', 'dist', 'win-unpacked', 'resources', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows-x64', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows-x64', 'CosmicClient-x64', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows-x64', 'dist', 'win-unpacked', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows-x64', 'dist', 'win-unpacked', 'resources', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows-x32', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Windows-x32', 'CosmicClient-x64', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Source Code', 'cosmic-agent.jar'),
    path.join(rootDir, '..', 'Cosmic Client.app', 'Contents', 'Resources', 'CosmicClient-x64', 'cosmic-agent.jar')
];

for (const dest of syncDestinations) {
    try {
        if (fs.existsSync(path.dirname(dest))) {
            fs.copyFileSync(targetJar, dest);
            console.log(`[Build] Synced to: ${dest}`);
        }
    } catch (e) {}
}


// Clean temporary compilation files to prevent local path leaks
try {
    fs.rmSync(sourcesListFile, { force: true });
} catch (e) {}

console.log(`[Build] ========================================================`);
console.log(`[Build] Successfully compiled and packaged:`);
console.log(`[Build] ${outputRootJar} (${(fs.statSync(outputRootJar).size / (1024 * 1024)).toFixed(2)} MB)`);
console.log(`[Build] ${clientFolderJar}`);
console.log(`[Build] ========================================================`);

