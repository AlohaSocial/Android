#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Aloha Social contributors
# SPDX-License-Identifier: MIT
#
# Signed-in UI flows on an emulator: the debug build signs in against the mock server, then the
# platform surfaces are driven through their manifest wiring, as other apps and the system reach them.
# The JVM tests cover what the app does with each intent; these check that it arrives.
#
#   ./gradlew :app:assembleGenericDebug && scripts/ui-flows.sh
#
# Needs one emulator (ANDROID_SERIAL picks it when more devices are attached), adb and maestro on the
# PATH (or ADB / MAESTRO), and no HTTP proxy set on the emulator: the mock is its localhost.
set -euo pipefail

port=${ALOHA_MOCK_PORT:-4567}
adb=(${ADB:-adb})
maestro=(${MAESTRO:-maestro})
if [ -n "${ANDROID_SERIAL:-}" ]; then
	adb+=(-s "$ANDROID_SERIAL")
	maestro+=(--device "$ANDROID_SERIAL")
fi
app=social.aloha.android
flows=.maestro/signed-in
# a path the JVM reads as the shell does: Git Bash's /d/... is D:\d\... to Java on Windows
stop="$(cygpath -m "$PWD" 2>/dev/null || pwd)/build/mock-server.stop"
mkdir -p build
rm -f "$stop"

step() { printf '\n== %s\n' "$*"; }
shell() { MSYS_NO_PATHCONV=1 "${adb[@]}" shell "$@"; }
flow() { local file=$1; shift; "${maestro[@]}" test "$@" "$flows/$file"; }

step "mock server on port $port"
ALOHA_MOCK_PORT=$port ALOHA_MOCK_STOP=$stop ./gradlew --console=plain -q :core:testing:testDebugUnitTest \
	--tests social.aloha.core.testing.ServeForDevice --rerun > build/mock-server.log 2>&1 &
trap 'touch "$stop"' EXIT
for _ in $(seq 1 120); do
	curl -sf -o /dev/null "http://localhost:$port/api/v2/instance" && break
	sleep 2
done
curl -sf -o /dev/null "http://localhost:$port/api/v2/instance" || { cat build/mock-server.log; exit 1; }
"${adb[@]}" reverse "tcp:$port" "tcp:$port"

step "sign in"
"${adb[@]}" install -r app/build/outputs/apk/generic/debug/*.apk
flow sign-in.yaml -e SERVER="http://localhost:$port"

step "Home's feeds: the switcher and Edit feeds"
flow feeds.yaml

step "share sheet: text to the app"
shell am start -a android.intent.action.SEND -t text/plain \
	--es android.intent.extra.TEXT "'Shared from another app'" -n "$app/.MainActivity"
flow shared-text.yaml -e TEXT="Shared from another app"

step "published shortcuts"
shortcuts=$(shell dumpsys shortcut | sed -n "/Package: $app /,/Package: /p")
for id in compose search notifications account:; do
	grep -q "id=$id" <<<"$shortcuts" || { echo "no shortcut $id"; exit 1; }
done
account=$(grep -o 'id=account:[^,]*' <<<"$shortcuts" | head -1 | cut -d= -f2)

step "Direct Share: the account's target"
shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'Shared directly'" \
	--es android.intent.extra.shortcut.ID "$account" -n "$app/.MainActivity"
flow shared-text.yaml -e TEXT="Shared directly"

step "launcher shortcuts: New post, Search"
shell am start -a social.aloha.action.COMPOSE -n "$app/.MainActivity"
flow shortcut.yaml -e SCREEN="Post"
shell am start -a social.aloha.action.SEARCH -n "$app/.MainActivity"
flow shortcut.yaml -e SCREEN="Search"

step "Open in Aloha: a profile address from the share sheet"
shell am start -a android.intent.action.SEND -t text/plain \
	--es android.intent.extra.TEXT "'Look: http://localhost:$port/@bob'" -n "$app/.OpenInAloha"
flow open-in-aloha.yaml

step "a post's link in a freeform window"
# bob's post, which the mock's corpus serves; alohasocial://open takes https only, and a post on the
# reader's own server opens by its id, so the address itself is never fetched
post="https://localhost:$port/index.php/apps/social/@bob/1790637117344364158"
freeform=$(shell settings get global enable_freeform_support | tr -d '\r')
shell settings put global enable_freeform_support 1
shell am force-stop "$app"
shell am start --windowingMode 5 -a android.intent.action.VIEW -d "'alohasocial://open?url=$post'" -n "$app/.MainActivity"
flow thread.yaml
shell dumpsys activity activities | grep "A=.*:$app" | grep -q "mode=freeform" || { echo "not in a freeform window"; exit 1; }
shell settings put global enable_freeform_support "${freeform/null/0}"
shell am force-stop "$app"

step "the watch page in tabletop posture, on a foldable"
if shell cmd device_state print-states | grep -q HALF_OPENED; then
	flow watch.yaml
	# the title's top edge, in the watch page's pane (the last one on screen)
	title_top() {
		shell uiautomator dump /sdcard/ui.xml > /dev/null
		shell cat /sdcard/ui.xml | grep -o 'text="Aloha test video"[^>]*bounds="\[[0-9]*,[0-9]*\]' | tail -1 |
			sed 's/.*bounds="\[[0-9]*,\([0-9]*\)\]/\1/'
	}
	rotation=$(shell settings get system user_rotation | tr -d '\r')
	auto=$(shell settings get system accelerometer_rotation | tr -d '\r')
	# tabletop: half open, the hinge across the screen
	shell settings put system accelerometer_rotation 0
	shell settings put system user_rotation 1
	shell cmd device_state state 1
	sleep 5
	# the screen as it is now, turned: `wm size` gives it unturned
	height=$(shell dumpsys window displays | grep -o 'cur=[0-9]*x[0-9]*' | head -1 | cut -dx -f2)
	top=$(title_top)
	shell cmd device_state state reset
	shell settings put system user_rotation "${rotation/null/0}"
	shell settings put system accelerometer_rotation "${auto/null/1}"
	echo "title at $top of $height"
	[ -n "$top" ] && [ "$top" -gt $((height / 2)) ] || { echo "the video and its details do not sit apart"; exit 1; }
	shell am force-stop "$app"
else
	echo "skipped: not a foldable"
fi

step "all flows passed"
