**File Synchronizer (like Dropbox)**

**Architecture:** client-server

**Method of synchronization:** hybrid (state-based + event-driven)

**Goals (CAP Theorem Priority):**

* Primary: Availability + Partition tolerance
* Secondary: Eventual Consistency
* Trade-off: Strong consistency sacrificed for better availability during network partitions

**System Qualities:**

* High scalability
* Fault tolerance
* Low latency file operations

**Functions:**

* Authentication
* File Upload, Download, Update, Roll-back
* Synchronization
* Conflict Detection and Resolution (using version vectors)
* Logging & Monitoring of files and folders

**Programming Language:** Java

**Data Sharing by:**

* REST API for client-server communication
* WebSocket for real-time updates
* File chunking for large file transfers

**Network Protocol:**

* HTTPS for REST API
* WSS (WebSocket Secure) for real-time updates
* TCP/IP for underlying transport

---

### Technical Implementation

**1. Server Components:**

* Spring Boot framework for REST endpoints
* PostgreSQL with JPA + Hibernate for metadata and user accounts (separate schemas)
* Redis for caching and real-time event management (Jedis or Lettuce)
* Java NIO for local file system storage
* RabbitMQ with Spring AMQP for asynchronous jobs
* Nginx as load balancer
* Docker containers for microservice deployment
* Spring WebSocket + STOMP for event delivery
* **Server stores version vectors per file for authoritative conflict detection and merging**

**2. Client Components:**

* `WatchService` for local file system monitoring
* SQLite with Xerial JDBC for local state/version tracking
* Apache HttpClient for HTTP communication
* `java.net.http.WebSocket` or Tyrus client for real-time sync
* **Client stores version vectors for each file locally to support offline sync and conflict detection**

**3. Synchronization Process:**

**a. File Changes Detection:**

* Monitor create, modify, delete, rollback using WatchService
* Calculate SHA-256 checksum + file size
* Maintain version vector per file (stored locally and server-side)
* Use heartbeats to track client liveness

**b. Data Transfer:**

* Chunk files > 5MB using Java NIO
* Deduplication using hash comparison
* Delta sync with binary diff/partial transfer
* Optional GZIP compression
* Resume-able transfers via byte-range headers
* Stream wrappers for bandwidth throttling

**c. Conflict Resolution:**

* Detect using version vectors (`{clientA: 3, clientB: 1}`)
* If concurrent updates detected (v1 || v2): trigger conflict
* Resolution:

  * Automatic: Last-Write-Wins (LWW) with notification
  * Manual: Conflict copies kept, user selects resolution
* File version history supports manual rollback
* **Server and client both compare version vectors during sync**

---

**4. Security Measures:**

* AES-256 file encryption via JCE
* Key derivation (PBKDF2 or Argon2) client-side
* TLS 1.3 for transport
* JWT-based authentication (with refresh)
* Spring Security for access control
* Rate limiting via Spring Gateway or custom filters
* Audit logging

**5. Performance & Scalability:**

* HikariCP for database pooling
* CDN for static file distribution
* Redis for event/metadata caching
* Indexing via JPA + native SQL
* Consistent hashing for user partitioning
* Priority sync for recently changed files

**6. Error Handling & Recovery:**

* Exponential backoff with Spring Retry
* Circuit breakers via Resilience4j
* Graceful degradation
* Retry with jitter
* Client-side offline mode with local sync queue

**6.1 Local File Storage Strategy:**

* Structure: `/storage/{user_id}/{year}/{month}/{file_id}`
* Content-addressed storage with hash-based deduplication
* Cleanup of orphans and old versions
* Disk space monitoring and alerting
* ACLs and quota enforcement
* Encrypted storage with versioning

---

### Data Models

**Users Table:**

* user\_id (PK)
* username, email, password\_hash
* created\_at, last\_login
* storage\_quota, used\_storage
* account\_status

**Files Table:**

* file\_id (PK)
* user\_id (FK)
* file\_path, file\_name
* file\_size, checksum
* current\_version\_vector (JSONB) -- stores latest known vector clock
* created\_at, modified\_at
* sync\_status, conflict\_status

**File\_Versions Table:**

* version\_id (PK)
* file\_id (FK)
* version\_number, checksum
* storage\_path
* created\_at
* is\_current\_version
* version\_vector (JSONB) -- stores snapshot of vector at the time of save

**Sync\_Events Table:**

* event\_id (PK)
* user\_id, file\_id
* event\_type (create/modify/delete)
* timestamp, client\_id
* sync\_status

**Client SQLite Table (file\_version\_vector):**

```sql
CREATE TABLE file_version_vector (
    file_id TEXT PRIMARY KEY,
    file_path TEXT,
    version_vector TEXT, -- JSON string: {"clientA":3,"clientB":2}
    last_modified TIMESTAMP
);
```

---

### API Endpoints

**Authentication:**

* POST /auth/login
* POST /auth/logout
* POST /auth/refresh
* POST /auth/register

**File Operations:**

* GET /files/
* POST /files/upload
* GET /files/{file\_id}/download
* PUT /files/{file\_id}
* DELETE /files/{file\_id}
* GET /files/{file\_id}/versions

**Synchronization:**

* GET /sync/changes
* POST /sync/heartbeat
* WebSocket /ws/sync

---

### Deployment Strategy

* Dockerized services
* Kubernetes orchestration
* CI/CD pipeline (GitHub Actions or Jenkins)
* Blue-green deployment
* Terraform for IaC
* Multi-region deployment for global access

---

### Monitoring & Observability

* Prometheus + Grafana
* Spring Boot Actuator health checks
* Centralized logs (ELK Stack)
* Error tracking with alerting

---

### Testing Strategy

* Unit tests with JUnit + Mockito
* Integration tests (Spring Boot Test)
* End-to-end sync flow tests
* Load testing (JMeter/Gatling)
* Chaos testing
* OWASP-based security testing

---

### Implementation Details

**a. File Storage:**

* `/storage/content/{hash[0:2]}/{hash[2:4]}/{full_hash}`
* `/storage/metadata/{user_id}/{file_path}.json`
* `/storage/chunks/{chunk_hash}`
* `/storage/temp/{upload_id}/`

**b. Sync Algorithm:**

* Merkle tree-based comparison
* Version vectors per file (stored server-side and client-side)
* Vector comparison: `<`, `>`, `||` to detect conflicts
* Priority queue for sync jobs

**c. Schema Enhancements:**

```sql
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_files_user_path ON files(user_id, file_path);
CREATE INDEX idx_files_checksum ON files(checksum);
CREATE INDEX idx_sync_events_user_time ON sync_events(user_id, timestamp);
```

**d. Config Management:**

* Profiles per environment (dev/staging/prod)
* Feature flags
* Rate limit configs
* Quotas
* Key management

**e. Error Handling:**

* Unified error schema
* Localized messages
* Retry policies per error type

---

### Development Setup

* Docker Compose for PostgreSQL, Redis, RabbitMQ
* Dev proxy config
* Seed data generation
* Spring Boot DevTools for hot reload

---

### Client Specifics

* `WatchService` for file change tracking
* SQLite DB with version vector table
* Conflict resolver UI (Swing/JavaFX)
* Background sync thread
* Offline support

---

### Security Details

* Key derivation with SecretKeyFactory
* JWT with `io.jsonwebtoken`
* Session timeout + refresh
* Hibernate Validator for input
* Secure CORS + CSP headers
