import os
import sys
import zipfile
import time

base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
output_zip = os.path.join(base_dir, "installer-payload.zip")

skip_filenames = {
    "jfxwebkit.dll",
    "gstreamer-lite.dll",
    "glib-lite.dll",
    "jfxmedia.dll",
    "fxplugins.dll",
    "jaccessinspector.exe",
    "jaccesswalker.exe",
    "jabswitch.exe",
    ".ds_store",
    "thumbs.db"
}

def should_skip(filename, rel_path):
    fname = filename.lower()
    if fname.startswith("._") or fname.endswith(".pdb") or fname.endswith(".bak"):
        return True
    if fname in skip_filenames:
        return True
    norm_rel = rel_path.replace("\\", "/").lower()
    if "/skins/" in norm_rel or norm_rel.startswith("skins/"):
        return True
    if "/legal/" in norm_rel or norm_rel.startswith("legal/"):
        return True
    return False

def build_payload():
    print("=" * 60)
    print("=== BUILDING MINIMIZED ULTRA-COMPRESSED OFFLINE VAULT ===")
    print("=" * 60)
    start_time = time.time()
    
    if os.path.exists(output_zip):
        os.remove(output_zip)

    file_count = 0
    total_uncompressed = 0

    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        # 1. Single core files
        core_files = [
            ("cosmic-agent.jar", "cosmic-agent.jar"),
            ("CosmicClient-x64/Launcher.jar", "Launcher.jar"),
            ("CosmicClient-x64/1.8/CosmicClient-1.8.9.jar", "1.8/CosmicClient-1.8.9.jar"),
            ("CosmicClient-x64/1.8.json", "1.8.json"),
            ("CosmicClientLauncher.exe", "CosmicClientLauncher.exe"),
            ("CosmicClient-Direct.exe", "CosmicClient-Direct.exe"),
            ("START-HERE-WINDOWS.bat", "START-HERE-WINDOWS.bat"),
            ("Launch-GUI.bat", "Launch-GUI.bat"),
            ("Launch-Cosmic-Direct.bat", "Launch-Cosmic-Direct.bat"),
            ("Launch-Cosmic-Direct-x32.bat", "Launch-Cosmic-Direct-x32.bat"),
            ("accounts.json", "accounts.json"),
            ("cosmic.ico", "cosmic.ico"),
            ("cosmic-icon.png", "cosmic-icon.png"),
            ("CosmicClient-x64/launcher-config.json", "launcher-config.json"),
            ("CosmicClient-x64/optionscosmic.txt", "optionscosmic.txt"),
            ("CosmicClient-x64/optionsof.txt", "optionsof.txt"),
        ]

        print("-> Packing client launchers, bytecode agent, and configurations...")
        for src_rel, arc_name in core_files:
            full_src = os.path.join(base_dir, src_rel)
            if os.path.exists(full_src):
                zf.write(full_src, arc_name)
                file_count += 1
                total_uncompressed += os.path.getsize(full_src)

        # 2. Native DLLs
        natives_src = os.path.join(base_dir, "CosmicClient-x64", "1.8", "bin-1.8")
        print("-> Packing 32-bit & 64-bit native libraries...")
        if os.path.exists(natives_src):
            for root, _, files in os.walk(natives_src):
                for f in files:
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, natives_src)
                    if not should_skip(f, rel):
                        arc = "1.8/bin-1.8/" + rel.replace("\\", "/")
                        zf.write(full, arc)
                        file_count += 1
                        total_uncompressed += os.path.getsize(full)

        # 3. Game Assets (textures, sound effects, music, models) - excluding stale skins cache
        assets_src = os.path.join(base_dir, "CosmicClient-x64", "assets_18")
        print("-> Packing 1.8.9 game assets (textures, audio, music)...")
        if os.path.exists(assets_src):
            for root, _, files in os.walk(assets_src):
                for f in files:
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, assets_src)
                    if not should_skip(f, rel):
                        arc = "assets_18/" + rel.replace("\\", "/")
                        zf.write(full, arc)
                        file_count += 1
                        total_uncompressed += os.path.getsize(full)

        # 4. Java Runtimes (64-bit and 32-bit Zulu OpenJDK 19)
        j64_src = os.path.join(base_dir, "CosmicClient-x64", "bootstrap", "java")
        print("-> Packing bundled Java 19 64-bit runtime (optimized)...")
        if os.path.exists(j64_src):
            for root, _, files in os.walk(j64_src):
                for f in files:
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, j64_src)
                    if not should_skip(f, rel):
                        arc = "bootstrap/java/" + rel.replace("\\", "/")
                        zf.write(full, arc)
                        file_count += 1
                        total_uncompressed += os.path.getsize(full)

        j32_src = os.path.join(base_dir, "CosmicClient-x64", "bootstrap", "java-x32")
        print("-> Packing bundled Java 19 32-bit runtime...")
        if os.path.exists(j32_src):
            for root, _, files in os.walk(j32_src):
                for f in files:
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, j32_src)
                    if not should_skip(f, rel):
                        arc = "bootstrap/java-x32/" + rel.replace("\\", "/")
                        zf.write(full, arc)
                        file_count += 1
                        total_uncompressed += os.path.getsize(full)

        # 5. Empty placeholder folders
        for folder in ["resourcepacks", "shaderpacks", "screenshots", "schematics"]:
            zfi = zipfile.ZipInfo(folder + "/")
            zf.writestr(zfi, "")

    compressed_size = os.path.getsize(output_zip)
    duration = time.time() - start_time
    print("-" * 60)
    print(f"Packaging complete in {duration:.1f}s!")
    print(f"Total payload files:     {file_count}")
    print(f"Uncompressed vault size: {total_uncompressed / 1024 / 1024:.1f} MB")
    print(f"Compressed archive size: {compressed_size / 1024 / 1024:.1f} MB")
    print(f"Output:                  {output_zip}")
    print("=" * 60)

if __name__ == "__main__":
    build_payload()
