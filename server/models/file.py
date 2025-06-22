from sqlalchemy import Column, String, DateTime, Boolean, Integer, JSON, LargeBinary
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.sql import func
import uuid

Base = declarative_base()

class User(Base):
    __tablename__ = "users"
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    username = Column(String(255), unique=True, nullable=False)
    email = Column(String(255), unique=True, nullable=True)
    hashed_password = Column(String(255), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    is_active = Column(Boolean, default=True)

class File(Base):
    __tablename__ = "files"
    
    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    path = Column(String(1024), nullable=False)
    checksum = Column(String(64), nullable=False)
    size = Column(Integer, nullable=False)
    owner_id = Column(String, nullable=False)
    version_vector = Column(JSON, default={})
    modified_at = Column(DateTime(timezone=True), server_default=func.now())
    is_deleted = Column(Boolean, default=False)
    content_path = Column(String(1024), nullable=True)  # Path to actual file content
