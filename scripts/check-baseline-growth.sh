#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
#
# Fails when a detekt, ktlint or Lint baseline has more entries than on the base
# branch. Baselines are how findings are tracked without a report server; they
# may shrink, never grow.
#   scripts/check-baseline-growth.sh origin/main
set -euo pipefail
base=${1:?base ref}

count() { grep -cE '<ID>|<issue |<error ' 2>/dev/null || true; }

failed=0
while IFS= read -r file; do
	now=$(count < "$file")
	before=$(git show "$base:$file" 2>/dev/null | count)
	before=${before:-0}
	if [ "${now:-0}" -gt "$before" ]; then
		echo "::error file=$file::baseline grew from $before to $now entries"
		failed=1
	fi
done < <(git ls-files 'config/detekt/baselines/*.xml' 'config/ktlint/baselines/*.xml' '**/lint-baseline.xml' 'lint-baseline.xml')
exit $failed
