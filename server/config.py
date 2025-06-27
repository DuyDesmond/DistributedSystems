"""Configuration management for the server."""

import os
from pydantic import BaseSettings


class Settings(BaseSettings):
    """Application settings."""
    
    # Database
    database_url: str = "postgresql://filesync:filesync_password@localhost:5432/filesync"
    
    # Redis
    redis_url: str = "redis://localhost:6379"
    
    # RabbitMQ
    rabbitmq_url: str = "amqp://filesync:filesync_password@localhost:5672/"
    
    # Storage
    storage_path: str = "./storage"
    
    # JWT
    jwt_secret: str = "change-this-in-production"
    jwt_algorithm: str = "HS256"
    jwt_expiry_hours: int = 1
    
    # File settings
    max_file_size: int = 100 * 1024 * 1024  # 100MB
    chunk_size: int = 5 * 1024 * 1024  # 5MB
    
    # Rate limiting
    rate_limit_requests: int = 100
    rate_limit_window: int = 3600  # 1 hour
    
    # Sync settings
    heartbeat_timeout: int = 300  # 5 minutes
    sync_batch_size: int = 50
    
    # Security
    password_min_length: int = 8
    session_timeout_hours: int = 24
    
    class Config:
        env_file = ".env"


settings = Settings()
