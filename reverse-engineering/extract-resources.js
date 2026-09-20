const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Extract specific resource patterns (assets, textures, jsons) from a JAR into an output directory.
 *
 * Usage:
 *   node extract-resources.js [path-to-jar] [output-dir] [filter-prefix]
 */
function extractResources(jarPath, outDir, filterPrefix = 'assets/') {
    if (!fs.existsSync(jarPath)) {
        console.error(`[Error] JAR file not found: ${jarPath}`);
        return;
    }

    if (!fs.existsSync(outDir)) {
        fs.mkdirSync(outDir, { recursive: true });
    }

    try {
        console.log(`Extracting entries matching "${filterPrefix}" from ${jarPath} into ${outDir}...`);
        const list = execSync(`jar tf "${jarPath}"`, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
        const entries = list.split(/\r?\n/).filter(l => l.startsWith(filterPrefix));

        console.log(`Found ${entries.length} matching entries to extract.`);
        if (entries.length === 0) return;

        execSync(`jar xf "${jarPath}" ${entries.slice(0, 100).join(' ')}`, { cwd: outDir });
        console.log(`Extraction complete.`);
    } catch (err) {
        console.error(`[Error] Extraction failed: ${err.message}`);
    }
}

const targetJar = process.argv[2] || path.join(__dirname, '..', 'CosmicClient-x64', '1.8', 'CosmicClient-1.8.9.jar');
const targetOut = process.argv[3] || path.join(__dirname, 'extracted-output');
const prefix = process.argv[4] || 'assets/minecraft/cosmic/';

extractResources(targetJar, targetOut, prefix);
