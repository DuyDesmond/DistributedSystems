#!/bin/bash

# Start the distributed file sync system with two clients

echo "Starting File Sync System with two clients..."

# Start all services
docker-compose up -d

echo "Waiting for services to be ready..."
sleep 30

echo "Services started:"
echo "- Server: http://localhost:8080"
echo "- Client 1: Container filesync-client1 (Port 8081)"
echo "- Client 2: Container filesync-client2 (Port 8082)"
echo "- PostgreSQL: localhost:5432"
echo "- Redis: localhost:6379"
echo "- RabbitMQ Management: http://localhost:15672"

echo ""
echo "To view logs:"
echo "docker logs filesync-server"
echo "docker logs filesync-client1"
echo "docker logs filesync-client2"

echo ""
echo "To access client sync directories:"
echo "Client 1: ./client/sync/"
echo "Client 2: ./client2/sync/"

echo ""
echo "To simulate device sync:"
echo "1. Add files to ./client/sync/ and watch them sync to ./client2/sync/"
echo "2. Add files to ./client2/sync/ and watch them sync to ./client/sync/"
