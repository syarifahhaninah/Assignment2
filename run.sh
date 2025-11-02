#!/bin/bash
# Smart Collections Manager - Linux/macOS Launcher Script

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}Smart Collections Manager - Launcher${NC}"
echo -e "${GREEN}=====================================${NC}"

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
elif command -v java &> /dev/null; then
    JAVA="java"
else
    echo -e "${RED}Error: Java not found!${NC}"
    echo "Please install Java 17 or later and set JAVA_HOME"
    exit 1
fi

# Check Java version
JAVA_VERSION=$("$JAVA" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17 or later is required (found version $JAVA_VERSION)${NC}"
    exit 1
fi

echo -e "${GREEN}Java Version:${NC} $("$JAVA" -version 2>&1 | head -n 1)"

# Get the directory of this script
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Check if Maven is available
if command -v mvn &> /dev/null; then
    MAVEN_AVAILABLE=true
else
    MAVEN_AVAILABLE=false
fi

# Check for build artifacts
if [ ! -d "target" ] || [ -z "$(ls -A target/*.jar 2>/dev/null)" ]; then
    if [ "$MAVEN_AVAILABLE" = true ]; then
        echo -e "${YELLOW}Build artifacts not found. Building project...${NC}"
        mvn clean package
    else
        echo -e "${RED}Error: No build artifacts found and Maven is not available!${NC}"
        echo "Please install Maven and run: mvn clean package"
        exit 1
    fi
fi

# Determine run method
if [ "$MAVEN_AVAILABLE" = true ] && [ "$1" != "--jar" ]; then
    # Method 1: Use Maven (most reliable for JavaFX)
    echo -e "${BLUE}Running via Maven (recommended)...${NC}"
    echo ""
    mvn javafx:run
    EXIT_CODE=$?
else
    # Method 2: Try to run JAR with classpath
    echo -e "${BLUE}Running via JAR file...${NC}"
    
    if [ -f "target/smart-collections-manager-1.0.0.jar" ] && [ -d "target/lib" ]; then
        echo -e "${GREEN}Using JAR with lib directory...${NC}"
        JAR_FILE="target/smart-collections-manager-1.0.0.jar"
        
        # Build classpath with lib directory
        CLASSPATH="$JAR_FILE"
        for jar in target/lib/*.jar; do
            CLASSPATH="$CLASSPATH:$jar"
        done
        
        echo ""
        "$JAVA" -cp "$CLASSPATH" com.smartcollections.SmartCollectionsApp "$@"
        EXIT_CODE=$?
        
    elif [ -f "target/smart-collections-manager-1.0.0-all.jar" ]; then
        echo -e "${GREEN}Using fat JAR...${NC}"
        echo -e "${YELLOW}Note: JavaFX fat JARs may not work. Use Maven if issues occur.${NC}"
        echo ""
        "$JAVA" -jar "target/smart-collections-manager-1.0.0-all.jar" "$@"
        EXIT_CODE=$?
        
        if [ $EXIT_CODE -ne 0 ]; then
            echo ""
            echo -e "${YELLOW}JAR execution failed. Trying Maven...${NC}"
            if [ "$MAVEN_AVAILABLE" = true ]; then
                mvn javafx:run
                EXIT_CODE=$?
            fi
        fi
    else
        echo -e "${RED}Error: No runnable JAR files found!${NC}"
        echo "Please run: mvn clean package"
        exit 1
    fi
fi

if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo -e "${RED}Application exited with error code: $EXIT_CODE${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting:${NC}"
    echo "1. Ensure Java 17+ is installed"
    echo "2. Try running with Maven: mvn javafx:run"
    echo "3. Check for error messages above"
fi

exit $EXIT_CODE
