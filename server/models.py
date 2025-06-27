"""Database models using SQLAlchemy."""

from datetime import datetime
from sqlalchemy import Column, String, Integer, DateTime, Boolean, ForeignKey, BigInteger, Text, Index
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship
from sqlalchemy.dialects.postgresql import UUID
import uuid

Base = declarative_base()


class User(Base):
    """User table model."""
    __tablename__ = "users"
    
    user_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    username = Column(String(100), unique=True, nullable=False, index=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    last_login = Column(DateTime)
    storage_quota = Column(BigInteger, default=5*1024*1024*1024, nullable=False)  # 5GB
    used_storage = Column(BigInteger, default=0, nullable=False)
    account_status = Column(String(20), default="active", nullable=False)
    
    # Relationships
    files = relationship("File", back_populates="user", cascade="all, delete-orphan")
    sync_events = relationship("SyncEvent", back_populates="user", cascade="all, delete-orphan")


class File(Base):
    """File table model."""
    __tablename__ = "files"
    
    file_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.user_id"), nullable=False, index=True)
    file_path = Column(String(500), nullable=False)
    file_name = Column(String(255), nullable=False)
    file_size = Column(BigInteger, nullable=False)
    checksum = Column(String(64), nullable=False, index=True)
    version_number = Column(Integer, default=1, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    modified_at = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    sync_status = Column(String(20), default="pending", nullable=False)
    conflict_status = Column(String(30), default="no_conflict", nullable=False)
    is_deleted = Column(Boolean, default=False, nullable=False)
    storage_path = Column(String(500))
    
    # Relationships
    user = relationship("User", back_populates="files")
    versions = relationship("FileVersion", back_populates="file", cascade="all, delete-orphan")
    sync_events = relationship("SyncEvent", back_populates="file", cascade="all, delete-orphan")
    
    # Composite indexes
    __table_args__ = (
        Index('idx_files_user_path', 'user_id', 'file_path'),
        Index('idx_files_user_status', 'user_id', 'sync_status'),
        Index('idx_files_checksum_size', 'checksum', 'file_size'),
    )


class FileVersion(Base):
    """File version table model."""
    __tablename__ = "file_versions"
    
    version_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    file_id = Column(UUID(as_uuid=True), ForeignKey("files.file_id"), nullable=False, index=True)
    version_number = Column(Integer, nullable=False)
    checksum = Column(String(64), nullable=False)
    storage_path = Column(String(500), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    is_current_version = Column(Boolean, default=False, nullable=False)
    file_size = Column(BigInteger, nullable=False)
    
    # Relationships
    file = relationship("File", back_populates="versions")
    
    # Composite indexes
    __table_args__ = (
        Index('idx_versions_file_number', 'file_id', 'version_number'),
        Index('idx_versions_current', 'file_id', 'is_current_version'),
    )


class SyncEvent(Base):
    """Sync event table model."""
    __tablename__ = "sync_events"
    
    event_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.user_id"), nullable=False, index=True)
    file_id = Column(UUID(as_uuid=True), ForeignKey("files.file_id"), nullable=True, index=True)
    event_type = Column(String(20), nullable=False)
    timestamp = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    client_id = Column(String(50), nullable=False)
    sync_status = Column(String(20), default="pending", nullable=False)
    metadata = Column(Text)  # JSON metadata
    
    # Relationships
    user = relationship("User", back_populates="sync_events")
    file = relationship("File", back_populates="sync_events")
    
    # Composite indexes
    __table_args__ = (
        Index('idx_sync_events_user_time', 'user_id', 'timestamp'),
        Index('idx_sync_events_status', 'sync_status', 'timestamp'),
    )


class Session(Base):
    """User session table model."""
    __tablename__ = "sessions"
    
    session_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.user_id"), nullable=False, index=True)
    client_id = Column(String(50), nullable=False)
    access_token = Column(String(500), nullable=False)
    refresh_token = Column(String(500), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    last_heartbeat = Column(DateTime, default=datetime.utcnow)
    
    # Relationships
    user = relationship("User")
    
    # Indexes
    __table_args__ = (
        Index('idx_sessions_token', 'access_token'),
        Index('idx_sessions_user_active', 'user_id', 'is_active'),
    )


class FileChunk(Base):
    """File chunk table model for large file uploads."""
    __tablename__ = "file_chunks"
    
    chunk_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    upload_id = Column(String(100), nullable=False, index=True)
    file_id = Column(UUID(as_uuid=True), ForeignKey("files.file_id"), nullable=True)
    chunk_number = Column(Integer, nullable=False)
    total_chunks = Column(Integer, nullable=False)
    chunk_size = Column(Integer, nullable=False)
    checksum = Column(String(64), nullable=False)
    storage_path = Column(String(500), nullable=False)
    uploaded_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    
    # Composite indexes
    __table_args__ = (
        Index('idx_chunks_upload', 'upload_id', 'chunk_number'),
    )


class AuditLog(Base):
    """Audit log table for security events."""
    __tablename__ = "audit_logs"
    
    log_id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.user_id"), nullable=True, index=True)
    action = Column(String(100), nullable=False)
    resource_type = Column(String(50))
    resource_id = Column(String(100))
    ip_address = Column(String(45))
    user_agent = Column(Text)
    timestamp = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    success = Column(Boolean, nullable=False)
    details = Column(Text)
    
    # Relationships
    user = relationship("User")
    
    # Indexes
    __table_args__ = (
        Index('idx_audit_action_time', 'action', 'timestamp'),
        Index('idx_audit_user_time', 'user_id', 'timestamp'),
    )
