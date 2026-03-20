#!/bin/bash

# Kafka Interview Prep - Quick Setup Script
# This script sets up the Kafka cluster and verifies everything is working

set -e

echo "=================================================="
echo "🚀 Kafka Interview Prep - Setup Script"
echo "=================================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if Docker is installed
echo "Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker and Docker Compose are installed${NC}"
echo ""

# Start Kafka cluster
echo "Starting Kafka cluster..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to start (this may take 30-60 seconds)..."
sleep 30

# Check if containers are running
echo ""
echo "Checking container status..."
RUNNING=$(docker ps --filter "name=kafka" --format "{{.Names}}" | wc -l | tr -d ' ')

if [ "$RUNNING" -lt 6 ]; then
    echo -e "${RED}❌ Not all containers are running${NC}"
    echo "Running containers:"
    docker ps --filter "name=kafka" --format "table {{.Names}}\t{{.Status}}"
    echo ""
    echo "Check logs with: docker-compose logs"
    exit 1
fi

echo -e "${GREEN}✅ All Kafka containers are running${NC}"
echo ""

# Wait a bit more for brokers to be fully ready
echo "⏳ Waiting for Kafka brokers to be ready..."
sleep 20

# Test Kafka cluster
echo ""
echo "Testing Kafka cluster..."

# Create a test topic
echo "Creating test topic..."
docker exec kafka-broker-1 kafka-topics --create \
  --bootstrap-server localhost:19092 \
  --topic test-connection \
  --partitions 3 \
  --replication-factor 3 \
  --if-not-exists &> /dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Test topic created successfully${NC}"
else
    echo -e "${RED}❌ Failed to create test topic${NC}"
    exit 1
fi

# List topics
TOPICS=$(docker exec kafka-broker-1 kafka-topics --list --bootstrap-server localhost:19092 2>/dev/null)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Successfully connected to Kafka cluster${NC}"
else
    echo -e "${RED}❌ Failed to connect to Kafka cluster${NC}"
    exit 1
fi

# Test produce and consume
echo ""
echo "Testing message produce and consume..."
echo "test message" | docker exec -i kafka-broker-1 kafka-console-producer \
  --bootstrap-server localhost:19092 \
  --topic test-connection &> /dev/null

sleep 2

MESSAGE=$(timeout 5 docker exec kafka-broker-1 kafka-console-consumer \
  --bootstrap-server localhost:19092 \
  --topic test-connection \
  --from-beginning \
  --max-messages 1 2>/dev/null)

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Message produce and consume working${NC}"
else
    echo -e "${YELLOW}⚠️  Could not verify message delivery (may be timing issue)${NC}"
fi

# Show cluster status
echo ""
echo "=================================================="
echo "📊 Kafka Cluster Status"
echo "=================================================="
echo ""

echo "Running containers:"
docker ps --filter "name=kafka" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "=================================================="
echo "🌐 Access Information"
echo "=================================================="
echo ""
echo -e "${BLUE}Kafka UI:${NC} http://localhost:8080"
echo -e "${BLUE}Schema Registry:${NC} http://localhost:8081"
echo -e "${BLUE}Kafka Connect:${NC} http://localhost:8083"
echo ""
echo -e "${BLUE}Bootstrap Servers (from host):${NC}"
echo "  localhost:19092,localhost:19093,localhost:19094"
echo ""
echo -e "${BLUE}To access broker container:${NC}"
echo "  docker exec -it kafka-broker-1 bash"
echo ""

# Show existing topics
echo "=================================================="
echo "📝 Existing Topics"
echo "=================================================="
echo ""
docker exec kafka-broker-1 kafka-topics --list --bootstrap-server localhost:19092 2>/dev/null | grep -v "^__" || echo "No user topics yet"
echo ""

# Clean up test topic
docker exec kafka-broker-1 kafka-topics --delete \
  --bootstrap-server localhost:19092 \
  --topic test-connection &> /dev/null

echo "=================================================="
echo "🎉 Setup Complete!"
echo "=================================================="
echo ""
echo -e "${GREEN}Your Kafka cluster is ready for interview prep!${NC}"
echo ""
echo "Next steps:"
echo "1. Open Kafka UI at http://localhost:8080"
echo "2. Start with Day 1 labs: labs/day1-fundamentals/"
echo "3. Review interview questions: interview-questions/day1-fundamentals.md"
echo "4. Use the cheat sheet: cheat-sheet.md"
echo ""
echo "📚 Quick Start Guide: QUICK_START.md"
echo "🎓 Welcome Guide: WELCOME.md"
echo ""
echo "To stop the cluster:"
echo "  docker-compose down"
echo ""
echo "To stop and remove all data:"
echo "  docker-compose down -v"
echo ""
echo -e "${GREEN}Happy learning! 🚀${NC}"
echo ""

