const fs = require('fs');
const path = require('path');
const archiver = require('archiver');

async function createZip(winDir, outZip) {
    if (fs.existsSync(outZip)) {
        try { fs.unlinkSync(outZip); } catch (e) {}
    }

    console.log(`[ZipWin] Creating ${path.basename(outZip)}...`);
    const output = fs.createWriteStream(outZip);
    const archive = archiver('zip', {
        zlib: { level: 6 }
    });

    return new Promise((resolve, reject) => {
        output.on('close', () => {
            const sizeMB = (archive.pointer() / 1024 / 1024).toFixed(2);
            console.log(`[ZipWin] Success! Created ${outZip} (${sizeMB} MB, ${archive.pointer()} bytes)`);
            resolve();
        });

        archive.on('warning', (err) => {
            if (err.code === 'ENOENT') {
                console.warn('[ZipWin] Warning:', err);
            } else {
                reject(err);
            }
        });

        archive.on('error', (err) => reject(err));

        archive.pipe(output);

        archive.directory(winDir, false);

        archive.finalize();
    });
}

async function zipWindows() {
    const rootDir = path.resolve(__dirname, '..');
    const winDir = path.join(rootDir, 'Windows-x64');
    const outZip1 = path.join(rootDir, 'Cosmic-Client-Windows-x64.zip');
    const outZip2 = path.join(rootDir, 'Windows-x64.zip');

    if (!fs.existsSync(winDir)) {
        throw new Error('Windows-x64 directory does not exist!');
    }

    await createZip(winDir, outZip1);
    try {
        fs.copyFileSync(outZip1, outZip2);
        console.log(`[ZipWin] Mirrored to ${outZip2}`);
    } catch (e) {
        await createZip(winDir, outZip2);
    }
}

zipWindows().catch(err => {
    console.error('[ZipWin] Error:', err);
    process.exit(1);
});
