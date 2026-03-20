#!/bin/bash
# Quick test script for Java examples

echo "🚀 Testing Java Examples..."
echo ""

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not installed. Install with: brew install maven"
    exit 1
fi
echo "✅ Maven found"

# Check Docker
if ! docker ps &> /dev/null; then
    echo "❌ Docker not running. Start with: ./setup.sh"
    exit 1
fi
echo "✅ Docker running"

# Build Producer
echo ""
echo "📦 Building Producer..."
cd examples/java/producer
mvn clean package -q
if [ $? -eq 0 ]; then
    echo "✅ Producer built"
else
    echo "❌ Producer build failed"
    exit 1
fi

# Build Consumer
echo ""
echo "📦 Building Consumer..."
cd ../consumer
mvn clean package -q
if [ $? -eq 0 ]; then
    echo "✅ Consumer built"
else
    echo "❌ Consumer build failed"
    exit 1
fi

echo ""
echo "✅ ALL READY!"
echo ""
echo "Run examples:"
echo "  Producer: cd examples/java/producer && mvn exec:java -Dexec.mainClass=\"SimpleProducer\""
echo "  Consumer: cd examples/java/consumer && mvn exec:java -Dexec.mainClass=\"SimpleConsumer\""
echo ""

