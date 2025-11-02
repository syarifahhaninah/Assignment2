#!/bin/bash
# Generate proper JavaFX classpath for VS Code

M2_REPO="$HOME/.m2/repository/org/openjfx"
JAVAFX_VERSION="21.0.2"
OS_CLASSIFIER="linux"  # Change to "mac" or "win" for other platforms

# Core JavaFX modules
MODULES=(
    "javafx-base"
    "javafx-graphics"
    "javafx-controls"
    "javafx-fxml"
    "javafx-media"
    "javafx-web"
    "javafx-swing"
)

# Build module path
MODULE_PATH=""
for module in "${MODULES[@]}"; do
    JAR="$M2_REPO/$module/$JAVAFX_VERSION/$module-$JAVAFX_VERSION-$OS_CLASSIFIER.jar"
    if [ -f "$JAR" ]; then
        if [ -z "$MODULE_PATH" ]; then
            MODULE_PATH="$JAR"
        else
            MODULE_PATH="$MODULE_PATH:$JAR"
        fi
    fi
done

echo "Module Path:"
echo "$MODULE_PATH"
echo ""
echo "VM Args:"
echo "--module-path $MODULE_PATH --add-modules javafx.controls,javafx.fxml,javafx.media,javafx.web --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
