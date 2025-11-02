#!/bin/bash
# Smart Collections Manager - Build Verification Script

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}============================================${NC}"
echo -e "${CYAN}Smart Collections Manager                   ${NC}"
echo -e "${CYAN}Build & Environment Verification            ${NC}"
echo -e "${CYAN}============================================${NC}"
echo ""

ERRORS=0
WARNINGS=0

# Function to check command
check_command() {
    if command -v "$1" &> /dev/null; then
        echo -e "${GREEN}✓${NC} $1 is installed"
        return 0
    else
        echo -e "${RED}✗${NC} $1 is NOT installed"
        return 1
    fi
}

# Function to print section header
section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# 1. Check Java
section "1. Java Environment"
if check_command java; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    JAVA_MAJOR=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    echo "   Version: $JAVA_VERSION"
    
    if [ "$JAVA_MAJOR" -ge 17 ]; then
        echo -e "${GREEN}✓${NC} Java version is compatible (17+)"
    else
        echo -e "${RED}✗${NC} Java version must be 17 or higher"
        ((ERRORS++))
    fi
    
    if [ -n "$JAVA_HOME" ]; then
        echo -e "${GREEN}✓${NC} JAVA_HOME is set: $JAVA_HOME"
    else
        echo -e "${YELLOW}⚠${NC} JAVA_HOME is not set (optional but recommended)"
        ((WARNINGS++))
    fi
else
    echo -e "${RED}✗${NC} Java is required but not found"
    ((ERRORS++))
fi

# 2. Check Maven
section "2. Maven Build Tool"
if check_command mvn; then
    MVN_VERSION=$(mvn -version 2>&1 | head -n 1 | awk '{print $3}')
    echo "   Version: $MVN_VERSION"
    echo -e "${GREEN}✓${NC} Maven is ready"
else
    echo -e "${RED}✗${NC} Maven is required for building"
    ((ERRORS++))
fi

# 3. Check Project Structure
section "3. Project Structure"
if [ -f "pom.xml" ]; then
    echo -e "${GREEN}✓${NC} pom.xml found"
else
    echo -e "${RED}✗${NC} pom.xml not found"
    ((ERRORS++))
fi

if [ -d "src/main/java" ]; then
    JAVA_FILES=$(find src/main/java -name "*.java" | wc -l)
    echo -e "${GREEN}✓${NC} Source directory found ($JAVA_FILES .java files)"
else
    echo -e "${RED}✗${NC} Source directory not found"
    ((ERRORS++))
fi

if [ -d "src/main/resources" ]; then
    CSS_FILES=$(find src/main/resources -name "*.css" | wc -l)
    echo -e "${GREEN}✓${NC} Resources directory found ($CSS_FILES .css files)"
else
    echo -e "${YELLOW}⚠${NC} Resources directory not found"
    ((WARNINGS++))
fi

# 4. Check Dependencies
section "4. Maven Dependencies"
if [ -f "pom.xml" ] && command -v mvn &> /dev/null; then
    echo "Checking dependencies (this may take a moment)..."
    if mvn dependency:resolve -q > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} All dependencies resolved"
        
        # Count dependencies
        DEP_COUNT=$(mvn dependency:list 2>/dev/null | grep -c ":.*:.*:.*:.*")
        echo "   Total dependencies: $DEP_COUNT"
    else
        echo -e "${RED}✗${NC} Some dependencies could not be resolved"
        ((ERRORS++))
    fi
fi

