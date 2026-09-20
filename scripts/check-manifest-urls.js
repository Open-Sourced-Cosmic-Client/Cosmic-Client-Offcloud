const fs = require('fs');
const path = require('path');

function checkManifests(dir) {
    if (!fs.existsSync(dir)) return;
    for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
        if (f.name === 'node_modules' || f.name === '.git') continue;
        const p = path.join(dir, f.name);
        if (f.isDirectory()) {
            checkManifests(p);
        } else if (f.name.toLowerCase() === 'manifest.json') {
            const c = fs.readFileSync(p, 'utf8');
            const matches = c.match(/https?:\/\/[^"\s]+/g) || [];
            const domains = [...new Set(matches.map(m => new URL(m).host))];
            console.log(p);
            console.log('  Domains found:', domains);
        }
    }
}

checkManifests('.');
