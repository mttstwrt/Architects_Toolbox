#!/usr/bin/env bash
# Apply inverse-rename sed for backporting master commits to older MC branches.
#
# Workflow:
#   1. git checkout <older-branch>
#   2. git cherry-pick -x <commit>
#   3. scripts/backport-renames.sh <target-version>
#   4. ./gradlew build  (fix any structural conflicts the script can't handle)
#
# Targets:
#   1.21.10  — inverse of the 1.21.10 -> 1.21.11 master rename set
#   1.21.1   — 1.21.10 renames plus 1.21.1-specific renames
#
# Args (optional): file paths to operate on. Defaults to all .java under common/, fabric/, neoforge/.
#
# Note: structural API differences (ClickEvent/HoverEvent shape, registry iteration)
# are NOT handled here — they live behind util/ToolboxText and util/RegistryHelpers,
# whose internals must be hand-adapted per branch.

set -euo pipefail

target="${1:?usage: $0 <1.21.10|1.21.1> [files...]}"
shift

if [ "$#" -gt 0 ]; then
    files=("$@")
else
    mapfile -t files < <(find common fabric neoforge -name '*.java' -not -path '*/build/*' 2>/dev/null)
fi

if [ "${#files[@]}" -eq 0 ]; then
    echo "no .java files to process" >&2
    exit 1
fi

apply_1_21_10_renames() {
    # 1.21.11 master -> 1.21.10
    sed -i -E \
        -e 's|net\.minecraft\.resources\.Identifier|net.minecraft.resources.ResourceLocation|g' \
        -e 's|net\.minecraft\.client\.renderer\.rendertype\.RenderTypes|net.minecraft.client.renderer.RenderType|g' \
        -e 's|\bIdentifier\b|ResourceLocation|g' \
        -e 's|\bRenderTypes\.LINES\b|RenderType.lines()|g' \
        -e 's|camera\.position\(\)|camera.getPosition()|g' \
        -e 's|\.identifier\(\)|.location()|g' \
        "${files[@]}"
}

apply_1_21_1_renames() {
    # 1.21.10 -> 1.21.1 (run after the 1.21.10 step)
    sed -i -E \
        -e 's|\.getMinY\(\)|.getMinBuildHeight()|g' \
        -e 's|\.getMaxY\(\)|.getMaxBuildHeight()|g' \
        "${files[@]}"
}

case "$target" in
    1.21.10)
        apply_1_21_10_renames
        ;;
    1.21.1)
        apply_1_21_10_renames
        apply_1_21_1_renames
        ;;
    *)
        echo "unknown target: $target (expected 1.21.10 or 1.21.1)" >&2
        exit 1
        ;;
esac

echo "Renames applied for $target across ${#files[@]} file(s). Run ./gradlew build to verify."
