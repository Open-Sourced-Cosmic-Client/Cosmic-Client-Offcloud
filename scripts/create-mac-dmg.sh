#!/bin/bash
# ==============================================================================
# Cosmic Client Offcloud - macOS DMG Disk Image Builder
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MAC_SRC="$ROOT_DIR/Mac"
DMG_NAME="Cosmic-Client-Mac"
DMG_OUT="$ROOT_DIR/$DMG_NAME.dmg"
TEMP_DMG_DIR="$ROOT_DIR/dist/dmg-stage"

echo "========================================================"
echo " Cosmic Client Offcloud - macOS DMG Disk Image Builder"
echo "========================================================"
echo ""

if [ ! -d "$MAC_SRC" ]; then
    echo "[!] Mac source directory not found. Generating platform packages..."
    node "$ROOT_DIR/scripts/package-split-releases.js"
fi

echo "[1/4] Preparing DMG staging environment..."
rm -rf "$TEMP_DMG_DIR" "$DMG_OUT"
mkdir -p "$TEMP_DMG_DIR"

# Copy all Mac release files into DMG staging folder
cp -R "$MAC_SRC"/* "$TEMP_DMG_DIR"/

# Create direct drag-and-drop symlink to /Applications
ln -s /Applications "$TEMP_DMG_DIR/Applications" 2>/dev/null || true

echo "[2/4] Stripping quarantine attributes & setting permissions..."
if [ "$(uname -s)" = "Darwin" ]; then
    xattr -cr "$TEMP_DMG_DIR" 2>/dev/null || true
    xattr -dr com.apple.quarantine "$TEMP_DMG_DIR" 2>/dev/null || true
    chmod -R 755 "$TEMP_DMG_DIR"/*.command "$TEMP_DMG_DIR"/*.sh "$TEMP_DMG_DIR"/*.app 2>/dev/null || true
    find "$TEMP_DMG_DIR" -type f -name "*.dylib" -exec chmod 755 {} + 2>/dev/null || true
    find "$TEMP_DMG_DIR" -type f -name "java" -exec chmod 755 {} + 2>/dev/null || true

    # Ad-hoc sign app bundle to prevent 'app damaged' warning on modern macOS
    if [ -d "$TEMP_DMG_DIR/Cosmic Client.app" ]; then
        echo " -> Applying local code signature to Cosmic Client.app..."
        codesign --force --deep -s - "$TEMP_DMG_DIR/Cosmic Client.app" 2>/dev/null || true
    fi
fi

echo "[3/4] Creating compressed DMG disk image via hdiutil..."
if [ "$(uname -s)" = "Darwin" ]; then
    hdiutil create -volname "Cosmic Client" \
                   -srcfolder "$TEMP_DMG_DIR" \
                   -ov \
                   -format UDZO \
                   -imagekey zlib-level=9 \
                   "$DMG_OUT"

    xattr -cr "$DMG_OUT" 2>/dev/null || true

    if [ -n "$APPLE_SIGNING_IDENTITY" ]; then
        echo "[Sign] Signing DMG with identity: $APPLE_SIGNING_IDENTITY..."
        codesign --force --sign "$APPLE_SIGNING_IDENTITY" "$DMG_OUT"
    else
        codesign --force --deep -s - "$DMG_OUT" 2>/dev/null || true
    fi

    echo " -> Created: $DMG_OUT"
else
    echo "[!] Note: Native hdiutil is macOS-only. Run this script directly on macOS to generate the .dmg file."
fi

echo "[4/4] Cleaning temporary files..."
rm -rf "$TEMP_DMG_DIR"

echo ""
echo "========================================================"
echo " [SUCCESS] macOS DMG creation complete!"
echo " DMG Location: $DMG_OUT"
echo "========================================================"
