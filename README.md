# Distributed File Synchronizer

A distributed file synchronization system similar to Dropbox, built with Java, Spring Boot, and JavaFX. This implementation features advanced conflict detection using version vectors and real-time synchronization.

## 🏗️ Architecture

- **Server**: Spring Boot application with PostgreSQL, Redis, RabbitMQ, and WebSocket support
- **Client**: JavaFX desktop application with local SQLite database and file monitoring
- **Common**: Shared models, DTOs, and version vector implementation

## ✨ Key Features

### Core Synchronization
- **Version Vector Conflict Detection**: Advanced conflict resolution using vector clocks
- **Real-time Updates**: WebSocket-based instant file change notifications
- **File Versioning**: Complete version history

### Security & Authentication
- **JWT Authentication**: Secure token-based authentication with refresh tokens
- **Password Security**: BCrypt hashing for secure password storage
- **CORS Support**: Configured for cross-origin requests

### Advanced Capabilities
- **Multi-client Support**: Concurrent editing with conflict detection
- **Priority Sync Queue**: Intelligent synchronization ordering
- **Audit Trail**: Complete sync event logging
- **File Monitoring**: Automatic detection of local file changes

## 🛠️ Technical Implementation

### Server Components
- **Spring Boot 3.2.0** with Spring Security
- **PostgreSQL** with JPA/Hibernate for persistence
- **Redis** for caching and session management
- **RabbitMQ** for asynchronous task processing
- **WebSocket + STOMP** for real-time communication

### Client Components
- **JavaFX 19** for desktop GUI
- **SQLite** for local state management
- **Apache HttpClient 5** for server communication
- **Java NIO WatchService** for file monitoring

### Database Schema
- **Users**: Account management with quotas
- **Files**: File metadata with version vectors
- **File_Versions**: Complete version history
- **Sync_Events**: Audit trail and synchronization log

## 🚀 Quick Start

**For detailed troubleshooting and step-by-step instructions, see [COMPLETE_STARTUP_GUIDE.md](COMPLETE_STARTUP_GUIDE.md)**

### Prerequisites
- **Java 17+** (OpenJDK or Oracle JDK)
- **Maven 3.8+** for building
- **Docker Desktop** for running services

### 1. Start Infrastructure
```bash
# Start PostgreSQL, Redis, and RabbitMQ
cd "c:\Users\Admin\Documents\GitHub\DistributedSystems"
docker-compose up -d
```

### 2. Build the Project
```bash
mvn clean install
```

### 3. Start the Server
```bash
# Use the provided script
start-server.bat

# Or manually
cd server
mvn spring-boot:run
```

### 4. Start the Client
```bash
# Use the provided script (handles JavaFX runtime automatically)
start-client.bat

# Or manually
cd client
mvn javafx:run
```

## 🔧 Common Issues

- **Docker connection error**: Start Docker Desktop and wait for initialization
- **JavaFX runtime missing**: Use `start-client.bat` or install JavaFX SDK
- **Build failures**: Ensure Java 17+ and Maven 3.8+ are installed
- **Port conflicts**: Stop local PostgreSQL/Redis services

**See [COMPLETE_STARTUP_GUIDE.md](COMPLETE_STARTUP_GUIDE.md) for detailed solutions**

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login
- `POST /api/auth/refresh` - Refresh JWT token
- `POST /api/auth/logout` - User logout

### File Operations
- `GET /api/files/` - List user files
- `POST /api/files/upload` - Upload file
- `GET /api/files/{fileId}/download` - Download file
- `PUT /api/files/{fileId}` - Update file
- `DELETE /api/files/{fileId}` - Delete file
- `GET /api/files/{fileId}/versions` - Get file versions

### Synchronization
- `GET /api/sync/changes` - Get sync changes
- `POST /api/sync/heartbeat` - Send heartbeat
- `WS /api/ws/sync` - WebSocket for real-time sync

## Development

### Database Schema

The system uses the following main tables:
- `users` - User accounts and metadata
- `files` - File metadata and version vectors
- `file_versions` - File version history
- `sync_events` - Synchronization events

### Configuration

Server configuration is in `server/src/main/resources/application.yml`

Key settings:
- Database connection
- Redis configuration
- RabbitMQ settings
- JWT settings
- File storage settings

## Architecture Details

### Synchronization Algorithm

The system uses version vectors for conflict detection:
1. Each file has a version vector stored on both client and server
2. When files are modified, the version vector is incremented for the client
3. During sync, version vectors are compared to detect conflicts
4. Conflicts are resolved using Last-Write-Wins or manual resolution

### File Storage

Files are stored using content-addressed storage:
- Path: `/storage/content/{hash[0:2]}/{hash[2:4]}/{full_hash}`
- Deduplication based on SHA-256 checksums
- Chunked storage for large files

### Security

- JWT tokens for authentication
- AES-256 encryption for files
- TLS for transport security
- CORS and CSRF protection
- Rate limiting

## Testing

Run all tests:
```bash
mvn test
```

Run server tests only:
```bash
cd server && mvn test
```

Run client tests only:
```bash
cd client && mvn test
```

## Monitoring

The server includes Spring Boot Actuator endpoints:
- `/api/actuator/health` - Health check
- `/api/actuator/metrics` - Application metrics
- `/api/actuator/info` - Application info

## Deployment

### Production Build

```bash
mvn clean package -Pprod
```

### Docker Build

Server Dockerfile:
```bash
cd server
docker build -t filesync-server .
```

### Kubernetes

Kubernetes manifests are in the `k8s/` directory.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.
