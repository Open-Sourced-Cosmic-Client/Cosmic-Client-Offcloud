const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const zlib = require('zlib');
const tar = require('tar');

const MANIFEST_URL = 'https://assets.cosmicclient.com/cosmic/manifest.json';
const ASSET_INDEX_URL = 'https://assets.cosmicclient.com/cosmic/assets/1.8.json';
const TARGET_ARCHIVE_DIR = path.join(__dirname, '..', 'offline-archive');
const COSMIC_CLIENT_DIR = path.join(__dirname, '..', 'CosmicClient-x64');

function downloadFile(url, destPath) {
    return new Promise((resolve, reject) => {
        fs.mkdirSync(path.dirname(destPath), { recursive: true });
        const client = url.startsWith('https') ? https : http;

        const req = client.get(url, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                return downloadFile(res.headers.location, destPath).then(resolve).catch(reject);
            }
            if (res.statusCode !== 200) {
                return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
            }

            const fileStream = fs.createWriteStream(destPath);
            let downloaded = 0;
            res.on('data', chunk => {
                downloaded += chunk.length;
            });
            res.pipe(fileStream);
            fileStream.on('finish', () => {
                fileStream.close();
                resolve(downloaded);
            });
            fileStream.on('error', reject);
        });
        req.on('error', reject);
    });
}

async function extractTarGz(archivePath, destDir) {
    fs.mkdirSync(destDir, { recursive: true });
    return new Promise((resolve, reject) => {
        const stream = fs.createReadStream(archivePath)
            .pipe(zlib.createGunzip())
            .pipe(tar.x({ cwd: destDir }));

        stream.on('finish', resolve);
        stream.on('error', reject);
    });
}

async function main() {
    console.log('=== COSMIC CLIENT OFFLINE ARCHIVER & MACBOOK SYNC ===');
    fs.mkdirSync(TARGET_ARCHIVE_DIR, { recursive: true });

    // 1. Fetch manifest
    console.log('[1/4] Fetching manifest from', MANIFEST_URL);
    const manifestPath = path.join(TARGET_ARCHIVE_DIR, 'manifest.json');
    await downloadFile(MANIFEST_URL, manifestPath);
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    console.log(`Manifest loaded: ${manifest.files.length} files listed.`);

    // 2. Fetch 1.8.json asset index
    console.log('[2/4] Downloading asset index 1.8.json...');
    const assetIndexPath = path.join(TARGET_ARCHIVE_DIR, '1.8.json');
    await downloadFile(ASSET_INDEX_URL, assetIndexPath);
    fs.copyFileSync(assetIndexPath, path.join(COSMIC_CLIENT_DIR, '1.8.json'));
    console.log('1.8.json saved.');

    // 3. Download all files listed in manifest
    console.log('[3/4] Downloading all cross-platform files (Windows, Mac, Linux)...');
    for (let i = 0; i < manifest.files.length; i++) {
        const file = manifest.files[i];
        const filename = path.basename(file.url);
        const archiveFile = path.join(TARGET_ARCHIVE_DIR, filename);

        console.log(`[${i + 1}/${manifest.files.length}] Downloading ${file.id} (${(file.size / 1024 / 1024).toFixed(1)} MB)...`);
        
        let needsDownload = true;
        if (fs.existsSync(archiveFile)) {
            const stat = fs.statSync(archiveFile);
            if (stat.size === file.size) {
                console.log(` -> Already cached in offline-archive: ${filename}`);
                needsDownload = false;
            }
        }

        if (needsDownload) {
            await downloadFile(file.url, archiveFile);
            console.log(` -> Downloaded ${filename}`);
        }

        // Also unpack Mac and cross-platform files into CosmicClient-x64 structure
        if (file.id === 'natives-mac-aarch64' || file.id === 'natives-mac-x64') {
            const macNativesDir = path.join(COSMIC_CLIENT_DIR, '1.8', 'bin-mac');
            console.log(` -> Extracting Mac natives to ${macNativesDir}`);
            await extractTarGz(archiveFile, macNativesDir);
        }

        if (file.id === 'java-mac-aarch64') {
            const macJavaDir = path.join(COSMIC_CLIENT_DIR, 'bootstrap', 'java-mac-arm64');
            console.log(` -> Extracting Mac ARM64 Java to ${macJavaDir}`);
            await extractTarGz(archiveFile, macJavaDir);
        }

        if (file.id === 'java-mac-x64') {
            const macJavaX64Dir = path.join(COSMIC_CLIENT_DIR, 'bootstrap', 'java-mac-x64');
            console.log(` -> Extracting Mac x64 Java to ${macJavaX64Dir}`);
            await extractTarGz(archiveFile, macJavaX64Dir);
        }
    }

    console.log('=== OFFLINE SYNC COMPLETED SUCCESSFULLY ===');
}

main().catch(err => {
    console.error('Fatal error:', err);
    process.exit(1);
});
