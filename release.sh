#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

echo "📦 Staging artifacts..."
mvn --batch-mode --no-transfer-progress -Ppublication -DskipTests=true -Dskip.spotless=true

echo "🚀 Releasing..."
mvn --batch-mode --no-transfer-progress -Prelease jreleaser:full-release

echo "🎉 Done!"
