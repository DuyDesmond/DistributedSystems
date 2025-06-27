# Setup Guide - Credentials and Configuration

## Required Credentials and Configuration Changes

### 1. **Docker Setup** ⚠️ **CRITICAL**

**Before running the project, you MUST:**

1. **Install Docker Desktop** if not already installed
   - Download from: https://www.docker.com/products/docker-desktop/
   - Install and start Docker Desktop
   - Ensure Docker Desktop is running (you should see the Docker whale icon in your system tray)

2. **Start the infrastructure services:**
   ```bash
   cd c:\Users\Admin\Documents\GitHub\DistributedSystems
   docker-compose up -d
   ```

### 2. **Database Credentials** ✅ **ALREADY CONFIGURED**

The following credentials are **already set up** in `application.yml` and `docker-compose.yml`:

```yaml
# PostgreSQL (matches docker-compose.yml)
Database: filesync
Username: filesync
Password: filesync123
Port: 5432
```

### 3. **Redis Configuration** ✅ **ALREADY CONFIGURED**

```yaml
# Redis (no password required for development)
Host: localhost
Port: 6379
Password: (empty/none)
```

### 4. **RabbitMQ Configuration** ✅ **ALREADY CONFIGURED**

```yaml
# RabbitMQ (default guest credentials)
Host: localhost
Port: 5672
Username: guest
Password: guest
Management UI: http://localhost:15672
```

### 5. **JWT Secret** ⚠️ **OPTIONAL - CHANGE FOR PRODUCTION**

The JWT secret is currently set to a default value. For production, you should change it:

**Current (development):**
```yaml
security:
  jwt:
    secret: mySecretKey1234567890123456789012345678901234567890
```

**For production, set environment variable:**
```bash
export JWT_SECRET=your-very-secure-secret-key-here
```

### 6. **Client Configuration** ✅ **NO CHANGES NEEDED**

The client configuration in `ClientConfig.java` uses:
- Server URL: `http://localhost:8080/api` (matches server setup)
- Sync folder: `./sync` (will be created automatically)

## 🚀 **Complete Startup Sequence**

### Step 1: Start Docker Desktop
- Ensure Docker Desktop is running
- You should see the Docker icon in your system tray

### Step 2: Start Infrastructure
```bash
cd c:\Users\Admin\Documents\GitHub\DistributedSystems
docker-compose up -d
```

### Step 3: Verify Services
```bash
# Check if containers are running
docker ps

# You should see 3 containers:
# - filesync-postgres
# - filesync-redis  
# - filesync-rabbitmq
```

### Step 4: Build the Project
```bash
mvn clean install
```

### Step 5: Start the Server
```bash
cd server
mvn spring-boot:run
```

### Step 6: Start the Client
```bash
cd client
mvn javafx:run
```

## 🔧 **Troubleshooting**

### Docker Issues:
- **Error: "The system cannot find the file specified"**
  - Solution: Start Docker Desktop and wait for it to fully initialize

- **Error: "port already in use"**
  - Solution: Stop any existing services on ports 5432, 6379, or 5672

### Database Issues:
- **Connection refused**
  - Solution: Ensure PostgreSQL container is running (`docker ps`)
  - Check logs: `docker logs filesync-postgres`

### Redis Issues:
- **Redis connection timeout**
  - Solution: Ensure Redis container is running
  - Check logs: `docker logs filesync-redis`

### RabbitMQ Issues:
- **RabbitMQ not responding**
  - Solution: Ensure RabbitMQ container is running
  - Access management UI: http://localhost:15672 (guest/guest)

## 📝 **No Credential Changes Required**

**Summary: You do NOT need to change any credentials for development use.** 

All credentials are pre-configured and match between:
- `docker-compose.yml` (infrastructure setup)
- `application.yml` (server configuration)
- `application-test.yml` (test configuration)
- Client configuration

The only change you might want to make is the JWT secret for production use, but it's not required for development and testing.

## 🔒 **Security Notes**

These are **development credentials only**. For production deployment:

1. Change all default passwords
2. Use environment variables for sensitive data
3. Set up proper network security
4. Use SSL/TLS certificates
5. Configure firewall rules
6. Use strong JWT secrets
