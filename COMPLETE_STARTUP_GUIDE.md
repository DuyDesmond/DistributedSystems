# 🚀 Complete Startup Guide

## ✅ Issues Resolved
- ✅ **Docker Compose**: Version warning fixed
- ✅ **Server Compilation**: Fixed missing VersionVector methods
- ✅ **Maven Build**: All modules now compile successfully

## 🔧 Current Issues and Solutions

### 1. Docker Desktop Connection Issue

**Error**: `unable to get image 'redis:7-alpine': error during connect`

**Solution**: 
1. **Start Docker Desktop** from Windows Start menu
2. **Wait for full initialization** (Docker whale icon should be stable)
3. **Verify Docker is running**:
   ```powershell
   docker --version
   docker info
   ```

If Docker Desktop fails to start:
- Try restarting Docker Desktop (right-click whale icon → Restart)
- Or restart your computer
- See [DOCKER_TROUBLESHOOTING.md](DOCKER_TROUBLESHOOTING.md) for detailed steps

### 2. JavaFX Runtime Components Missing

**Error**: `JavaFX runtime components are missing, and are required to run this application`

**Solutions**:

#### Option A: Use the Provided Startup Script (Recommended)
```bash
# Simply run this script - it tries multiple methods automatically
start-client.bat
```

#### Option B: Manual JavaFX Setup
1. **Download JavaFX SDK**:
   - Go to https://openjfx.io/
   - Download JavaFX 21 SDK for Windows
   - Extract to a folder (e.g., `C:\Program Files\JavaFX\`)

2. **Run with JavaFX path**:
   ```bash
   java --module-path "C:\Program Files\JavaFX\lib" --add-modules javafx.controls,javafx.fxml -cp "target\classes;target\dependency\*" com.filesync.client.FileSyncClientApplication
   ```

#### Option C: Use IDE
- **IntelliJ IDEA**: File → Project Structure → Libraries → Add JavaFX
- **Eclipse**: Project Properties → Java Build Path → Add External JARs
- **VS Code**: Install Java extensions and configure JavaFX

## 🚀 Complete Startup Sequence

### Step 1: Start Docker Desktop
```bash
# Ensure Docker Desktop is running
docker --version
# Should show version without errors
```

### Step 2: Start Infrastructure Services
```bash
cd "c:\Users\Admin\Documents\GitHub\DistributedSystems"
docker-compose up -d
```

**Verify services**:
```bash
docker-compose ps
# Should show 3 running containers: postgres, redis, rabbitmq
```

### Step 3: Start the Server
```bash
# Option A: Use the startup script
start-server.bat

# Option B: Manual start
cd server
mvn spring-boot:run
```

**Server should start on**: http://localhost:8080

### Step 4: Start the Client
```bash
# Option A: Use the startup script (recommended)
start-client.bat

# Option B: Manual start
cd client
mvn javafx:run
```

## 🔍 Troubleshooting

### Server Issues
1. **Port 8080 already in use**:
   ```bash
   netstat -ano | findstr :8080
   # Kill the process using the port
   taskkill /PID <process_id> /F
   ```

2. **Database connection errors**:
   ```bash
   # Check if PostgreSQL container is running
   docker logs filesync-postgres
   ```

### Client Issues
1. **JavaFX not found**: Use the startup script or install JavaFX SDK
2. **Connection refused**: Ensure the server is running first
3. **Build errors**: Run `mvn clean install` in the root directory

### Docker Issues
1. **Cannot connect to Docker**: Start Docker Desktop and wait for initialization
2. **Port conflicts**: Stop local services using ports 5432, 6379, 5672
3. **Image pull failures**: Check internet connection and Docker Hub access

## 📊 Health Checks

### Verify All Services are Running
```bash
# Check Docker containers
docker-compose ps

# Test database
docker exec -it filesync-postgres psql -U filesync -d filesync -c "SELECT 1;"

# Test Redis
docker exec -it filesync-redis redis-cli ping

# Test RabbitMQ Management UI
# Open browser: http://localhost:15672 (guest/guest)

# Test Server API
curl http://localhost:8080/api/auth/test
```

## 🎯 Quick Start Commands

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Start server
start-server.bat

# 3. Start client (in new terminal)
start-client.bat
```

## 📝 Notes

- **All credentials are pre-configured** for local development
- **No configuration changes needed** unless specified
- **Default ports**: 8080 (server), 5432 (postgres), 6379 (redis), 5672/15672 (rabbitmq)
- **For production**: Change JWT secret and database passwords

## 🆘 If Everything Fails

1. **Restart your computer** (solves most Docker/Java path issues)
2. **Use an IDE** instead of command line (IntelliJ IDEA, Eclipse, VS Code)
3. **Check Java version**: `java -version` (should be 17 or higher)
4. **Reinstall Docker Desktop** if Docker issues persist
