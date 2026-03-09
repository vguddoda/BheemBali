#!/bin/bash

# Java 8 Features - Run All Demos
# Author: Practice Script
# Purpose: Execute all Java 8 examples in sequence

echo "=========================================="
echo "   JAVA 8 FEATURES - ALL DEMOS"
echo "=========================================="

# Compile all files
echo -e "\n📦 Compiling all Java files..."
javac *.java
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi
echo "✅ Compilation successful!"

# Run each demo
demos=(
    "LambdaDemo:Lambda Expressions"
    "StreamsDemo:Streams API"
    "OptionalDemo:Optional"
    "CollectorsDemo:Collectors"
    "DateTimeDemo:Date & Time"
    "MyArrayListDemo:ArrayList Internals"
)

for demo in "${demos[@]}"; do
    IFS=':' read -r class_name title <<< "$demo"
    echo -e "\n"
    echo "=========================================="
    echo "  🎯 $title"
    echo "=========================================="
    java "$class_name"
    echo ""
done

echo "=========================================="
echo "  ✅ ALL DEMOS COMPLETED!"
echo "=========================================="

