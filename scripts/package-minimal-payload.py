import os
import sys
import zipfile
import json
import time

base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
output_zip = os.path.join(base_dir, "cosmic-payload.zip")
asset_index_path = os.path.join(base_dir, "CosmicClient-x64", "1.8.json")

# Dead Twitch streaming native DLLs discontinued since 2015 (saves >21 MB raw)
dead_native_dlls = {
    "libmfxsw64.dll",
    "twitchsdk.dll",
    "avutil-ttv-51.dll",
    "libmp3lame-ttv.dll",
    "swresample-ttv-0.dll"
}

def load_asset_metadata():
    if not os.path.exists(asset_index_path):
        return {}
    with open(asset_index_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    # Map hash -> relative asset path
    return {v.get("hash"): k for k, v in data.get("objects", {}).items()}

def is_unnecessary_asset(rel_name):
    lower = rel_name.lower()
    # Skip ambient background music and jukebox records to maximize byte-size reduction
    if lower.startswith("minecraft/sounds/music/"):
        return True
    if lower.startswith("minecraft/sounds/records/"):
        return True
    # Skip stale prebaked mapwriter radar PNGs (dynamically mapped in-game anyway)
    if lower.startswith("minecraft/mapwriter/prebaked/"):
        return True
    return False

def safe_write(zf, src, arc):
    try:
        mtime = os.path.getmtime(src)
        if mtime < 315532800: # Before 1980
            mtime = time.time()
        dt = time.localtime(mtime)[:6]
        zinfo = zipfile.ZipInfo(arc, dt)
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zinfo.external_attr = 0o644 << 16
        with open(src, 'rb') as f:
            zf.writestr(zinfo, f.read())
    except Exception:
        zf.write(src, arc)

def build_minimal_payload():
    print("=" * 65)
    print("=== BUILDING ULTRA-MINIMIZED ZERO-BLOAT COSMIC CLIENT VAULT ===")
    print("=" * 65)
    start_time = time.time()

    if os.path.exists(output_zip):
        try:
            os.remove(output_zip)
        except Exception as e:
            print(f"Warning removing old payload: {e}")

    hash_to_name = load_asset_metadata()
    print(f"Loaded asset index: {len(hash_to_name)} objects indexed.")

    file_count = 0
    total_uncompressed = 0

    with zipfile.ZipFile(output_zip, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        # 1. Core Executable and Client JARs
        core_files = [
            ("cosmic-agent.jar", "cosmic-agent.jar"),
            ("CosmicClient-x64/Launcher.jar", "Launcher.jar"),
            ("CosmicClient-x64/1.8/CosmicClient-1.8.9.jar", "1.8/CosmicClient-1.8.9.jar"),
            ("CosmicClient-x64/1.8.json", "1.8.json"),
            ("CosmicClient-x64/1.8.json", "1.8/1.8.json"),
            ("accounts.json", "accounts.json"),
            ("cosmic.ico", "cosmic.ico"),
            ("cosmic-icon.png", "cosmic-icon.png"),
            ("CosmicClient-x64/launcher-config.json", "launcher-config.json"),
            ("CosmicClient-x64/optionscosmic.txt", "optionscosmic.txt"),
            ("CosmicClient-x64/optionsof.txt", "optionsof.txt"),
            ("CosmicClient-x64/background.jpg", "background.jpg"),
            ("CosmicClient-x64/background.jpg", "cosmic/background.jpg"),
        ]

        print("-> Packing client JARs, bytecode agent, and core configurations...")
        for src_rel, arc_name in core_files:
            full_src = os.path.join(base_dir, src_rel)
            if os.path.exists(full_src):
                safe_write(zf, full_src, arc_name)
                file_count += 1
                total_uncompressed += os.path.getsize(full_src)

        # 2. Clean Windows Natives (excluding dead 2015 Twitch DLLs)
        natives_src = os.path.join(base_dir, "CosmicClient-x64", "1.8", "bin-1.8")
        print("-> Packing clean Windows native libraries (LWJGL, OpenAL, JInput)...")
        if os.path.exists(natives_src):
            for f in os.listdir(natives_src):
                if f.lower() in dead_native_dlls:
                    print(f"   [Skipped Dead Bloat] {f}")
                    continue
                full = os.path.join(natives_src, f)
                if os.path.isfile(full):
                    arc = f"1.8/bin-1.8/{f}"
                    safe_write(zf, full, arc)
                    file_count += 1
                    total_uncompressed += os.path.getsize(full)

        # 3. Essential Assets (all gameplay sound effects, icons, text, configs)
        # Omit skins cache, omit ambient background music and records
        assets_src = os.path.join(base_dir, "CosmicClient-x64", "assets_18")
        print("-> Packing essential game assets (sound effects, icons, UI, shaders)...")
        if os.path.exists(assets_src):
            # Write indexes
            indexes_dir = os.path.join(assets_src, "indexes")
            if os.path.exists(indexes_dir):
                for f in os.listdir(indexes_dir):
                    full = os.path.join(indexes_dir, f)
                    if os.path.isfile(full):
                        safe_write(zf, full, f"assets_18/indexes/{f}")
                        safe_write(zf, full, f"1.8/assets/indexes/{f}")
                        safe_write(zf, full, f"assets/indexes/{f}")
                        file_count += 3
                        total_uncompressed += os.path.getsize(full) * 3

            # Write objects
            objects_dir = os.path.join(assets_src, "objects")
            skipped_music = 0
            packed_objects = 0
            if os.path.exists(objects_dir):
                for root, _, files in os.walk(objects_dir):
                    for f in files:
                        full = os.path.join(root, f)
                        rel_name = hash_to_name.get(f)
                        if not rel_name:
                            # Skip assets not in the 1.8.json index (e.g. imported from player's PC)
                            continue
                        if is_unnecessary_asset(rel_name):
                            skipped_music += 1
                            continue

                        rel_to_objects = os.path.relpath(full, objects_dir)
                        arc = "assets_18/objects/" + rel_to_objects.replace("\\", "/")
                        safe_write(zf, full, arc)
                        file_count += 1
                        packed_objects += 1
                        total_uncompressed += os.path.getsize(full)

            print(f"   Packed {packed_objects} gameplay objects, filtered out {skipped_music} background music/record tracks.")

        # 4. Bundled Java 19 Runtime (Zulu JRE) for 100% self-contained offline execution
        java_src = os.path.join(base_dir, "CosmicClient-x64", "bootstrap", "java")
        print("-> Packing bundled Java 19 JRE for 100% self-contained offline execution...")
        if os.path.exists(java_src):
            packed_java = 0
            for root, _, files in os.walk(java_src):
                if "._" in root:
                    continue
                for f in files:
                    if f.startswith("._") or f == ".DS_Store":
                        continue
                    full = os.path.join(root, f)
                    rel = os.path.relpath(full, os.path.join(base_dir, "CosmicClient-x64"))
                    arc = rel.replace("\\", "/")
                    safe_write(zf, full, arc)
                    file_count += 1
                    packed_java += 1
                    total_uncompressed += os.path.getsize(full)
            print(f"   Packed {packed_java} Java runtime files.")

        # 5. Create empty standard user directories (screenshots, schematics)
        # Note: resourcepacks and shaderpacks are bridged dynamically from the player's PC
        for folder in ["screenshots", "schematics"]:
            zfi = zipfile.ZipInfo(folder + "/")
            zf.writestr(zfi, "")

    compressed_size = os.path.getsize(output_zip)
    duration = time.time() - start_time
    print("-" * 65)
    print(f"Packaging complete in {duration:.1f}s!")
    print(f"Total payload files:     {file_count}")
    print(f"Uncompressed vault size: {total_uncompressed / 1024 / 1024:.1f} MB")
    print(f"Compressed archive size: {compressed_size / 1024 / 1024:.1f} MB")
    print(f"Output:                  {output_zip}")
    print("=" * 65)

if __name__ == "__main__":
    build_minimal_payload()
