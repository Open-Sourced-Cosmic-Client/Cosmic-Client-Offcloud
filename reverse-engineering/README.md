# Reverse Engineering & Developer Tools

This directory contains modular utility scripts and tooling used to inspect, reverse-engineer, and test Cosmic Client JARs and Java bytecode transformations.

---

## Tooling Inventory

| Script | Purpose | Usage |
| :--- | :--- | :--- |
| `scan-urls.js` | Scans any `.jar` file for embedded HTTP/HTTPS URLs, domains, and API endpoints. | `node scan-urls.js <path-to-jar>` |
| `inspect-classes.js` | Inspects class definitions, fields, and method signatures inside a JAR without extracting. | `node inspect-classes.js <path-to-jar> [className]` |
| `extract-resources.js` | Extracts resources, textures, and sound manifests from client JARs. | `node extract-resources.js <path-to-jar> <output-dir>` |
| `download-all-offline.js` | Downloads and verifies offline asset manifests from Minecraft/Cosmic endpoints. | `node download-all-offline.js` |
| `test-agent-launch.js` | Spawns Cosmic Client with Java Agent attached and logs bytecode transformation hooks. | `node test-agent-launch.js` |
| `test-folder-launch.js` | Direct folder launcher test runner for `CosmicClient-x64`. | `node test-folder-launch.js` |

---

## Guidelines for Open Source Development

1. **No Credentials in Tools**: Never hardcode personal access tokens, refresh tokens, or account passwords into test scripts.
2. **Local Loopback Only**: All mock servers and URL redirects should point to `127.0.0.1` or `localhost`.
3. **Isolated Builds**: This directory is excluded from production `.exe` bundles via `.gitignore` and `package.json`.
