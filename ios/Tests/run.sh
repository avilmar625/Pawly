#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
artifacts=$(mktemp -d)
trap 'rm -rf "$artifacts"' EXIT
run_test() {
    local name=$1
    shift
    swiftc "$@" "Tests/${name}Tests.swift" -o "$artifacts/$name"
    "$artifacts/$name"
}
run_test UploadContract Pawly/PawlyUploadContract.swift
run_test UploadRetry Pawly/PawlyUploadContract.swift Pawly/PawlyUploadCache.swift
run_test AgendaCache Pawly/PawlyAgendaCache.swift
run_test BackupPolicy Pawly/PawlyBackupPolicy.swift
python3 Tests/check-localizations.py
