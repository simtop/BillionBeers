#!/bin/bash

# Configuration
README_FILE="README.md"
LIBS_CATALOG="gradle/libs.versions.toml"
GRADLE_WRAPPER="gradle/wrapper/gradle-wrapper.properties"

# Extraction using a single pass for each file
KOTLIN_VER=$(grep "org-jetbrains-kotlin =" $LIBS_CATALOG | cut -d'"' -f2)
COMPOSE_BOM_VER=$(grep "composeBom =" $LIBS_CATALOG | cut -d'"' -f2)
METRO_VER=$(grep "dev-zacsweers-metro =" $LIBS_CATALOG | cut -d'"' -f2)
ROOM_VER=$(grep "androidx-room =" $LIBS_CATALOG | cut -d'"' -f2)
GRADLE_VER=$(grep "distributionUrl" $GRADLE_WRAPPER | sed 's/.*gradle-\(.*\)-all.zip/\1/')

# Logic to update a specific table in the README
# This script expects a block starting with <!-- START_VERSIONS --> and ending with <!-- END_VERSIONS -->

TEMP_FILE=$(mktemp)

# Create the new table content in a temp file
NEW_TABLE_FILE=$(mktemp)
cat <<EOF > "$NEW_TABLE_FILE"
| Tech | Version |
| :--- | :--- |
| **Kotlin** | $KOTLIN_VER |
| **Gradle** | $GRADLE_VER |
| **Compose BOM** | $COMPOSE_BOM_VER |
| **Metro DI** | $METRO_VER |
| **Room DB** | $ROOM_VER |
EOF

# Use sed to replace the block
# 1. Take everything before START_VERSIONS
# 2. Append START_VERSIONS and the new table
# 3. Take everything after END_VERSIONS
sed -n '1,/<!-- START_VERSIONS -->/p' $README_FILE > "$TEMP_FILE"
cat "$NEW_TABLE_FILE" >> "$TEMP_FILE"
sed -n '/<!-- END_VERSIONS -->/,$p' $README_FILE >> "$TEMP_FILE"

mv "$TEMP_FILE" $README_FILE
rm "$NEW_TABLE_FILE"

echo "✅ README.md updated with latest versions:"
echo "- Kotlin: $KOTLIN_VER"
echo "- Gradle: $GRADLE_VER"
echo "- Compose: $COMPOSE_BOM_VER"
echo "- Metro: $METRO_VER"
