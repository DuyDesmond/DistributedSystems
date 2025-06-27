#!/usr/bin/env python3
"""Setup script for the File Synchronization System."""

import os
import sys
import subprocess
import argparse
from pathlib import Path


def run_command(command, cwd=None):
    """Run a command and return success status."""
    try:
        result = subprocess.run(command, shell=True, cwd=cwd, check=True, capture_output=True, text=True)
        print(f"✓ {command}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"✗ {command}")
        print(f"Error: {e.stderr}")
        return False


def setup_environment():
    """Set up the development environment."""
    print("Setting up File Synchronization System...")
    
    # Create necessary directories
    directories = [
        "storage",
        "storage/content",
        "storage/chunks",
        "storage/temp",
        "storage/metadata",
        "logs"
    ]
    
    for directory in directories:
        Path(directory).mkdir(parents=True, exist_ok=True)
        print(f"✓ Created directory: {directory}")
    
    # Install Python dependencies
    print("\nInstalling Python dependencies...")
    success = run_command("pip install -r requirements.txt")
    if not success:
        print("Failed to install dependencies")
        return False
    
    return True


def setup_database():
    """Set up the database."""
    print("\nSetting up database...")
    
    # Check if PostgreSQL is running
    try:
        result = subprocess.run(
            "pg_isready -h localhost -p 5432", 
            shell=True, 
            capture_output=True, 
            text=True
        )
        if result.returncode != 0:
            print("PostgreSQL is not running. Please start PostgreSQL first.")
            return False
    except FileNotFoundError:
        print("PostgreSQL client not found. Please install PostgreSQL.")
        return False
    
    # Create database if it doesn't exist
    commands = [
        "createdb filesync 2>/dev/null || true",
        "psql -d filesync -c 'CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";'"
    ]
    
    for command in commands:
        run_command(command)
    
    print("✓ Database setup complete")
    return True


def start_services():
    """Start required services with Docker Compose."""
    print("\nStarting services with Docker Compose...")
    
    if not Path("docker-compose.yml").exists():
        print("docker-compose.yml not found")
        return False
    
    success = run_command("docker-compose up -d postgres redis rabbitmq")
    if success:
        print("✓ Services started successfully")
        print("Waiting for services to be ready...")
        import time
        time.sleep(10)  # Wait for services to start
    
    return success


def run_tests():
    """Run the test suite."""
    print("\nRunning tests...")
    success = run_command("python -m pytest tests/ -v")
    return success


def create_sample_user():
    """Create a sample user for testing."""
    print("\nCreating sample user...")
    
    # This would need the server to be running
    # For now, just print instructions
    print("To create a sample user, run the following after starting the server:")
    print("curl -X POST http://localhost:8000/auth/register \\")
    print("  -H 'Content-Type: application/json' \\")
    print("  -d '{\"username\": \"demo\", \"email\": \"demo@example.com\", \"password\": \"demopassword123\"}'")


def main():
    """Main setup function."""
    parser = argparse.ArgumentParser(description="Setup File Synchronization System")
    parser.add_argument("--skip-db", action="store_true", help="Skip database setup")
    parser.add_argument("--skip-deps", action="store_true", help="Skip dependency installation")
    parser.add_argument("--docker", action="store_true", help="Use Docker for services")
    parser.add_argument("--test", action="store_true", help="Run tests after setup")
    
    args = parser.parse_args()
    
    print("=" * 60)
    print("File Synchronization System Setup")
    print("=" * 60)
    
    # Setup environment
    if not args.skip_deps:
        if not setup_environment():
            print("Environment setup failed")
            sys.exit(1)
    
    # Setup services
    if args.docker:
        if not start_services():
            print("Failed to start services with Docker")
            sys.exit(1)
    elif not args.skip_db:
        if not setup_database():
            print("Database setup failed")
            sys.exit(1)
    
    # Run tests
    if args.test:
        if not run_tests():
            print("Tests failed")
            sys.exit(1)
    
    # Create sample user
    create_sample_user()
    
    print("\n" + "=" * 60)
    print("Setup completed successfully!")
    print("=" * 60)
    
    print("\nNext steps:")
    print("1. Start the server: python server/main.py")
    print("2. Start a client: python client/sync_client.py --username demo --password demopassword123")
    print("3. Access the API documentation at: http://localhost:8000/docs")
    
    if args.docker:
        print("\nDocker services are running. To stop them:")
        print("docker-compose down")


if __name__ == "__main__":
    main()
