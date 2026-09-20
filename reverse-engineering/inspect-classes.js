const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Inspect contents, class files, and package structure of a JAR.
 *
 * Usage:
 *   node inspect-classes.js [path-to-jar] [filter-query]
 */
function inspectJar(jarPath, filterQuery = '') {
    if (!fs.existsSync(jarPath)) {
        console.error(`[Error] JAR file not found: ${jarPath}`);
        return;
    }

    try {
        const output = execSync(`jar tf "${jarPath}"`, { encoding: 'utf-8', maxBuffer: 10 * 1024 * 1024 });
        const lines = output.split(/\r?\n/).filter(Boolean);
        
        let filtered = lines;
        if (filterQuery) {
            filtered = lines.filter(l => l.toLowerCase().includes(filterQuery.toLowerCase()));
        }

        console.log(`=======================================================`);
        console.log(`  JAR Inspection: ${path.basename(jarPath)}`);
        console.log(`  Total Entries:  ${lines.length}`);
        console.log(`  Filtered Match: ${filtered.length} (Filter: "${filterQuery}")`);
        console.log(`=======================================================`);
        
        filtered.slice(0, 50).forEach(l => console.log('  ' + l));
        if (filtered.length > 50) {
            console.log(`  ... and ${filtered.length - 50} more entries.`);
        }
    } catch (err) {
        console.error(`[Error] Failed to read JAR: ${err.message}`);
    }
}

const targetJar = process.argv[2] || path.join(__dirname, '..', 'CosmicClient-x64', '1.8', 'CosmicClient-1.8.9.jar');
const filter = process.argv[3] || '';

inspectJar(targetJar, filter);
