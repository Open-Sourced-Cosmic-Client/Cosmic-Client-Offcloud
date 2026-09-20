const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const zlib = require('zlib');
const tar = require('tar');
const { execSync, spawnSync } = require('child_process');

const JRE_DOWNLOAD_MAP = {
    'win32-x64': {
        name: 'Zulu 19 JRE (Windows x64)',
        url: 'https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-win_x64.zip',
        format: 'zip',
        destSubdir: 'java',
        binRelPath: path.join('bin', 'java.exe')
    },
    'darwin-arm64': {
        name: 'Zulu 19 JRE (macOS Apple Silicon ARM64)',
        url: 'https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-macosx_aarch64.tar.gz',
        format: 'tar.gz',
        destSubdir: 'java-mac-arm64',
        binRelPath: path.join('zulu-19.jre', 'Contents', 'Home', 'bin', 'java')
    },
    'darwin-x64': {
        name: 'Zulu 19 JRE (macOS Intel x64)',
        url: 'https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-macosx_x64.tar.gz',
        format: 'tar.gz',
        destSubdir: 'java-mac-x64',
        binRelPath: path.join('zulu-19.jre', 'Contents', 'Home', 'bin', 'java')
    },
    'linux-x64': {
        name: 'Zulu 19 JRE (Linux x64)',
        url: 'https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-linux_x64.tar.gz',
        format: 'tar.gz',
        destSubdir: 'java-linux',
        binRelPath: path.join('bin', 'java')
    }
};

/**
 * Check if a Java binary is working by executing -version.
 */
function testJavaExecutable(binPath) {
    if (!binPath) return false;
    try {
        if (binPath !== 'java' && !fs.existsSync(binPath)) return false;
        const res = spawnSync(binPath, ['-version'], { stdio: 'pipe', encoding: 'utf-8', timeout: 5000 });
        const output = (res.stderr || '') + (res.stdout || '');
        return output.includes('version') || output.includes('Runtime') || output.includes('OpenJDK') || output.includes('Zulu') || output.includes('HotSpot');
    } catch (e) {
        return false;
    }
}

/**
 * Download a file with redirect support and progress callbacks.
 */
function downloadFile(url, destPath, onProgress = () => {}) {
    return new Promise((resolve, reject) => {
        fs.mkdirSync(path.dirname(destPath), { recursive: true });
        const client = url.startsWith('https') ? https : http;

        const req = client.get(url, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return downloadFile(res.headers.location, destPath, onProgress).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) {
                return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
            }

            const totalBytes = parseInt(res.headers['content-length'] || '0', 10);
            let downloadedBytes = 0;
            const fileStream = fs.createWriteStream(destPath);

            res.on('data', (chunk) => {
                downloadedBytes += chunk.length;
                if (totalBytes > 0) {
                    const percent = Math.round((downloadedBytes / totalBytes) * 100);
                    onProgress({ downloadedBytes, totalBytes, percent });
                } else {
                    onProgress({ downloadedBytes, totalBytes: 0, percent: 0 });
                }
            });

            res.pipe(fileStream);

            fileStream.on('finish', () => {
                fileStream.close();
                resolve(destPath);
            });

            fileStream.on('error', (err) => {
                try { fs.unlinkSync(destPath); } catch (e) {}
                reject(err);
            });
        });

        req.on('error', (err) => {
            try { fs.unlinkSync(destPath); } catch (e) {}
            reject(err);
        });
    });
}

/**
 * Extract .tar.gz archive.
 */
function extractTarGz(archivePath, destDir) {
    fs.mkdirSync(destDir, { recursive: true });
    return new Promise((resolve, reject) => {
        const stream = fs.createReadStream(archivePath)
            .pipe(zlib.createGunzip())
            .pipe(tar.x({ cwd: destDir }));

        stream.on('finish', resolve);
        stream.on('error', reject);
    });
}

/**
 * Extract .zip archive using system utilities or PowerShell on Windows.
 */
function extractZip(archivePath, destDir) {
    fs.mkdirSync(destDir, { recursive: true });
    if (process.platform === 'win32') {
        try {
            execSync(`tar -xf "${archivePath}" -C "${destDir}"`, { stdio: 'ignore' });
            return;
        } catch (e) {
            execSync(`powershell -Command "Expand-Archive -Path '${archivePath}' -DestinationPath '${destDir}' -Force"`, { stdio: 'ignore' });
            return;
        }
    } else {
        execSync(`unzip -q -o "${archivePath}" -d "${destDir}"`, { stdio: 'ignore' });
    }
}

