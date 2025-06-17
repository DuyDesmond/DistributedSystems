# DistributedSystems


Architecture: client-server

Method of synchronization: state-based 

---

Goals:
   - Consistency
   - Availability
   - Partition tolerance

Functions:
   - Authentication & Authorization
   - File Upload & Download
   - Synchronization 
   - Conflict (update collision) Resolution
   - Logging & Monitoring

---

Programming language: Python

Data sharing by:
    - REST API for client-server communication
    - WebSocket for real-time updates
    - File chunking for large file transfers

Network protocol: 
    - HTTPS for REST API
    - WSS (WebSocket Secure) for real-time updates
    - TCP/IP for underlying transport

---

Technical Implementation:

1. Server Components:
    - FastAPI framework for REST endpoints
    - PostgreSQL database for metadata storage
    - Redis for caching and real-time event management
    - Store file locally and metadata in the database
    - JWT (JSON web token) for authentication

    - (Possible service) S3-compatible object storage for file content (large scale only)

2. Client Components:
    - Watchdog for local file system monitoring
    - SQLite for local state management
    - Requests library for HTTP communications
    - WebSocket client for real-time updates

3. Synchronization Process:
   
    a. File Changes Detection:
   
        - Monitor file system events (create, modify, delete)
        - Calculate file checksums (SHA-256)
        - Maintain version vectors for conflict detection
    
    b. Data Transfer:
   
        - Chunk files > 10MB
        - Compress data when beneficial
        - Implement resume-able uploads/downloads
    
    c. Conflict Resolution:
   
        - Use Last-Write-Wins (LWW) as default strategy
        - Keep conflict copies for manual resolution
        - Maintain version history

5. Security Measures:
    - End-to-end encryption for file content
    - TLS for all network communications
    - Rate limiting and request validation
    - Access control lists (ACL)

6. Monitoring & Logging:
    - Structured logging with ELK stack
    - Prometheus metrics for system health
    - Grafana dashboards for visualization
    - Error tracking and alerting

---

Project Stucture:

```plaintext
project/
├── server/
│   ├── main.py
│   ├── api/
│   │   ├── __init__.py
│   │   ├── routes.py
│   │   └── websocket.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── auth.py
│   │   ├── file_manager.py
│   │   └── sync_manager.py
│   ├── models/
│   │   ├── __init__.py
│   │   ├── file.py
│   │   └── user.py
│   └── utils/
│       ├── __init__.py
│       └── encryption.py
├── client/
│   ├── main.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── watcher.py
│   │   └── sync_manager.py
│   ├── storage/
│   │   ├── __init__.py
│   │   └── local_db.py
│   └── utils/
│       ├── __init__.py
│       └── encryption.py
└── common/
    ├── __init__.py
    ├── models.py
    └── constants.py
