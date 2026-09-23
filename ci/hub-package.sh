#!/usr/bin/env bash
# Builds this plugin with RuneLite's own Plugin Hub packager, the tool the
# runelite/plugin-hub repository runs on every submission. It validates the
# descriptor (runelite-plugin.properties, LICENSE, plugin classes), builds with the
# Hub's Gradle template and checks the jar against the RuneLite API the Hub ships.
# Nothing is published: uploads go to a local stub that discards them.
#
# Usage: ci/hub-package.sh <commit-sha> [workdir]
# The commit must already be pushed to github.com/Brettgod1355/Slayer-Companion,
# because the packager clones the plugin from GitHub like the real Hub does.
# workdir is reused between runs (plugin-hub clone, packager bundle, api files).
set -euo pipefail

commit="${1:?commit sha required}"
work="${2:-${RUNNER_TEMP:-/tmp}/hub-package}"
here="$(cd "$(dirname "$0")" && pwd)"
packager_url="https://github.com/runelite/plugin-hub-tooling/releases/download/v4/bundle.tar.zst"

if ! [[ "$commit" =~ ^[0-9a-f]{40}$ ]]; then
	echo "::error::need a full 40-character commit sha, got '$commit'"
	exit 1
fi

mkdir -p "$work"
cd "$work"

if [[ ! -d plugin-hub ]]; then
	git clone --quiet --depth 1 https://github.com/runelite/plugin-hub.git plugin-hub
fi
if [[ ! -f package.jar ]]; then
	curl --location --fail --retry 4 --max-time 120 --output bundle.tar.zst "$packager_url"
	tar xf bundle.tar.zst
fi
if [[ ! -d api ]] || [[ -z "$(ls -A api)" ]]; then
	./prepare.sh
fi

# The packager builds whatever descriptor sits in plugin-hub/plugins/. Commit it to
# the throwaway clone only; this clone is never pushed.
printf 'repository=https://github.com/Brettgod1355/Slayer-Companion.git\ncommit=%s\n' "$commit" \
	> plugin-hub/plugins/slayer-companion
git -C plugin-hub add plugins/slayer-companion
git -C plugin-hub -c user.name=ci -c user.email=ci@localhost \
	commit --quiet --allow-empty -m "local descriptor for slayer-companion (never pushed)"

logs="$work/logs"
rm -rf "$logs" && mkdir -p "$logs"
python3 "$here/hub-stub-repo.py" "$logs" &
stub=$!
trap 'kill "$stub" 2>/dev/null || true' EXIT
sleep 1

rm -rf /tmp/jars
# REPO_CREDS is a dummy the stub ignores; the packager refuses to start without one.
status=0
REPO_ROOT="http://127.0.0.1:18777" REPO_CREDS="local:local" FORCE_BUILD="slayer-companion" \
	java -XX:+UseParallelGC -cp package.jar net.runelite.pluginhub.packager.Packager || status=$?

if [[ $status -ne 0 ]]; then
	for f in "$logs"/*; do
		[[ -f "$f" ]] || continue
		echo "----- Hub build log: $(basename "$f")"
		cat "$f"
	done
	echo "::error::The Plugin Hub packager rejected this commit (see the log above)."
fi
exit $status
