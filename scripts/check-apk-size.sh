#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
#
# Compares the release APK sizes with config/size-baseline.json and fails on
# growth above 2 %. A pull request that grows the app on purpose
# updates the baseline file in the same change.
set -euo pipefail
baseline=config/size-baseline.json
failed=0
for apk in app/build/outputs/apk/*/release/*.apk; do
	name=$(basename "$apk")
	size=$(stat -c %s "$apk")
	allowed=$(python3 -c "import json,sys; b=json.load(open('$baseline')).get('$name'); print(int(b*1.02) if b else 0)")
	echo "$name: $size bytes (limit $allowed)"
	if [ "$allowed" -gt 0 ] && [ "$size" -gt "$allowed" ]; then
		echo "::error::$name grew beyond 2 % of $baseline"
		failed=1
	fi
done
exit $failed
