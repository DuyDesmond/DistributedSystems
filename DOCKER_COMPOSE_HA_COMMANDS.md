# Docker Compose High Availability Commands

This document contains all the essential Docker Compose commands for managing the high-availability distributed file sync system.

## Starting Services

### Start All Services
```bash
docker-compose -f docker-compose-ha.yml up -d
```

### Start Services with Build (if images need rebuilding)
```bash
docker-compose -f docker-compose-ha.yml up -d --build
```

### Start Specific Services
```bash
# Start only infrastructure services
docker-compose -f docker-compose-ha.yml up -d postgres redis rabbitmq

# Start only servers (after infrastructure is running)
docker-compose -f docker-compose-ha.yml up -d server-1 server-2 server-3

# Start nginx load balancer (after servers are running)
docker-compose -f docker-compose-ha.yml up -d nginx

# Start clients (after nginx is running)
docker-compose -f docker-compose-ha.yml up -d client1 client2
```

## Stopping Services

### Stop All Services
```bash
docker-compose -f docker-compose-ha.yml down
```

### Stop All Services and Remove Volumes (⚠️ Data Loss Warning)
```bash
docker-compose -f docker-compose-ha.yml down -v
```

### Stop Specific Services
```bash
# Stop clients only
docker-compose -f docker-compose-ha.yml stop client1 client2

# Stop servers only
docker-compose -f docker-compose-ha.yml stop server-1 server-2 server-3

# Stop nginx load balancer
docker-compose -f docker-compose-ha.yml stop nginx
```

## Monitoring and Troubleshooting

### Check Service Status
```bash
docker-compose -f docker-compose-ha.yml ps
```

### View Logs
```bash
# All services
docker-compose -f docker-compose-ha.yml logs

# Specific service
docker-compose -f docker-compose-ha.yml logs nginx
docker-compose -f docker-compose-ha.yml logs server-1
docker-compose -f docker-compose-ha.yml logs client1

# Follow logs in real-time
docker-compose -f docker-compose-ha.yml logs -f nginx

# View logs with timestamps
docker-compose -f docker-compose-ha.yml logs -t server-1
```

### Restart Services
```bash
# Restart all services
docker-compose -f docker-compose-ha.yml restart

# Restart specific service
docker-compose -f docker-compose-ha.yml restart nginx
docker-compose -f docker-compose-ha.yml restart server-1
```

### Execute Commands in Running Containers
```bash
# Connect to postgres
docker exec -it filesync-postgres-master psql -U filesync -d filesync

# Connect to redis
docker exec -it filesync-redis redis-cli

# Check nginx configuration
docker exec -it filesync-nginx nginx -t

# Access server container shell
docker exec -it filesync-server-1 /bin/sh
```

## Service Access Points

### Web Interfaces
- **Load Balancer**: http://localhost:80
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)
- **Server 1 Direct**: http://localhost:8081
- **Server 2 Direct**: http://localhost:8082  
- **Server 3 Direct**: http://localhost:8083
- **Client 1 Interface**: http://localhost:8084
- **Client 2 Interface**: http://localhost:8085

### Database Connections
- **PostgreSQL**: localhost:5432 (filesync/filesync123)
- **Redis**: localhost:6379
- **RabbitMQ**: localhost:5672 (guest/guest)

## Useful Flag Explanations

- `-f docker-compose-ha.yml`: Specify the compose file to use
- `-d`: Run in detached mode (background)
- `--build`: Force rebuild images before starting
- `-v`: Remove named volumes when stopping (⚠️ deletes data)
- `--remove-orphans`: Remove containers for services not defined in compose file
- `--force-recreate`: Recreate containers even if config hasn't changed

## Health Checks and Status

### Check Individual Container Health
```bash
# Check all container status
docker ps --filter "name=filesync-"

# Check specific container health
docker inspect filesync-nginx --format='{{.State.Health.Status}}'
docker inspect filesync-server-1 --format='{{.State.Health.Status}}'
```

### Test Load Balancer
```bash
# Test API endpoint through load balancer
curl http://localhost:80/api/actuator/health

# Test direct server endpoints
curl http://localhost:8081/api/actuator/health
curl http://localhost:8082/api/actuator/health
curl http://localhost:8083/api/actuator/health
```

## Quick Recovery Commands

### If nginx fails to start
```bash
# Check nginx configuration
docker-compose -f docker-compose-ha.yml exec nginx nginx -t

# Restart nginx
docker-compose -f docker-compose-ha.yml restart nginx
```

### If servers fail to connect to dependencies
```bash
# Restart in dependency order
docker-compose -f docker-compose-ha.yml restart postgres redis rabbitmq
sleep 30
docker-compose -f docker-compose-ha.yml restart server-1 server-2 server-3
sleep 15
docker-compose -f docker-compose-ha.yml restart nginx
```

### Complete Clean Restart
```bash
# Stop everything
docker-compose -f docker-compose-ha.yml down

# Remove any orphaned containers
docker container prune -f

# Start infrastructure first
docker-compose -f docker-compose-ha.yml up -d postgres redis rabbitmq

# Wait for health checks, then start servers
sleep 60
docker-compose -f docker-compose-ha.yml up -d server-1 server-2 server-3

# Wait for servers, then start nginx and clients
sleep 30
docker-compose -f docker-compose-ha.yml up -d nginx client1 client2
```

## Notes

- Always start infrastructure services (postgres, redis, rabbitmq) first
- Wait for health checks to pass before starting dependent services
- The nginx load balancer distributes requests across all three server instances
- Clients connect through the load balancer for high availability
- Use health check endpoints to verify service status before proceeding
