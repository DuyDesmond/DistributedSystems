"""Database configuration and connection management."""

import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import StaticPool
from contextlib import contextmanager
from typing import Generator
import redis
import pika
from server.models import Base


class DatabaseManager:
    """Database connection and session management."""
    
    def __init__(self, database_url: str):
        self.database_url = database_url
        self.engine = create_engine(
            database_url,
            pool_pre_ping=True,
            pool_recycle=300,
            echo=os.getenv("DATABASE_DEBUG", "false").lower() == "true"
        )
        self.SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=self.engine)
    
    def create_tables(self):
        """Create all database tables."""
        Base.metadata.create_all(bind=self.engine)
    
    def get_session(self) -> Session:
        """Get a database session."""
        return self.SessionLocal()
    
    @contextmanager
    def session_scope(self) -> Generator[Session, None, None]:
        """Provide a transactional scope around a series of operations."""
        session = self.SessionLocal()
        try:
            yield session
            session.commit()
        except Exception:
            session.rollback()
            raise
        finally:
            session.close()


class RedisManager:
    """Redis connection and operations management."""
    
    def __init__(self, redis_url: str):
        self.redis_url = redis_url
        self.client = redis.from_url(redis_url, decode_responses=True)
    
    def get_client(self) -> redis.Redis:
        """Get Redis client."""
        return self.client
    
    def set_with_expiry(self, key: str, value: str, expiry: int):
        """Set a key with expiry time."""
        self.client.setex(key, expiry, value)
    
    def get(self, key: str) -> str:
        """Get value by key."""
        return self.client.get(key)
    
    def delete(self, key: str):
        """Delete a key."""
        self.client.delete(key)
    
    def publish(self, channel: str, message: str):
        """Publish message to a channel."""
        self.client.publish(channel, message)
    
    def subscribe(self, channel: str):
        """Subscribe to a channel."""
        pubsub = self.client.pubsub()
        pubsub.subscribe(channel)
        return pubsub


class RabbitMQManager:
    """RabbitMQ connection and queue management."""
    
    def __init__(self, rabbitmq_url: str):
        self.rabbitmq_url = rabbitmq_url
        self.connection = None
        self.channel = None
    
    def connect(self):
        """Establish connection to RabbitMQ."""
        self.connection = pika.BlockingConnection(pika.URLParameters(self.rabbitmq_url))
        self.channel = self.connection.channel()
        
        # Declare queues
        self.channel.queue_declare(queue='file_operations', durable=True)
        self.channel.queue_declare(queue='sync_events', durable=True)
        self.channel.queue_declare(queue='notifications', durable=True)
    
    def publish_message(self, queue: str, message: str):
        """Publish message to queue."""
        if not self.channel:
            self.connect()
        
        self.channel.basic_publish(
            exchange='',
            routing_key=queue,
            body=message,
            properties=pika.BasicProperties(
                delivery_mode=2,  # Make message persistent
            )
        )
    
    def consume_messages(self, queue: str, callback):
        """Consume messages from queue."""
        if not self.channel:
            self.connect()
        
        self.channel.basic_consume(
            queue=queue,
            on_message_callback=callback,
            auto_ack=False
        )
        self.channel.start_consuming()
    
    def close(self):
        """Close connection."""
        if self.connection and not self.connection.is_closed:
            self.connection.close()


# Global instances
db_manager = None
redis_manager = None
rabbitmq_manager = None


def init_database(database_url: str):
    """Initialize database manager."""
    global db_manager
    db_manager = DatabaseManager(database_url)
    db_manager.create_tables()
    return db_manager


def init_redis(redis_url: str):
    """Initialize Redis manager."""
    global redis_manager
    redis_manager = RedisManager(redis_url)
    return redis_manager


def init_rabbitmq(rabbitmq_url: str):
    """Initialize RabbitMQ manager."""
    global rabbitmq_manager
    rabbitmq_manager = RabbitMQManager(rabbitmq_url)
    rabbitmq_manager.connect()
    return rabbitmq_manager


def get_db_session():
    """Dependency to get database session."""
    if not db_manager:
        raise RuntimeError("Database not initialized")
    
    session = db_manager.get_session()
    try:
        yield session
    finally:
        session.close()


def get_redis_client():
    """Dependency to get Redis client."""
    if not redis_manager:
        raise RuntimeError("Redis not initialized")
    return redis_manager.get_client()


def get_rabbitmq_channel():
    """Dependency to get RabbitMQ channel."""
    if not rabbitmq_manager:
        raise RuntimeError("RabbitMQ not initialized")
    return rabbitmq_manager.channel
