#!/bin/bash

# Quick test script for Java 8 examples

echo "==================================="
echo "Java 8 Examples - Quick Test"
echo "==================================="
echo ""

files=("LambdaDemo" "StreamsDemo" "OptionalDemo" "DateTimeDemo" "CollectorsDemo")

for file in "${files[@]}"; do
    echo "Testing $file..."
    javac "$file.java"
    if [ $? -eq 0 ]; then
        echo "✓ Compiled successfully"
    else
        echo "✗ Compilation failed"
        exit 1
    fi
done

echo ""
echo "All examples compiled successfully!"
echo ""
echo "To run an example:"
echo "  java LambdaDemo"
echo "  java StreamsDemo"
echo "  java OptionalDemo"
echo "  java DateTimeDemo"
echo "  java CollectorsDemo"
echo ""

# Clean up
rm -f *.class
echo "Cleaned up class files."

