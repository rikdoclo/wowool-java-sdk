#!/usr/bin/env bash
# ==============================================================================
# Wowool Native Engine & Models Installer (Zero-Config / OCI Pre-Bake)
# Pure shell implementation (curl + unzip). Zero Python, pip, or venvs required.
# Supports granular model-only updates and explicit version pinning (e.g. dutch@3.2.8)
# ==============================================================================

set -euo pipefail

TARGET_DIR="${WOWOOL_INSTALL_DIR:-$HOME/.wowool}"
PLATFORM="auto"
LANGUAGES="dutch"
MODELS_ONLY=false
PYPI_API="https://pypi.org/pypi"

# Parse command line options
for arg in "$@"; do
    case "$arg" in
        --target=*)
            TARGET_DIR="${arg#*=}"
            ;;
        --platform=*)
            PLATFORM="${arg#*=}"
            ;;
        --languages=*)
            LANGUAGES="${arg#*=}"
            ;;
        --models-only|--update-models)
            MODELS_ONLY=true
            ;;
        --help|-h)
            echo "Usage: $0 [--target=/path/to/install] [--platform=auto|linux-x86_64|linux-aarch64|darwin-aarch64] [--languages=dutch@3.2.8,english] [--models-only]"
            exit 0
            ;;
    esac
done

# Detect platform if auto
if [ "$PLATFORM" = "auto" ] && [ "$MODELS_ONLY" = false ]; then
    OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
    ARCH="$(uname -m | tr '[:upper:]' '[:lower:]')"
    
    if [ "$OS" = "darwin" ]; then
        PLATFORM="darwin-aarch64"
    elif [ "$OS" = "linux" ]; then
        if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
            PLATFORM="linux-aarch64"
        else
            PLATFORM="linux-x86_64"
        fi
    else
        echo "Error: Unsupported operating system: $OS" >&2
        exit 1
    fi
fi

echo "================================================================================"
if [ "$MODELS_ONLY" = true ]; then
    echo "▶ Updating Wowool Linguistic Models Only"
else
    echo "▶ Installing Wowool Engine & Models"
    echo "  Platform         : $PLATFORM"
fi
echo "  Target Directory : $TARGET_DIR"
echo "  Languages        : $LANGUAGES"
echo "================================================================================"

mkdir -p "$TARGET_DIR/lib" "$TARGET_DIR/bin" "$TARGET_DIR/models/lxware/wowool"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

download_and_extract() {
    local raw_pkg="$1"
    local filter="$2"
    local dest_sub="$3"

    local pkg="$raw_pkg"
    local ver=""
    if [[ "$raw_pkg" == *"@"* ]]; then
        pkg="${raw_pkg%%@*}"
        ver="${raw_pkg#*@}"
    elif [[ "$raw_pkg" == *"=="* ]]; then
        pkg="${raw_pkg%%==*}"
        ver="${raw_pkg#*==}"
    fi

    local api_endpoint="$PYPI_API/$pkg/json"
    if [ -n "$ver" ]; then
        api_endpoint="$PYPI_API/$pkg/$ver/json"
    fi

    echo "  → Resolving package: $pkg (version: ${ver:-latest})..."
    local json
    json="$(curl -sSL -H "User-Agent: wowool-installer" "$api_endpoint")"

    local wheel_url
    wheel_url="$(echo "$json" | grep -o 'https://[^"]*\.whl' | grep "$filter" | head -n 1 || true)"

    if [ -z "$wheel_url" ]; then
        echo "Error: Could not find matching wheel for $pkg (version: ${ver:-latest}, filter: $filter)" >&2
        exit 1
    fi

    echo "  → Downloading: $(basename "$wheel_url")..."
    curl -sSL -H "User-Agent: wowool-installer" "$wheel_url" -o "$TMP_DIR/pkg.zip"

    echo "  → Extracting into $dest_sub..."
    unzip -q -o "$TMP_DIR/pkg.zip" -d "$TMP_DIR/extracted"
    rm -f "$TMP_DIR/pkg.zip"
}

# 1. C++ Engine & Dynamic Libraries (Skipped when --models-only)
if [ "$MODELS_ONLY" = false ]; then
    case "$PLATFORM" in
        darwin*|mac*)
            download_and_extract "wowool-sdk-cpp" "macosx.*arm64" "lib/bin"
            ;;
        linux-aarch64*)
            download_and_extract "wowool-sdk-cpp" "manylinux.*aarch64" "lib/bin"
            ;;
        *)
            download_and_extract "wowool-sdk-cpp" "manylinux.*x86_64" "lib/bin"
            ;;
    esac

    # Move libraries and binaries
    find "$TMP_DIR/extracted" -type f \( -name "*.dylib" -o -name "*.so*" \) -exec cp -f {} "$TARGET_DIR/lib/" \;
    for b in afst++ woc++ wow++; do
        find "$TMP_DIR/extracted" -type f -name "$b" -exec cp -f {} "$TARGET_DIR/bin/" \;
        if [ -f "$TARGET_DIR/bin/$b" ]; then
            chmod +x "$TARGET_DIR/bin/$b"
        fi
    done
    rm -rf "$TMP_DIR/extracted"
fi

# 2. Generic Language Support
download_and_extract "wowool-generic" "any" "models"
if [ -d "$TMP_DIR/extracted/wowool/lxware" ]; then
    cp -Rf "$TMP_DIR/extracted/wowool/lxware/"* "$TARGET_DIR/models/lxware/"
fi
rm -rf "$TMP_DIR/extracted"

# 3. Language Identification (LID)
download_and_extract "wowool-lid" "any" "models"
if [ -d "$TMP_DIR/extracted/wowool/lxware" ]; then
    cp -Rf "$TMP_DIR/extracted/wowool/lxware/"* "$TARGET_DIR/models/lxware/"
fi
rm -rf "$TMP_DIR/extracted"

# 4. Language Packages (supports language@version e.g. dutch@3.2.8)
IFS=',' read -ra LANG_ARRAY <<< "$LANGUAGES"
for lang_spec in "${LANG_ARRAY[@]}"; do
    clean_spec="$(echo "$lang_spec" | tr -d ' ')"
    if [[ "$clean_spec" != wowool-* ]]; then
        pkg_name="wowool-$clean_spec"
    else
        pkg_name="$clean_spec"
    fi
    download_and_extract "$pkg_name" "any" "models"
    if [ -d "$TMP_DIR/extracted/wowool/lxware" ]; then
        cp -Rf "$TMP_DIR/extracted/wowool/lxware/"* "$TARGET_DIR/models/lxware/"
    fi
    rm -rf "$TMP_DIR/extracted"
done

# 5. Create Convenience Symlinks
(cd "$TARGET_DIR" && ln -sfn "models/lxware" "lxware")
if [ "$MODELS_ONLY" = false ]; then
    (cd "$TARGET_DIR/lib" && for f in libwowool.*.dylib; do [ -f "$f" ] && ln -sfn "$f" libwowool.dylib; break; done || true)
    (cd "$TARGET_DIR/lib" && for f in libwowool.so.*; do [ -f "$f" ] && ln -sfn "$f" libwowool.so; break; done || true)
fi

echo "================================================================================"
echo "✔ Wowool assets successfully provisioned to: $TARGET_DIR"
echo "================================================================================"
