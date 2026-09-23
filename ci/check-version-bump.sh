#!/usr/bin/env bash
# Fails a pull request unless every version field agrees, the version is strictly
# newer than the base branch's (semver-ordered), and the PR title ends with "(x.y.z)".
#
# Usage: ci/check-version-bump.sh <base-ref> "<pr title>"
set -euo pipefail

base_ref="${1:?base ref required}"
pr_title="${2:?pr title required}"

read_props_version() { sed -n 's/^version=//p' "$1" | tr -d '[:space:]'; }
read_gradle_version() { sed -n "s/^version = ['\"]\(.*\)['\"]$/\1/p" "$1" | tr -d '[:space:]'; }
read_java_version() { sed -n 's/.*String VERSION = "\([^"]*\)".*/\1/p' "$1" | tr -d '[:space:]'; }

props_v=$(read_props_version runelite-plugin.properties)
gradle_v=$(read_gradle_version build.gradle)
java_v=$(read_java_version src/main/java/com/slayercompanion/SlayerCompanionPlugin.java)

echo "runelite-plugin.properties: $props_v"
echo "build.gradle:               $gradle_v"
echo "SlayerCompanionPlugin.java: $java_v"

if [[ -z "$props_v" || "$props_v" != "$gradle_v" || "$props_v" != "$java_v" ]]; then
	echo "::error::Version fields disagree or are missing. Bump all three together."
	exit 1
fi

if ! [[ "$props_v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "::error::Version '$props_v' is not x.y.z"
	exit 1
fi

base_v=$(git show "$base_ref:runelite-plugin.properties" 2>/dev/null | sed -n 's/^version=//p' | tr -d '[:space:]' || true)
echo "base ($base_ref):            ${base_v:-<none>}"

semver_gt() {
	# returns 0 when $1 > $2
	local IFS=.
	local -a a=($1) b=($2)
	for i in 0 1 2; do
		if (( ${a[$i]:-0} > ${b[$i]:-0} )); then return 0; fi
		if (( ${a[$i]:-0} < ${b[$i]:-0} )); then return 1; fi
	done
	return 1
}

if [[ -n "$base_v" ]] && ! semver_gt "$props_v" "$base_v"; then
	echo "::error::Version $props_v is not newer than base version $base_v"
	exit 1
fi

if [[ "$pr_title" != *"($props_v)" ]]; then
	echo "::error::PR title must end with ($props_v). Title was: $pr_title"
	exit 1
fi

echo "Version bump OK: $props_v"
