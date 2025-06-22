# Distributed File Synchronization System

A robust client-server file synchronization system built with Python, featuring real-time updates, conflict resolution, and secure file transfer.

## Architecture

- **Architecture**: Client-Server
- **Synchronization Method**: State-based
- **Programming Language**: Python 3.11+
- **Database**: PostgreSQL (server), SQLite (client)
- **Communication**: REST API + WebSockets

## Features

### Core Functionality
- ✅ **Authentication & Authorization** - JWT-based user authentication
- ✅ **File Upload & Download** - Secure file transfer with chunking for large files
- ✅ **Real-time Synchronization** - WebSocket-based real-time updates
- ✅ **Conflict Resolution** - Last-Write-Wins (LWW) strategy with version vectors
- ✅ **File System Monitoring** - Automatic detection of local file changes
- ✅ **End-to-end Encryption** - File content encryption for security

### Security Features
- JWT token-based authentication
- End-to-end file encryption
- TLS/HTTPS for all communications
- Access control lists (ACL)
- Rate limiting and request validation

### CAP Theorem Considerations
- **Consistency**: Ensured through version vectors and conflict resolution
- **Availability**: Server remains available during network partitions
- **Partition Tolerance**: Clients can work offline and sync when reconnected

## Project Structure

```
project/
├── server/                 # Server-side components
│   ├── main.py            # FastAPI server entry point
│   ├── api/               # API endpoints
│   ├── core/              # Core business logic
│   ├── models/            # Database models
│   └── utils/             # Utilities and encryption
├── client/                # Client-side components
│   ├── main.py            # Client entry point
│   ├── core/              # Client core logic
│   ├── storage/           # Local SQLite database
│   └── utils/             # Client utilities
├── common/                # Shared components
│   ├── models.py          # Pydantic models
│   └── constants.py       # Application constants
└── requirements.txt       # Python dependencies
```

## Installation & Setup

### Prerequisites
- Python 3.11 or higher
- PostgreSQL database
- Redis server (optional, for advanced features)

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Database Setup
```bash
# Install PostgreSQL and create a database
createdb filesync

# Run setup script to create tables and test user
python setup.py
```

### 3. Configuration
```bash
# Copy environment template
cp .env.example .env

# Edit .env with your configuration
# Update database connection strings, secret keys, etc.
```

### 4. Start the Server
```bash
python server/main.py
```
Server will be available at `http://localhost:8000`

### 5. Start a Client
```bash
python client/main.py --watch-dir /path/to/sync/folder --username testuser --password testpass123
```

## Usage

### Server API Endpoints

#### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - User login

#### File Operations
- `POST /api/v1/files/upload` - Upload file
- `GET /api/v1/files/download/{file_id}` - Download file
- `GET /api/v1/files/list` - List user files

#### Synchronization
- `POST /api/v1/sync/event` - Process sync event
- `GET /api/v1/sync/changes` - Get changes since timestamp

### Client Usage

#### Basic Synchronization
The client automatically:
1. Monitors the specified directory for changes
2. Uploads new/modified files to the server
3. Downloads updates from other clients
4. Resolves conflicts using Last-Write-Wins strategy

#### Command Line Options
```bash
python client/main.py \
  --watch-dir /path/to/folder \
  --server-url http://localhost:8000 \
  --username your_username \
  --password your_password
```

## Technical Implementation

### Synchronization Process

1. **File Change Detection**
   - Watchdog monitors local file system
   - Calculates SHA-256 checksums
   - Maintains version vectors for conflict detection

2. **Data Transfer**
   - Files > 10MB are chunked
   - Resume-able uploads/downloads
   - Data compression when beneficial

3. **Conflict Resolution**
   - Last-Write-Wins (LWW) as default
   - Version history maintenance
   - Conflict copies for manual resolution

### Security Measures

- **File Encryption**: End-to-end encryption using Fernet (AES)
- **Network Security**: TLS for all communications
- **Authentication**: JWT tokens with configurable expiration
- **Access Control**: User-based file ownership

### Database Schema

#### Users Table
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE,
    email VARCHAR(255),
    hashed_password VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### Files Table
```sql
CREATE TABLE files (
    id UUID PRIMARY KEY,
    path VARCHAR(1024),
    checksum VARCHAR(64),
    size INTEGER,
    owner_id UUID REFERENCES users(id),
    version_vector JSONB,
    modified_at TIMESTAMP,
    is_deleted BOOLEAN DEFAULT FALSE
);
```

## Testing

### Run System Tests
```bash
python test_system.py
```

### Manual Testing
1. Start the server
2. Start multiple clients with different watch directories
3. Create, modify, and delete files in watched directories
4. Verify synchronization across all clients

## Configuration Options

### Environment Variables
- `POSTGRES_URL` - PostgreSQL connection string
- `SECRET_KEY` - JWT signing secret
- `SERVER_HOST` - Server bind address
- `SERVER_PORT` - Server port
- `FILES_DIRECTORY` - Server file storage location
- `CHUNK_SIZE` - File chunk size for large file transfers
- `SYNC_INTERVAL` - Client sync interval in seconds

### Performance Tuning
- Adjust `CHUNK_SIZE` for network conditions
- Modify `SYNC_INTERVAL` for sync frequency
- Configure PostgreSQL for optimal performance
- Use Redis for caching in high-load scenarios

## Monitoring & Logging

### Server Monitoring
- Health check endpoint: `GET /health`
- Structured logging to console
- Performance metrics collection ready

### Client Monitoring
- Console output for sync events
- Local SQLite database for sync state
- Error reporting and retry mechanisms

## Troubleshooting

### Common Issues

1. **Authentication Failed**
   - Verify username/password
   - Check server is running
   - Ensure network connectivity

2. **Files Not Syncing**
   - Check file permissions
   - Verify watch directory path
   - Check server logs for errors

3. **Database Connection Error**
   - Ensure PostgreSQL is running
   - Verify connection string in .env
   - Check database user permissions

### Debug Mode
Start components with additional logging:
```bash

# Server with debug logging
python server/main.py --log-level debug

# Client with verbose output
python client/main.py --verbose --watch-dir /path
```
