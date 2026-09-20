const fs = require('fs');
const path = require('path');

const cleanTemplate = JSON.stringify({
  "profiles": {},
  "settings": {},
  "version": 4,
  "authenticationDatabase": {
    "00000000-0000-0000-0000-000000000001": {
      "username": "CosmicPlayer",
      "profiles": {
        "00000000-0000-0000-0000-000000000001": {
          "displayName": "CosmicPlayer"
        }
      },
      "type": "offline",
      "accessToken": "offline_token_cosmic",
      "isOffline": true
    }
  },
  "clientToken": "00000000-0000-0000-0000-000000000000",
  "selectedUser": {
    "account": "00000000-0000-0000-0000-000000000001",
    "profile": "00000000-0000-0000-0000-000000000001"
  }
}, null, 2);

const root = path.resolve(__dirname, '..');
const explicitPaths = [
  path.join(root, 'accounts.json'),
  path.join(root, 'cosmic', 'accounts.json'),
  path.join(root, 'CosmicClient-x64', 'accounts.json'),
  path.join(root, 'CosmicClient-x64', 'cosmic', 'accounts.json'),
  path.join(root, 'Mac', 'accounts.json'),
  path.join(root, 'Mac', 'CosmicClient-x64', 'accounts.json'),
  path.join(root, 'Mac', 'CosmicClient-x64', 'cosmic', 'accounts.json'),
  path.join(root, 'Source Code', 'accounts.json'),
  path.join(root, 'Windows', 'accounts.json'),
  path.join(root, 'Windows', 'CosmicClient-x64', 'accounts.json'),
  path.join(root, 'Windows', 'CosmicClient-x64', 'cosmic', 'accounts.json'),
  path.join(root, 'Windows-x32', 'accounts.json'),
  path.join(root, 'Windows-x32', 'CosmicClient-x64', 'accounts.json'),
  path.join(root, 'Windows-x32', 'CosmicClient-x64', 'cosmic', 'accounts.json'),
  path.join(root, 'Windows-x64', 'accounts.json'),
  path.join(root, 'Windows-x64', 'CosmicClient-x64', 'accounts.json'),
  path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'accounts.json'),
  path.join(process.env.APPDATA || '', '.minecraft', 'cosmic', 'cosmic', 'accounts.json')
];

let cleaned = 0;
for (const p of explicitPaths) {
  try {
    if (fs.existsSync(p)) {
      fs.writeFileSync(p, cleanTemplate, 'utf-8');
      console.log(`[Cleaned] ${p}`);
      cleaned++;
    }
  } catch (e) {
    console.error(`[Error] ${p}: ${e.message}`);
  }
}
console.log(`Successfully sanitized ${cleaned} accounts.json files with CosmicPlayer offline template.`);
