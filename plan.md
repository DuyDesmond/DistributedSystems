File synchroniser (like DropBox)

Architecture: client-server

Method of synchronization: hybrid (state-based + event-driven)

Goals (CAP Theorem Priority):
    Primary: Availability + Partition tolerance
    Secondary: Eventual Consistency
    Trade-off: Strong consistency sacrificed for better availability during network partitions
    
System Qualities:
    - High scalability (support millions of users)
    - Fault tolerance (graceful degradation)
    - Low latency file operations

Functions:
    Authentication 
    File Upload & Download & Update & Revert
    Synchronization 
    Conflict (update collision) Resolution
    Logging & Monitoring

Programming language: Python 3.13.5

Data sharing by:
    - REST API for client-server communication
    - WebSocket for real-time updates
    - File chunking for large file transfers

Network protocol: 
    - HTTPS for REST API
    - WSS (WebSocket Secure) for real-time updates
    - TCP/IP for underlying transport

Technical Implementation:

1. Server Components:
    - FastAPI framework for REST endpoints
    - Single PostgreSQL database with separate schemas for metadata and user accounts
    - Redis for caching and real-time event management
    - Local file system storage with organized directory structure
    - Message queue (RabbitMQ) for async processing and job scheduling
    - Load balancer (Nginx) for horizontal scaling
    - Docker containers for microservice deployment

2. Client Components:
    - Watchdog for local file system monitoring
    - SQLite for local state management
    - Requests library for HTTP communications
    - WebSocket client for real-time updates

3. Synchronization Process:
    a. File Changes Detection:
        - Monitor file system events (create, modify, delete)
        - Calculate file checksums (SHA-256) + file size for quick comparison
        - Use timestamps with client IDs for version tracking
        - Implement heartbeat mechanism for client liveness
    
    b. Data Transfer:
        - Chunk files > 5MB (smaller threshold for better UX)
        - Use content-based deduplication
        - Implement delta sync for modified files
        - Compress data when beneficial (configurable)
        - Implement resume-able uploads/downloads
        - Add bandwidth throttling controls

    c. Conflict Resolution:
        - Detect conflicts using timestamp + client ID comparison
        - Give the user 2 options: Automatic, Manual
        - Automatic uses Last-Write-Wins (LWW) with user notification
        - Keep conflict copies with clear naming convention for manual conflic resolution
        - Maintain version history with configurable retention period

4. Security Measures:
    - End-to-end encryption for file content (AES-256)
    - Client-side encryption keys derived from user password
    - TLS 1.3 for all network communications
    - JWT tokens for authentication with refresh mechanism
    - Rate limiting and request validation
    - Access control lists (ACL) with role-based permissions
    - Audit logging for security events

5. Performance & Scalability:
    - Implement connection pooling
    - Use CDN for file distribution
    - Add caching layers (Redis)
    - Database indexing strategy for metadata queries
    - Horizontal scaling with consistent hashing
    - Background sync prioritization (recent files first)

6. Error Handling & Recovery:
    - Exponential backoff for failed operations
    - Circuit breaker pattern for external dependencies
    - Graceful degradation during partial outages
    - Automatic retry mechanisms with jitter
    - Client-side offline mode with sync queue

6.1. Local File Storage Strategy:
    - Organized directory structure: `/storage/{user_id}/{year}/{month}/{file_id}`
    - File deduplication using content-addressed storage (hash-based naming)
    - Regular cleanup of orphaned files and old versions
    - Disk space monitoring and alerts
    - Automated backup strategy for file storage
    - File system permissions and access control
    - Storage quota enforcement per user

