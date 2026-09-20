const fs = require('fs');
const path = require('path');
const archiver = require('archiver');

async function createZip(macDir, outZip) {
    if (fs.existsSync(outZip)) {
        try { fs.unlinkSync(outZip); } catch(e){}
    }

    console.log(`[ZipMac] Creating ${path.basename(outZip)} with native POSIX executable permissions...`);
    const output = fs.createWriteStream(outZip);
    const archive = archiver('zip', {
        zlib: { level: 6 }
    });

    return new Promise((resolve, reject) => {
        output.on('close', () => {
            const sizeMB = (archive.pointer() / 1024 / 1024).toFixed(2);
            console.log(`[ZipMac] Success! Created ${outZip} (${sizeMB} MB, ${archive.pointer()} bytes)`);
            resolve();
        });

        archive.on('warning', (err) => {
            if (err.code === 'ENOENT') {
                console.warn('[ZipMac] Warning:', err);
            } else {
                reject(err);
            }
        });

        archive.on('error', (err) => reject(err));

        archive.pipe(output);

        archive.directory(macDir, false, (data) => {
            const rel = data.name.replace(/\\/g, '/');
            let isExec = false;
            if (rel.endsWith('.sh') ||
                rel.endsWith('.command') ||
                rel.endsWith('CosmicLauncher') ||
                rel.endsWith('.dylib') ||
                rel.endsWith('/java') ||
                rel.endsWith('.exe')) {
                isExec = true;
            }
            if (rel.includes('bin-mac') || rel.includes('Contents/MacOS/')) {
                isExec = true;
            }

            if (data.stats && data.stats.isDirectory()) {
                data.mode = 0o755;
            } else {
                data.mode = isExec ? 0o755 : 0o644;
            }
            return data;
        });

        archive.finalize();
    });
}

async function zipMac() {
    const rootDir = path.resolve(__dirname, '..');
    const macDir = path.join(rootDir, 'Mac');
    const outZip1 = path.join(rootDir, 'Cosmic-Client-Mac.zip');
    const outZip2 = path.join(rootDir, 'Mac.zip');

    if (!fs.existsSync(macDir)) {
        throw new Error('Mac directory does not exist!');
    }

    await createZip(macDir, outZip1);
    try {
        fs.copyFileSync(outZip1, outZip2);
        console.log(`[ZipMac] Mirrored to ${outZip2}`);
    } catch (e) {
        await createZip(macDir, outZip2);
    }
}

zipMac().catch(err => {
    console.error('[ZipMac] Error:', err);
    process.exit(1);
});
