#!/usr/bin/env bash
set -euo pipefail

mode="${1:---full}"
case "$mode" in
  --full|--player|--unreleased) ;;
  *) echo "Usage: $0 [--full|--player|--unreleased]" >&2; exit 2 ;;
esac

version="$(sed -n 's/^mod_version[[:space:]]*=[[:space:]]*//p' gradle.properties | head -n 1)"
test -n "$version"
if [[ "$mode" == "--unreleased" ]]; then
  version="Unreleased"
fi

awk -v version="$version" -v mode="$mode" '
  $0 == "# " version || $0 == "## " version { in_version = 1; next }
  in_version && /^# / { exit }
  !in_version { next }
  mode == "--player" && ($0 == "## Technical Changes" || $0 == "### Technical changes") { exit }
  { print }
' CHANGELOG.md
