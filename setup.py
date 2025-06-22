#!/usr/bin/env python3
"""
Database Setup Script
Creates the necessary database tables and initial configuration
"""

import os
import sys
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

# Add current directory to path for imports
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from server.models.file import Base, User, File
from server.core.auth import get_password_hash
from common.constants import POSTGRES_URL

def create_database():
    """Create database and tables"""
    try:
        # Create engine
        engine = create_engine(POSTGRES_URL)
        
        # Create all tables
        Base.metadata.create_all(bind=engine)
        
        print("Database tables created successfully!")
        
        # Create a test user
        SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
        db = SessionLocal()
        
        # Check if test user already exists
        existing_user = db.query(User).filter(User.username == "testuser").first()
        if not existing_user:
            test_user = User(
                username="testuser",
                email="test@example.com",
                hashed_password=get_password_hash("testpass123")
            )
            db.add(test_user)
            db.commit()
            print("Test user created (username: testuser, password: testpass123)")
        else:
            print("Test user already exists")
        
        db.close()
        
    except Exception as e:
        print(f"Error creating database: {e}")
        print("Make sure PostgreSQL is running and the connection string is correct")

def check_dependencies():
    """Check if all required dependencies are installed"""
    required_packages = [
        'fastapi', 'uvicorn', 'sqlalchemy', 'psycopg2', 'redis',
        'python-jose', 'passlib', 'python-multipart', 'watchdog',
        'requests', 'websockets', 'cryptography', 'pydantic'
    ]
    
    missing_packages = []
    
    for package in required_packages:
        try:
            __import__(package.replace('-', '_'))
        except ImportError:
            missing_packages.append(package)
    
    if missing_packages:
        print("Missing packages:")
        for package in missing_packages:
            print(f"  - {package}")
        print("\nInstall missing packages with:")
        print("pip install -r requirements.txt")
        return False
    
    print("All dependencies are installed!")
    return True

if __name__ == "__main__":
    print("File Synchronization System - Database Setup")
    print("=" * 50)
    
    # Check dependencies
    if not check_dependencies():
        sys.exit(1)
    
    # Create database
    create_database()
    
    print("\nSetup completed!")
    print("\nNext steps:")
    print("1. Copy .env.example to .env and update configuration")
    print("2. Start the server: python server/main.py")
    print("3. Start a client: python client/main.py --watch-dir /path/to/folder --username testuser --password testpass123")
