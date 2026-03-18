#!/bin/bash

# Test Kafka setup script

echo "Testing Kafka Interview Prep Repository..."
echo ""

# Check if docker-compose.yml exists
if [ -f "docker-compose.yml" ]; then
    echo "✅ docker-compose.yml found"
else
    echo "❌ docker-compose.yml not found"
    exit 1
fi

# Check if setup.sh exists
if [ -f "setup.sh" ]; then
    echo "✅ setup.sh found"
else
    echo "❌ setup.sh not found"
fi

# Check if README.md exists
if [ -f "README.md" ]; then
    echo "✅ README.md found"
else
    echo "❌ README.md not found"
fi

# Check if QUICK_START.md exists
if [ -f "QUICK_START.md" ]; then
    echo "✅ QUICK_START.md found"
else
    echo "❌ QUICK_START.md not found"
fi

# Check interview questions
if [ -d "interview-questions" ]; then
    echo "✅ interview-questions directory found"
    count=$(ls -1 interview-questions/*.md 2>/dev/null | wc -l)
    echo "   Found $count question files"
else
    echo "❌ interview-questions directory not found"
fi

# Check labs
if [ -d "labs" ]; then
    echo "✅ labs directory found"
else
    echo "❌ labs directory not found"
fi

# Check examples
if [ -d "examples" ]; then
    echo "✅ examples directory found"
else
    echo "❌ examples directory not found"
fi

echo ""
echo "Repository structure check complete!"
echo ""
echo "To get started:"
echo "1. Run: ./setup.sh"
echo "2. Read: QUICK_START.md"
echo "3. Start: Day 1 labs"

