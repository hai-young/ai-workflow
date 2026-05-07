#!/bin/bash

# Backend Start Script

echo "=================================="
echo "Starting Spring Boot Backend"
echo "=================================="
echo ""

cd "D:/zhy_code/ai-workflow/backend/ai-orchestrator-zhy"

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed or not in PATH"
    exit 1
fi

echo "✓ Java found: $(java -version 2>&1 | head -1)"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed or not in PATH"
    exit 1
fi

echo "✓ Maven found: $(mvn -version 2>&1 | head -1)"
echo ""

# Clean and build
echo "Step 1: Cleaning and building..."
mvnw clean compile -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✓ Build successful"
echo ""

# Start the application
echo "Step 2: Starting application..."
echo ""
echo "You can now access:"
echo "  - API endpoints: http://localhost:8080/api"
echo "  - Home: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

mvnw spring-boot:run

# If the server stops
echo ""
echo "Server stopped"
