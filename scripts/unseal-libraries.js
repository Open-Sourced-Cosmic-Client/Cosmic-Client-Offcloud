const { execSync } = require('child_process');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');

const pyScript = `
import zipfile, os, shutil

def unseal_all(base_dir):
    count = 0
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith('.jar'):
                jar_path = os.path.join(root, f)
                try:
                    with zipfile.ZipFile(jar_path, 'r') as zin:
                        if 'META-INF/MANIFEST.MF' not in zin.namelist():
                            continue
                        manifest_text = zin.read('META-INF/MANIFEST.MF').decode('utf-8', errors='ignore')
                        if 'sealed:' not in manifest_text.lower():
                            continue
                    
                    temp_jar = jar_path + '.tmp'
                    with zipfile.ZipFile(jar_path, 'r') as zin:
                        with zipfile.ZipFile(temp_jar, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
                            for item in zin.infolist():
                                buf = zin.read(item.filename)
                                if item.filename == 'META-INF/MANIFEST.MF':
                                    m_text = buf.decode('utf-8', errors='ignore')
                                    new_lines = [l for l in m_text.splitlines() if not l.lower().startswith('sealed:')]
                                    buf = '\\r\\n'.join(new_lines).encode('utf-8') + b'\\r\\n'
                                zout.writestr(item, buf)
                    shutil.move(temp_jar, jar_path)
                    print(f'[Unsealed] {jar_path}')
                    count += 1
                except Exception as e:
                    print(f'[Error] {jar_path}: {e}')
    print(f'Total JARs unsealed: {count}')

unseal_all('${ROOT.replace(/\\/g, '/')}')
`;

console.log('[Unseal] Scanning and unsealing all JARs across workspace...');
try {
    execSync(`python -c "${pyScript.replace(/\n/g, ' ')}"`, { stdio: 'inherit' });
    console.log('[Unseal] All libraries unsealed successfully.');
} catch (e) {
    console.error('[Unseal] Error:', e.message);
}
