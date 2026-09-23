#!/usr/bin/env bash
# Proves the Hub rules guard still catches a known-bad file (a guard that cannot fail is no guard).
set -u
if ci/check-hub-rules.sh ci/fixtures/bad > /tmp/hub-rules-selftest.log 2>&1; then
	echo "::error::check-hub-rules.sh accepted the known-bad fixture"
	cat /tmp/hub-rules-selftest.log
	exit 1
fi
for label in "openConnection" "direct file I/O" "Thread.sleep" "reflection" "external processes"; do
	if ! grep -q "$label" /tmp/hub-rules-selftest.log; then
		echo "::error::guard did not report: $label"
		cat /tmp/hub-rules-selftest.log
		exit 1
	fi
done
echo "Hub rules self-test passed"
