#!/bin/bash
# Simple launcher for Smart Collections Manager
# Just run: ./launch.sh

echo "🚀 Smart Collections Manager Launcher"
echo "======================================"
echo ""

cd "$(dirname "$0")"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed"
    echo "Install with: sudo apt install maven"
    exit 1
fi

# Compile if needed
if [ ! -d "target/classes" ]; then
    echo "📦 First run - compiling project..."
    mvn compile -q
    echo ""
fi

# Run the application
echo "▶️  Launching application..."
echo ""
mvn javafx:run