7. Data Models:
    
    Users Table:
    - user_id (Primary Key)
    - username, email, password_hash
    - created_at, last_login
    - storage_quota, used_storage
    - account_status (active/suspended)
    
    Files Table:
    - file_id (Primary Key)
    - user_id (Foreign Key)
    - file_path, file_name
    - file_size, checksum (SHA-256)
    - version_number, created_at, modified_at
    - sync_status, conflict_status
    
    File_Versions Table:
    - version_id (Primary Key)
    - file_id (Foreign Key)
    - version_number, checksum
    - storage_path, created_at
    - is_current_version
    
    Sync_Events Table:
    - event_id (Primary Key)
    - user_id, file_id
    - event_type (create/modify/delete)
    - timestamp, client_id
    - sync_status (pending/completed/failed)

8. API Endpoints:
    
    Authentication:
    - POST /auth/login
    - POST /auth/logout
    - POST /auth/refresh
    - POST /auth/register
    
    File Operations:
    - GET /files/ (list user files)
    - POST /files/upload
    - GET /files/{file_id}/download
    - PUT /files/{file_id}
    - DELETE /files/{file_id}
    - GET /files/{file_id}/versions
    
    Synchronization:
    - GET /sync/changes (get pending changes)
    - POST /sync/heartbeat
    - WebSocket /ws/sync (real-time updates)

9. Deployment Strategy:
    - Containerized microservices with Docker
    - Kubernetes orchestration for production
    - CI/CD pipeline with automated testing
    - Blue-green deployment for zero downtime
    - Infrastructure as Code (Terraform)
    - Multi-region deployment for global availability
    
10. Monitoring & Observability:
    - Prometheus metrics collection
    - Health checks and service discovery
    - Error tracking 

11. Testing Strategy:
    - Unit tests (pytest) - 80% coverage minimum
    - Integration tests for API endpoints
    - End-to-end tests for sync workflows
    - Load testing with realistic file sizes
    - Chaos engineering for failure scenarios
    - Security testing (OWASP guidelines)

12. Implementation Details:

    a. File Storage Implementation:
    - Directory structure: /storage/content/{hash[0:2]}/{hash[2:4]}/{full_hash}
    - Metadata storage: /storage/metadata/{user_id}/{file_path}.json
    - Chunk storage for large files: /storage/chunks/{chunk_hash}
    - Temporary upload directory: /storage/temp/{upload_id}/
    
    b. Synchronization Algorithm:
    - Use Merkle trees for efficient directory comparison
    - Implement vector clocks for conflict detection
    - Batch operations for better performance
    - Priority queue for sync operations (user files > shared files > old files)
    
    c. Database Schema Details:
    ```sql
    -- Users table with indexes
    CREATE INDEX idx_users_email ON users(email);
    CREATE INDEX idx_users_username ON users(username);
    
    -- Files table with composite indexes
    CREATE INDEX idx_files_user_path ON files(user_id, file_path);
    CREATE INDEX idx_files_checksum ON files(checksum);
    CREATE INDEX idx_files_modified ON files(modified_at);
    
    -- Sync events with time-based partitioning
    CREATE INDEX idx_sync_events_user_time ON sync_events(user_id, timestamp);
    ```
    
    d. Configuration Management:
    - Environment-specific configs (dev/staging/prod)
    - Feature flags for gradual rollouts
    - Rate limiting configurations
    - Storage quotas and limits
    - Encryption key management
    
    e. Error Codes and Messages:
    - Standardized error response format
    - Client-side error handling mapping
    - Localization support for error messages
    - Retry strategies for different error types

13. Development Environment Setup:
    - Docker Compose for local development
    - Seed data for testing
    - Development proxy configuration
    - Hot reload setup for client development

14. Client Implementation Specifics:
    - File system watcher configuration per OS
    - Local database schema (SQLite)
    - Sync state machine implementation
    - UI for conflict resolution
    - Offline mode data structures


16. Security Implementation Details:
    - Key derivation functions (PBKDF2/Argon2)
    - Session management and timeout policies
    - API rate limiting rules (requests per minute/hour)
    - Input validation schemas
    - CORS policy configuration
    - Content Security Policy headers

