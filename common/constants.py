# Application Constants
CHUNK_SIZE = 10 * 1024 * 1024  # 10MB
MAX_FILE_SIZE = 100 * 1024 * 1024  # 100MB
SUPPORTED_FILE_TYPES = ['.txt', '.pdf', '.doc', '.docx', '.jpg', '.png', '.mp4']

# Security Constants
SECRET_KEY = "your-secret-key-change-in-production"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30

# Database Constants
POSTGRES_URL = "postgresql://user:password@localhost/filesync"
REDIS_URL = "redis://localhost:6379"

# Server Configuration
SERVER_HOST = "0.0.0.0"
SERVER_PORT = 8000
WEBSOCKET_PORT = 8001

# File Storage
FILES_DIRECTORY = "server_files"
TEMP_DIRECTORY = "temp_files"

# Sync Configuration
SYNC_INTERVAL = 30  # seconds
MAX_RETRY_ATTEMPTS = 3
CONFLICT_RESOLUTION_STRATEGY = "lww"  # last-write-wins
