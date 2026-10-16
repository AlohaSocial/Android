#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
#
# Checks the release APKs' version codes (build-logic VersionSlots,
# docs/12-store.md): each one is its module's form-factor slot plus
# aloha.versionBase from gradle.properties, no two APKs of a flavour share a
# code, and the TV app outranks the phone app, which a TV qualifying for both
# must never be given.
set -euo pipefail
aapt2=$(find "${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools" -name 'aapt2*' -type f | sort | tail -1)
slot_size=100000000
base=$(sed -n 's/^aloha.versionBase=//p' gradle.properties | tr -d '\r')
declare -A expected=([app]=1 [tv]=2 [wear]=3)
failed=0

for flavor in generic gplay; do
	declare -A codes=()
	for module in app tv wear; do
		apk=$(ls "$module"/build/outputs/apk/"$flavor"/release/*.apk 2>/dev/null | head -1)
		if [ -z "$apk" ]; then
			echo "::error::no $flavor release APK for :$module"
			failed=1
			continue
		fi
		code=$("$aapt2" dump badging "$apk" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
		slot=$((code / slot_size))
		echo "$flavor :$module: $code (slot $slot)"
		want=$((expected[$module] * slot_size + base))
		if [ "$code" -ne "$want" ]; then
			echo "::error:::$module's $flavor APK has version code $code, not $want (slot ${expected[$module]}, aloha.versionBase $base)"
			failed=1
		fi
		if [ "$slot" -ne "${expected[$module]}" ]; then
			echo "::error:::$module's $flavor APK is in slot $slot, not ${expected[$module]}"
			failed=1
		fi
		for other in "${!codes[@]}"; do
			if [ "${codes[$other]}" = "$code" ]; then
				echo "::error:::$module and :$other share version code $code in $flavor"
				failed=1
			fi
		done
		codes[$module]=$code
	done
	if [ -n "${codes[tv]:-}" ] && [ -n "${codes[app]:-}" ] && [ "${codes[tv]}" -le "${codes[app]}" ]; then
		echo "::error::the $flavor TV APK (${codes[tv]}) must outrank the phone APK (${codes[app]})"
		failed=1
	fi
	unset codes
done
exit $failed
