const fs = require('fs');
const path = require('path');

/**
 * Scan a JAR or binary file for all embedded HTTP/HTTPS URLs.
 *
 * Usage:
 *   node scan-urls.js [path-to-jar]
 */
function scanFile(filePath) {
    if (!fs.existsSync(filePath)) {
        console.error(`[Error] File not found: ${filePath}`);
        return [];
    }
    const buf = fs.readFileSync(filePath);
    const str = buf.toString('latin1');
    const matches = str.match(/https?:\/\/[a-zA-Z0-9\.\-\_\:\@\/\?\=\&\%\#]+/g) || [];
    return [...new Set(matches)];
}

const targetFile = process.argv[2] || path.join(__dirname, '..', 'CosmicClient-x64', '1.8', 'CosmicClient-1.8.9.jar');
console.log(`Scanning URLs in: ${targetFile}`);
const results = scanFile(targetFile);
console.log(`Found ${results.length} unique URLs:`);
results.forEach(url => console.log(' - ' + url));
