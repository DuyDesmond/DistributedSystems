# Distributed File Synchronization System

A **desktop file synchronization system** (like Dropbox) built with Python, providing both CLI and GUI interfaces for managing file synchronization. This is a native desktop application with visual monitoring and command-line control - not a web application.

## Architecture

The system follows a client-server architecture with:

- **Server**: FastAPI-based REST API with WebSocket support for real-time updates
- **Desktop Client**: Python client with file system monitoring and sync capabilities
- **CLI Interface**: Command-line tool for file and user management
- **Desktop GUI**: Tkinter-based desktop interface for visual monitoring and control
- **Database**: PostgreSQL for metadata storage
- **Cache**: Redis for session management and real-time updates
- **Message Queue**: RabbitMQ for asynchronous processing

## Features

- **Desktop Application**: Native desktop GUI and CLI interfaces (not a web app)
- Real-time file synchronization with visual status monitoring
- Conflict resolution (Last-Write-Wins with user notification)
- File versioning and history tracking
- Chunked file transfer for large files
- Content-based deduplication
- End-to-end encryption support
- WebSocket for real-time updates
- JWT-based authentication
- Audit logging for security
- Cross-platform support (Windows, Linux, macOS)

## Quick Start

### Using Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone <repository-url>
cd DistributedSystems
```

2. Start the server services:
```bash
docker-compose up -d
```

3. The server will be available at `http://localhost:8000`

4. Install desktop client dependencies:
```bash
pip install -r requirements.txt
```

5. Launch the desktop application:
```bash
# Desktop GUI (recommended for most users)
python launcher.py gui

# Command Line Interface
python launcher.py cli

# Direct sync client
python launcher.py sync
```

### Manual Setup

#### Server Setup

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Set up PostgreSQL database:
```bash
createdb filesync
```

3. Set environment variables:
```bash
export DATABASE_URL=postgresql://user:password@localhost:5432/filesync
export REDIS_URL=redis://localhost:6379
export RABBITMQ_URL=amqp://user:password@localhost:5672/
export JWT_SECRET=your-secret-key
```

4. Run database migrations:
```bash
cd server
python -c "from database import init_database; init_database('postgresql://user:password@localhost:5432/filesync')"
```

5. Start the server:
```bash
python server/main.py
```

#### Desktop Client Setup

1. Install client dependencies (including GUI support):
```bash
pip install -r requirements.txt

# On Ubuntu/Debian, if tkinter is not available:
sudo apt-get install python3-tk
```

2. Launch the **desktop application** (choose one):
```bash
# Start with desktop GUI (primary interface)
python launcher.py gui

# Or use command line interface  
python launcher.py cli

# Or start background sync client only
python launcher.py sync
```

### Windows Users - Quick Launch

Use the provided PowerShell script:
```powershell
# Setup and launch desktop GUI
.\run.ps1 -GUI

# Or launch CLI
.\run.ps1 -CLI

# Or start full system with Docker
.\run.ps1 -Docker
```

### Manual Usage Examples

```bash
# Start with desktop GUI
python launcher.py gui

# Or use command line interface
python cli.py user register --username myuser --email user@example.com
python cli.py sync start --server http://localhost:8000 --folder ./sync --username myuser
```

## User Interfaces

### Desktop GUI
The primary interface is a cross-platform desktop application built with Tkinter:

- **File Management**: Visual file browser with sync status indicators
- **Real-time Monitoring**: Live updates of sync events and file changes
- **Configuration**: Easy setup of server connection and sync folders
- **Status Dashboard**: Monitor connection status, sync progress, and errors

Launch with: `python launcher.py gui`

### Command Line Interface (CLI)
Full-featured CLI for automation and advanced users:

- **User Management**: Register, login, manage accounts
- **File Operations**: Add, remove, list synchronized files
- **Sync Control**: Start, stop, monitor synchronization
- **Configuration**: Server settings, sync folder management

Launch with: `python launcher.py cli` or `python cli.py`

### Direct Sync Client
Background sync service for headless operation:

- **Automatic Sync**: Continuous file monitoring and synchronization
- **Lightweight**: Minimal resource usage for server deployments
- **Configurable**: Command-line options for all settings

Launch with: `python launcher.py sync`

## API Endpoints

### Authentication
- `POST /auth/register` - Register new user
- `POST /auth/login` - Login user
- `POST /auth/refresh` - Refresh access token
- `POST /auth/logout` - Logout user

### File Operations
- `GET /files/` - List user files
- `POST /files/upload` - Upload file
- `GET /files/{file_id}/download` - Download file
- `DELETE /files/{file_id}` - Delete file
- `GET /files/{file_id}/versions` - Get file versions

### Synchronization
- `GET /sync/changes` - Get pending changes
- `POST /sync/heartbeat` - Update client heartbeat
- `WebSocket /ws/sync/{client_id}` - Real-time updates

## Configuration

### Server Configuration

Environment variables:
- `DATABASE_URL` - PostgreSQL connection string
- `REDIS_URL` - Redis connection string
- `RABBITMQ_URL` - RabbitMQ connection string
- `JWT_SECRET` - JWT signing secret
- `STORAGE_PATH` - File storage directory (default: ./storage)

### Client Configuration

The client can be configured via command line arguments or configuration file:
- `--server` - Server URL
- `--folder` - Local sync folder
- `--username` - Username for authentication
- `--password` - Password for authentication

## Database Schema

### Users Table
- User ID, username, email, password hash
- Storage quota and usage tracking
- Account status and timestamps

### Files Table
- File metadata (name, path, size, checksum)
- Version tracking and sync status
- Conflict resolution status

### File Versions Table
- Version history for each file
- Content checksums and storage paths
- Current version tracking

### Sync Events Table
- File system events (create, modify, delete)
- Client identification and timestamps
- Event processing status

## Security Features

- JWT-based authentication with refresh tokens
- Session management with heartbeat tracking
- End-to-end encryption support (AES-256)
- TLS/HTTPS for all communications
- Audit logging for security events
- Rate limiting and input validation

## Performance Optimizations

- Content-addressed storage for deduplication
- File chunking for large transfers
- Compression for applicable file types
- Database indexing for fast queries
- Connection pooling and caching
- Asynchronous processing with message queues

## Testing

Run tests with pytest:
```bash
pytest tests/
```

## Monitoring

Health check endpoint available at:
```
GET /health
```

## License

MIT License

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request
