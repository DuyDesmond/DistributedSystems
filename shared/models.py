"""Shared data models and utilities used by both client and server."""

from datetime import datetime
from enum import Enum
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field
import hashlib
import os


class SyncStatus(str, Enum):
    """Synchronization status enumeration."""
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"
    CONFLICT = "conflict"


class EventType(str, Enum):
    """File system event types."""
    CREATE = "create"
    MODIFY = "modify"
    DELETE = "delete"
    MOVE = "move"


class ConflictStatus(str, Enum):
    """File conflict status."""
    NO_CONFLICT = "no_conflict"
    CONFLICT_DETECTED = "conflict_detected"
    CONFLICT_RESOLVED = "conflict_resolved"


class AccountStatus(str, Enum):
    """User account status."""
    ACTIVE = "active"
    SUSPENDED = "suspended"
    PENDING = "pending"


class FileInfo(BaseModel):
    """File information model."""
    file_id: Optional[str] = None
    user_id: str
    file_path: str
    file_name: str
    file_size: int
    checksum: str
    version_number: int = 1
    created_at: datetime
    modified_at: datetime
    sync_status: SyncStatus = SyncStatus.PENDING
    conflict_status: ConflictStatus = ConflictStatus.NO_CONFLICT
    is_deleted: bool = False


class FileVersion(BaseModel):
    """File version model."""
    version_id: str
    file_id: str
    version_number: int
    checksum: str
    storage_path: str
    created_at: datetime
    is_current_version: bool = False


class SyncEvent(BaseModel):
    """Synchronization event model."""
    event_id: Optional[str] = None
    user_id: str
    file_id: Optional[str] = None
    event_type: EventType
    timestamp: datetime
    client_id: str
    sync_status: SyncStatus = SyncStatus.PENDING
    metadata: Optional[Dict[str, Any]] = None


class User(BaseModel):
    """User model."""
    user_id: Optional[str] = None
    username: str
    email: str
    password_hash: Optional[str] = None
    created_at: Optional[datetime] = None
    last_login: Optional[datetime] = None
    storage_quota: int = 5 * 1024 * 1024 * 1024  # 5GB default
    used_storage: int = 0
    account_status: AccountStatus = AccountStatus.ACTIVE


class AuthToken(BaseModel):
    """Authentication token model."""
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    refresh_token: Optional[str] = None


class APIResponse(BaseModel):
    """Standard API response model."""
    success: bool
    message: Optional[str] = None
    data: Optional[Any] = None
    error: Optional[str] = None


def calculate_file_checksum(file_path: str) -> str:
    """Calculate SHA-256 checksum of a file."""
    sha256_hash = hashlib.sha256()
    try:
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                sha256_hash.update(chunk)
        return sha256_hash.hexdigest()
    except Exception:
        return ""


def get_file_size(file_path: str) -> int:
    """Get file size in bytes."""
    try:
        return os.path.getsize(file_path)
    except Exception:
        return 0


def normalize_path(path: str) -> str:
    """Normalize file path for consistent storage."""
    return os.path.normpath(path).replace("\\", "/")


class FileChunk(BaseModel):
    """File chunk model for large file transfers."""
    chunk_id: str
    file_id: str
    chunk_number: int
    total_chunks: int
    data: bytes
    checksum: str


class ConflictResolution(BaseModel):
    """Conflict resolution configuration."""
    strategy: str  # "auto_lww", "manual", "both_versions"
    user_choice: Optional[str] = None  # "local", "remote", "both"


class ClientConfig(BaseModel):
    """Client configuration model."""
    server_url: str
    sync_folder: str
    chunk_size: int = 5 * 1024 * 1024  # 5MB
    max_retries: int = 3
    retry_delay: int = 5
    heartbeat_interval: int = 30
    conflict_resolution: ConflictResolution = ConflictResolution(strategy="auto_lww")


class ServerConfig(BaseModel):
    """Server configuration model."""
    database_url: str
    redis_url: str
    rabbitmq_url: str
    storage_path: str = "./storage"
    jwt_secret: str
    jwt_expiry: int = 3600  # 1 hour
    max_file_size: int = 100 * 1024 * 1024  # 100MB
    chunk_size: int = 5 * 1024 * 1024  # 5MB
