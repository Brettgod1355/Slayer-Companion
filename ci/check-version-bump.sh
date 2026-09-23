#!/usr/bin/env bash
# Fails a pull request that changes what the Plugin Hub builds (src/main, the
# plugin descriptor, Gradle files, the icon) unless every version field agrees,
# the version is strictly newer than the base branch's (semver-ordered), and the
# PR title ends with "(x.y.z)". Pull requests that only touch other files (docs,
# CI, tools, editor settings) need no bump.
#
# Usage: ci/check-version-bump.sh <base-sha> <head-sha> "<pr title>"
#
# Pass the base commit the event was raised against, not the base branch name:
# a re-run that starts after the PR merged would otherwise compare the version
# with itself.
set -euo pipefail

base="${1:?base sha required}"
head="${2:?head sha required}"
pr_title="${3:?pr title required}"

# Files the Hub packager builds the plugin jar from.
shipping_re='^src/main/|^runelite-plugin\.properties$|\.gradle$|^icon\.png$'

changed=$(git diff --name-only "$base...$head")
shipping=$(grep -E "$shipping_re" <<< "$changed" || true)

read_props_version() { sed -n 's/^version=//p' | tr -d '[:space:]'; }
read_gradle_version() { sed -n "s/^version = ['\"]\(.*\)['\"]$/\1/p" | tr -d '[:space:]'; }
read_java_version() { sed -n 's/.*String VERSION = "\([^"]*\)".*/\1/p' | tr -d '[:space:]'; }

props_v=$(git show "$head:runelite-plugin.properties" | read_props_version)
gradle_v=$(git show "$head:build.gradle" | read_gradle_version)
java_v=$(git show "$head:src/main/java/com/slayercompanion/SlayerCompanionPlugin.java" | read_java_version)
base_v=$(git show "$base:runelite-plugin.properties" 2>/dev/null | read_props_version || true)

echo "runelite-plugin.properties: $props_v"
echo "build.gradle:               $gradle_v"
echo "SlayerCompanionPlugin.java: $java_v"
echo "base (${base:0:12}):        ${base_v:-<none>}"

if [[ -z "$props_v" || "$props_v" != "$gradle_v" || "$props_v" != "$java_v" ]]; then
	echo "::error::Version fields disagree or are missing. Bump all three together."
	exit 1
fi

if ! [[ "$props_v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "::error::Version '$props_v' is not x.y.z"
	exit 1
fi

if [[ -z "$shipping" && "$props_v" == "$base_v" ]]; then
	echo "No plugin files changed and the version is unchanged; no bump needed."
	exit 0
fi

if [[ -n "$shipping" ]]; then
	echo "Plugin files changed:"
	sed 's/^/  /' <<< "$shipping"
fi

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
