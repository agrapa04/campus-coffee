#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<USAGE
Usage (solo):
  ./prepare-submission.sh <Lastname> <Firstname>

Usage (group of two):
  ./prepare-submission.sh <Lastname1> <Firstname1> <Lastname2> <Firstname2>

Examples:
  ./prepare-submission.sh Mueller Anna
    -> ../iase26_assignment05_Mueller_Anna.zip

  ./prepare-submission.sh Mueller-Schmidt Anna Weber Berta
    -> ../iase26_assignment05_Mueller-Schmidt_Anna__Weber_Berta.zip

Set SKIP_BUILD_CHECK=1 to skip the post-zip 'mise install && gradle build' validation.
USAGE
}

if [ "$#" -ne 2 ] && [ "$#" -ne 4 ]; then
    usage
    exit 1
fi

for part in "$@"; do
    case "$part" in
        "" | *[!A-Za-z0-9-]*)
            echo "error: name parts must use only ASCII letters, digits, and hyphens" >&2
            exit 1
            ;;
    esac
done

if [ "$#" -eq 2 ]; then
    NAME_PART="$1_$2"
else
    NAME_PART="$1_$2__$3_$4"
fi

OUTPUT="../iase26_assignment05_${NAME_PART}.zip"

if [ ! -d ".git" ]; then
    echo "error: not a git repository (run from the root of your assignment clone)" >&2
    exit 1
fi

for required in \
    "settings.gradle.kts" \
    "mise.toml" \
    "gradle/libs.versions.toml" \
    "REFLECTION.md" \
    "application/src/main/kotlin/de/seuhd/campuscoffee"
do
    if [ ! -e "$required" ]; then
        echo "error: missing $required (run from the root of your assignment clone)" >&2
        exit 1
    fi
done

# The 'zip' and 'unzip' tools below are checked up front so the script fails early with guidance
# instead of mid-zip with a raw shell error.
if ! command -v zip >/dev/null 2>&1; then
    echo "error: 'zip' is not installed." >&2
    echo "  - Windows: run this script inside WSL ('sudo apt install zip'); Git Bash does not ship zip." >&2
    echo "  - Debian/Ubuntu/WSL: sudo apt install zip unzip" >&2
    echo "  - macOS: zip ships with the system; check your PATH." >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "warning: your working tree has uncommitted or untracked changes (listed below)." >&2
    echo "warning: they WILL be included in the zip, but graders also read your git history --" >&2
    echo "warning: commit your work before submitting so the zip and the history match." >&2
    git status --short >&2
fi

SYMLINK="$(find . -type l -print -quit)"
if [ -n "$SYMLINK" ]; then
    echo "error: refusing to package repository with symlinks" >&2
    echo "first symlink found: $SYMLINK" >&2
    exit 1
fi

rm -f "$OUTPUT"

# Keep .git/ and the build inputs (mise.toml, gradle/libs.versions.toml, build-logic/, gradle.properties);
# exclude generated build output, IDE files, and the mise-provisioned Gradle wrapper (this project has no
# wrapper, so gradlew*/gradle/wrapper are IDE-generated and ignored).
COPYFILE_DISABLE=1 zip -qr "$OUTPUT" . \
    -x "*/.gradle/*"  -x ".gradle/*" \
    -x "*/.gradle"    -x ".gradle" \
    -x "*/build/*"    -x "build/*" \
    -x "*/build"      -x "build" \
    -x "gradle/wrapper/*" -x "gradle/wrapper" \
    -x "*/gradlew"          -x "gradlew" \
    -x "*/gradlew.bat"      -x "gradlew.bat" \
    -x "*/.idea/*"    -x ".idea/*" \
    -x "*/.idea"      -x ".idea" \
    -x "*.iml"        -x "*/*.iml" \
    -x "*/.kotlin/*"  -x ".kotlin/*" \
    -x "*/.kotlin"    -x ".kotlin" \
    -x "*/.vscode/*"  -x ".vscode/*" \
    -x "*/.vscode"    -x ".vscode" \
    -x "*/.claude/*"  -x ".claude/*" \
    -x "*/.claude"    -x ".claude" \
    -x "*/out/*"      -x "out/*" \
    -x "*/out"        -x "out" \
    -x "*/target/*"   -x "target/*" \
    -x "*/target"     -x "target" \
    -x "ai-slop-report.md" \
    -x "*.zip" \
    -x ".DS_Store" -x "*/.DS_Store"

echo "Wrote $OUTPUT"

# Validate the zip the way graders will: unzip it, confirm .git/ made it in, then run the build on the
# unzipped tree (mise trust && mise install && gradle build). A missing .git/ or a failing build is graded 0.
if ! command -v unzip >/dev/null 2>&1; then
    echo "warning: 'unzip' is not installed; skipping the post-zip validation." >&2
    echo "warning: graders will unzip and run 'gradle build' -- make sure both succeed." >&2
    exit 0
fi

echo "Validating the zip the way graders will (unzip + gradle build)..."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
unzip -q "$OUTPUT" -d "$TMP_DIR"

if [ ! -d "$TMP_DIR/.git" ]; then
    echo "error: the zip does not contain the .git/ directory -- this submission would be graded 0." >&2
    exit 1
fi

if [ "${SKIP_BUILD_CHECK:-0}" = "1" ]; then
    echo "Zip contains .git/; skipping 'gradle build' (SKIP_BUILD_CHECK=1)."
    exit 0
fi

# The build is provisioned via mise and the tests use Testcontainers, so it needs mise and a running
# Docker daemon. If either is missing locally, skip the build check with a warning rather than report a
# false failure -- the student's environment, not their submission, is incomplete.
if ! command -v mise >/dev/null 2>&1; then
    echo "warning: 'mise' is not installed; skipping the 'gradle build' validation." >&2
    echo "warning: graders run 'mise trust && mise install && gradle build' -- make sure it succeeds." >&2
    exit 0
fi

if ! docker info >/dev/null 2>&1; then
    echo "warning: the Docker daemon is not reachable; skipping the 'gradle build' validation." >&2
    echo "warning: the tests use Testcontainers, so graders run the build with Docker running." >&2
    echo "warning: start Docker and re-run to validate, or submit knowing the build is unverified." >&2
    exit 0
fi

if ! ( cd "$TMP_DIR" && mise trust && mise install && mise exec -- gradle build ); then
    echo "error: 'gradle build' FAILED on the unzipped submission -- this would be graded 0." >&2
    echo "error: fix the build, commit, and re-run this script." >&2
    exit 1
fi

echo "Validation OK: the zip contains .git/ and 'gradle build' passes on the unzipped tree."