/**
 * Find java executable recursively within an extracted directory.
 */
function findJavaInDir(dirPath) {
    if (!fs.existsSync(dirPath)) return null;
    const isWin = process.platform === 'win32';
    const targetName = isWin ? 'java.exe' : 'java';

    function walk(current) {
        const entries = fs.readdirSync(current, { withFileTypes: true });
        for (const entry of entries) {
            const full = path.join(current, entry.name);
            if (entry.isDirectory()) {
                const found = walk(full);
                if (found) return found;
            } else if (entry.name.toLowerCase() === targetName.toLowerCase() && path.basename(path.dirname(full)).toLowerCase() === 'bin') {
                return full;
            }
        }
        return null;
    }

    return walk(dirPath);
}

/**
 * Automatically download, extract, and configure the appropriate Java Runtime if missing.
 *
 * @param {string} installDir - Directory of CosmicClient-x64
 * @param {function} onLog - Logging callback
 * @param {function} onProgress - Download progress callback
 * @returns {Promise<string>} - The path to the verified java executable
 */
async function ensureJava(installDir, onLog = () => {}, onProgress = () => {}) {
    const cosmic = require('./cosmic');
    const existingJava = cosmic.getJavaBinary();

    if (existingJava && testJavaExecutable(existingJava)) {
        return existingJava;
    }

    const plat = process.platform;
    const arch = process.arch;
    const key = `${plat}-${arch}`;
    const targetInfo = JRE_DOWNLOAD_MAP[key] || JRE_DOWNLOAD_MAP['win32-x64'];

    onLog(`[JavaDownloader] No working Java runtime found on system.`, 'warn');
    onLog(`[JavaDownloader] Starting automatic download of ${targetInfo.name}...`, 'launcher');

    const bootstrapDir = path.join(installDir, 'bootstrap');
    fs.mkdirSync(bootstrapDir, { recursive: true });

    const tempArchive = path.join(bootstrapDir, `temp_jre_${Date.now()}.${targetInfo.format === 'zip' ? 'zip' : 'tar.gz'}`);
    const targetExtractDir = path.join(bootstrapDir, targetInfo.destSubdir);

    try {
        let lastReportedPercent = -1;
        await downloadFile(targetInfo.url, tempArchive, (prog) => {
            if (prog.percent && prog.percent !== lastReportedPercent && prog.percent % 10 === 0) {
                lastReportedPercent = prog.percent;
                onLog(`[JavaDownloader] Downloading Java JRE: ${prog.percent}% (${(prog.downloadedBytes / 1024 / 1024).toFixed(1)} MB)...`, 'launcher');
            }
            onProgress(prog);
        });

        onLog(`[JavaDownloader] Download completed. Extracting JRE package to ${targetInfo.destSubdir}...`, 'launcher');

        if (targetInfo.format === 'zip') {
            extractZip(tempArchive, targetExtractDir);
        } else {
            await extractTarGz(tempArchive, targetExtractDir);
        }

        // Clean up temp archive
        try { fs.unlinkSync(tempArchive); } catch (e) {}

        // Locate java executable in extracted directory
        const discoveredJava = findJavaInDir(targetExtractDir);
        if (!discoveredJava) {
            throw new Error(`Could not locate java binary in extracted directory: ${targetExtractDir}`);
        }

        // Set executable permissions on macOS / Linux
        if (process.platform !== 'win32') {
            try {
                execSync(`chmod +x "${discoveredJava}"`, { stdio: 'ignore' });
            } catch (e) {}
        }

        if (testJavaExecutable(discoveredJava)) {
            onLog(`[JavaDownloader] Java successfully installed and verified: ${discoveredJava}`, 'launcher');
            return discoveredJava;
        } else {
            throw new Error(`Installed Java binary failed test execution: ${discoveredJava}`);
        }

    } catch (err) {
        onLog(`[JavaDownloader] Automatic Java installation failed: ${err.message}`, 'error');
        try { if (fs.existsSync(tempArchive)) fs.unlinkSync(tempArchive); } catch (e) {}
        throw err;
    }
}

module.exports = {
    ensureJava,
    testJavaExecutable,
    findJavaInDir,
    downloadFile
};
