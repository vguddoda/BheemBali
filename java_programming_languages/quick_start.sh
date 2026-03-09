#!/bin/bash

# Quick Start - Compile and Run First Demo

echo "╔══════════════════════════════════════════════╗"
echo "║   Java 8 Features - Quick Start             ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# Compile
echo "📦 Compiling Java files..."
javac LambdaDemo.java StreamsDemo.java MyArrayListDemo.java 2>&1 | grep -v "warning:"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🚀 Running LambdaDemo (Functional Interfaces)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    java LambdaDemo
    echo ""

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "🎯 Running StreamsDemo (filter & map)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    java StreamsDemo
    echo ""

    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "⚙️  Running MyArrayListDemo (Internals)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    java MyArrayListDemo
    echo ""

    echo "╔══════════════════════════════════════════════╗"
    echo "║  ✅ Quick Start Complete!                    ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""
    echo "📖 Next steps:"
    echo "   1. Read START_HERE.txt for full overview"
    echo "   2. Check CHEATSHEET.md for quick reference"
    echo "   3. Run: ./run_all.sh to see all demos"
    echo ""
else
    echo "❌ Compilation failed. Check for errors above."
fi

