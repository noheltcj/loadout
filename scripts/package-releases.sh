#!/usr/bin/env bash

# Script to package Loadout binaries for Homebrew release
# Usage: ./scripts/package-releases.sh [version]

set -euo pipefail

VERSION="${1:-0.1.0}"
BUILD_DIR="build/bin"
DIST_DIR="dist/v${VERSION}"

echo "Packaging Loadout v${VERSION} for Homebrew release..."

# Create distribution directory
mkdir -p "${DIST_DIR}"

# Function to package a binary
package_binary() {
    local platform=$1
    local arch=$2
    local binary_path="${BUILD_DIR}/${platform}/releaseExecutable/loadout.kexe"
    local tarball_name="loadout-${arch}.tar.gz"
    local tarball_path="${DIST_DIR}/${tarball_name}"

    if [[ ! -f "${binary_path}" ]]; then
        echo "Error: Binary not found at ${binary_path}"
        echo "Run './gradlew build' first to build all binaries"
        return 1
    fi

    echo "Packaging ${arch}..."

    # Create a temporary directory for the tarball
    local temp_dir=$(mktemp -d)
    cp "${binary_path}" "${temp_dir}/loadout"
    chmod +x "${temp_dir}/loadout"

    # Create tarball
    tar -czf "${tarball_path}" -C "${temp_dir}" loadout

    # Clean up
    rm -rf "${temp_dir}"

    echo "Created: ${tarball_path}"
}

# Package all architectures
package_binary "macosArm64" "macos-arm64"
package_binary "macosX64" "macos-x64"
package_binary "linuxArm64" "linux-arm64"
package_binary "linuxX64" "linux-x64"
package_binary "mingwX64" "windows-x64"

# Generate checksums
echo ""
echo "Generating SHA256 checksums..."
cd "${DIST_DIR}"
shasum -a 256 *.tar.gz | tee SHA256SUMS.txt

echo ""
echo "Release artifacts created in ${DIST_DIR}"
echo ""
echo "Next steps:"
echo "1. Create a GitHub release for v${VERSION}"
echo "2. Upload all *.tar.gz files from ${DIST_DIR}"
echo "3. Update homebrew-tap/Formula/loadout.rb with:"
echo "   - version: ${VERSION}"
echo "   - SHA256 checksums from SHA256SUMS.txt"
echo ""
echo "SHA256 checksums:"
cat SHA256SUMS.txt
