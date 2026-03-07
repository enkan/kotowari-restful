#!/usr/bin/env bash
# Update project version in pom.xml, README.md, and example/pom.xml.
# Usage: ./scripts/update-version.sh <version>
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <version>" >&2
  exit 1
fi

VERSION="$1"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
README_FILE="$ROOT_DIR/README.md"
EXAMPLE_POM="$ROOT_DIR/example/pom.xml"

# 1. Update project version in pom.xml
echo "Running mvn versions:set -DnewVersion=$VERSION ..."
mvn -f "$ROOT_DIR/pom.xml" versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false
echo "Project version updated -> $VERSION"

# 2. Update <version> in README.md (only the one inside the <dependency> block)
sed -i '' "/<artifactId>kotowari-restful<\/artifactId>/{n;s|<version>[^<]*</version>|<version>$VERSION</version>|;}" "$README_FILE"
echo "README.md updated -> $VERSION"

# 3. Update <enkan.version> in example/pom.xml
if [[ -f "$EXAMPLE_POM" ]]; then
  sed -i '' "s|<enkan\.version>[^<]*</enkan\.version>|<enkan.version>$VERSION</enkan.version>|g" "$EXAMPLE_POM"
  echo "example/pom.xml updated -> $VERSION"
fi
