#!/usr/bin/env bash
# Fail if the project version drifts between the Gradle build and CHANGELOG.md. The `version` property in
# the root gradle.properties (Gradle sets it on project.version for every module) is the source of truth;
# the latest `## [x.y.z]` header in CHANGELOG.md must agree with it. Run locally or in CI; emits GitHub
# Actions error annotations on mismatch.
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
  echo "::error::$*" >&2
  exit 1
}

# Source of truth: the project version Gradle reads from the root gradle.properties. Capture the full token
# (up to whitespace), including any pre-release/build suffix, so a suffix mismatch is not silently dropped.
gradle=$(sed -n 's/^version *= *\([0-9][^[:space:]]*\).*/\1/p' gradle.properties)
[ -n "$gradle" ] || fail "could not read the version from gradle.properties"

# The latest released version header in CHANGELOG.md (## [x.y.z]) must match it. The header must start with a
# digit, so the [Unreleased] header is skipped; the first match is the newest release. Capture the full token
# between the brackets (including any suffix) so it compares verbatim with the Gradle version.
changelog=$(sed -n 's/^## \[\([0-9][^]]*\)\].*/\1/p' CHANGELOG.md | head -n1)
[ -n "$changelog" ] || fail "could not read a released version (## [x.y.z]) from CHANGELOG.md"

echo "Project version — gradle=$gradle changelog=$changelog"

[ "$gradle" = "$changelog" ] ||
  fail "latest CHANGELOG.md version ($changelog) differs from the Gradle build version ($gradle)"

echo "Project versions are consistent ($gradle)."