# 5. Check Build Artifacts
section "5. Build Artifacts"
if [ -d "target" ]; then
    echo -e "${GREEN}✓${NC} Target directory exists"
    
    # Check for JAR files
    if [ -f "target/smart-collections-manager-1.0.0.jar" ]; then
        SIZE=$(du -h "target/smart-collections-manager-1.0.0.jar" | cut -f1)
        echo -e "${GREEN}✓${NC} Regular JAR: smart-collections-manager-1.0.0.jar ($SIZE)"
    else
        echo -e "${YELLOW}⚠${NC} Regular JAR not found (run: mvn package)"
        ((WARNINGS++))
    fi
    
    if [ -f "target/smart-collections-manager-1.0.0-all.jar" ]; then
        SIZE=$(du -h "target/smart-collections-manager-1.0.0-all.jar" | cut -f1)
        echo -e "${GREEN}✓${NC} Fat JAR (Shade): smart-collections-manager-1.0.0-all.jar ($SIZE)"
    else
        echo -e "${YELLOW}⚠${NC} Fat JAR (Shade) not found (run: mvn package)"
        ((WARNINGS++))
    fi
    
    if [ -f "target/smart-collections-manager-1.0.0-jar-with-dependencies.jar" ]; then
        SIZE=$(du -h "target/smart-collections-manager-1.0.0-jar-with-dependencies.jar" | cut -f1)
        echo -e "${GREEN}✓${NC} Fat JAR (Assembly): *-jar-with-dependencies.jar ($SIZE)"
    else
        echo -e "${YELLOW}⚠${NC} Fat JAR (Assembly) not found (run: mvn package)"
        ((WARNINGS++))
    fi
    
    if [ -d "target/lib" ]; then
        LIB_COUNT=$(ls target/lib/*.jar 2>/dev/null | wc -l)
        echo -e "${GREEN}✓${NC} Lib directory with $LIB_COUNT dependency JARs"
    else
        echo -e "${YELLOW}⚠${NC} Lib directory not found (run: mvn package)"
        ((WARNINGS++))
    fi
    
    if [ -d "target/classes" ]; then
        CLASS_COUNT=$(find target/classes -name "*.class" | wc -l)
        echo -e "${GREEN}✓${NC} Compiled classes: $CLASS_COUNT .class files"
    else
        echo -e "${YELLOW}⚠${NC} Classes directory not found (run: mvn compile)"
        ((WARNINGS++))
    fi
else
    echo -e "${YELLOW}⚠${NC} Target directory not found (run: mvn package)"
    ((WARNINGS++))
fi

# 6. Check Launcher Scripts
section "6. Launcher Scripts"
if [ -f "run.sh" ]; then
    if [ -x "run.sh" ]; then
        echo -e "${GREEN}✓${NC} run.sh found and executable"
    else
        echo -e "${YELLOW}⚠${NC} run.sh found but not executable (run: chmod +x run.sh)"
        ((WARNINGS++))
    fi
else
    echo -e "${YELLOW}⚠${NC} run.sh not found"
    ((WARNINGS++))
fi

if [ -f "run.bat" ]; then
    echo -e "${GREEN}✓${NC} run.bat found (Windows launcher)"
else
    echo -e "${YELLOW}⚠${NC} run.bat not found"
    ((WARNINGS++))
fi

# 7. Try a test build
section "7. Test Build"
if command -v mvn &> /dev/null && [ -f "pom.xml" ]; then
    echo "Attempting test compilation..."
    if mvn compile -q > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Test compilation successful"
    else
        echo -e "${RED}✗${NC} Test compilation failed"
        ((ERRORS++))
    fi
else
    echo -e "${YELLOW}⚠${NC} Skipping test build (Maven not available or pom.xml missing)"
fi

# 8. Check Documentation
section "8. Documentation"
if [ -f "README.md" ]; then
    echo -e "${GREEN}✓${NC} README.md found"
else
    echo -e "${YELLOW}⚠${NC} README.md not found"
    ((WARNINGS++))
fi

if [ -f "BUILD.md" ]; then
    echo -e "${GREEN}✓${NC} BUILD.md found"
else
    echo -e "${YELLOW}⚠${NC} BUILD.md not found"
    ((WARNINGS++))
fi

# Summary
section "SUMMARY"
echo ""
if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}✓ ALL CHECKS PASSED!${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "Your environment is fully configured and ready to build/run."
    echo ""
    echo "Next steps:"
    echo "  • Build project:     mvn clean package"
    echo "  • Run application:   ./run.sh"
    echo "  • Or use Maven:      mvn javafx:run"
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}⚠ PASSED WITH $WARNINGS WARNING(S)${NC}"
    echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "Your environment is mostly configured, but some optional components are missing."
    echo "The project should build and run, but consider addressing the warnings above."
    echo ""
    echo "To build and run:"
    echo "  • Build project:     mvn clean package"
    echo "  • Run application:   ./run.sh"
else
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${RED}✗ FAILED WITH $ERRORS ERROR(S) AND $WARNINGS WARNING(S)${NC}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "Please address the errors above before building the project."
    echo ""
    echo "Common solutions:"
    echo "  • Install Java 17+:  https://adoptium.net/"
    echo "  • Install Maven:     https://maven.apache.org/install.html"
    echo "  • Set JAVA_HOME:     export JAVA_HOME=/path/to/java"
fi

echo ""
exit $ERRORS
