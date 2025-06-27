"""Test suite for the file synchronization system."""

import pytest
import tempfile
import os
import shutil
from datetime import datetime
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from fastapi.testclient import TestClient

from server.main import app
from server.models import Base
from server.database import get_db_session
from shared.models import User, FileInfo


# Test database setup
@pytest.fixture(scope="session")
def test_db():
    """Create test database."""
    engine = create_engine("sqlite:///./test.db", connect_args={"check_same_thread": False})
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    
    Base.metadata.create_all(bind=engine)
    
    def override_get_db():
        try:
            db = TestingSessionLocal()
            yield db
        finally:
            db.close()
    
    app.dependency_overrides[get_db_session] = override_get_db
    
    yield TestingSessionLocal
    
    # Cleanup
    os.remove("./test.db")


@pytest.fixture
def client(test_db):
    """Create test client."""
    return TestClient(app)


@pytest.fixture
def temp_dir():
    """Create temporary directory for testing."""
    temp_dir = tempfile.mkdtemp()
    yield temp_dir
    shutil.rmtree(temp_dir)


class TestAuthentication:
    """Test authentication endpoints."""
    
    def test_register_user(self, client):
        """Test user registration."""
        response = client.post(
            "/auth/register",
            json={
                "username": "testuser",
                "email": "test@example.com",
                "password": "testpassword123"
            }
        )
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "user_id" in data["data"]
    
    def test_register_duplicate_user(self, client):
        """Test registration with duplicate username."""
        # First registration
        client.post(
            "/auth/register",
            json={
                "username": "testuser2",
                "email": "test2@example.com",
                "password": "testpassword123"
            }
        )
        
        # Second registration with same username
        response = client.post(
            "/auth/register",
            json={
                "username": "testuser2",
                "email": "different@example.com",
                "password": "testpassword123"
            }
        )
        assert response.status_code == 400
    
    def test_login_valid_user(self, client):
        """Test login with valid credentials."""
        # Register user first
        client.post(
            "/auth/register",
            json={
                "username": "loginuser",
                "email": "login@example.com",
                "password": "testpassword123"
            }
        )
        
        # Login
        response = client.post(
            "/auth/login",
            json={
                "username": "loginuser",
                "password": "testpassword123"
            }
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"
    
    def test_login_invalid_user(self, client):
        """Test login with invalid credentials."""
        response = client.post(
            "/auth/login",
            json={
                "username": "nonexistent",
                "password": "wrongpassword"
            }
        )
        assert response.status_code == 401


class TestFileOperations:
    """Test file operation endpoints."""
    
    @pytest.fixture
    def authenticated_headers(self, client):
        """Get authentication headers for testing."""
        # Register and login
        client.post(
            "/auth/register",
            json={
                "username": "fileuser",
                "email": "file@example.com",
                "password": "testpassword123"
            }
        )
        
        response = client.post(
            "/auth/login",
            json={
                "username": "fileuser",
                "password": "testpassword123"
            }
        )
        
        token = response.json()["access_token"]
        return {"Authorization": f"Bearer {token}"}
    
    def test_list_files_empty(self, client, authenticated_headers):
        """Test listing files when none exist."""
        response = client.get("/files/", headers=authenticated_headers)
        assert response.status_code == 200
        assert response.json() == []
    
    def test_upload_file(self, client, authenticated_headers, temp_dir):
        """Test file upload."""
        # Create test file
        test_file_path = os.path.join(temp_dir, "test.txt")
        with open(test_file_path, "w") as f:
            f.write("Hello, World!")
        
        # Upload file
        with open(test_file_path, "rb") as f:
            response = client.post(
                "/files/upload",
                files={"file": ("test.txt", f, "text/plain")},
                data={"file_path": "test.txt", "client_id": "test_client"},
                headers={"Authorization": authenticated_headers["Authorization"]}
            )
        
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "file_id" in data["data"]
    
    def test_list_files_after_upload(self, client, authenticated_headers, temp_dir):
        """Test listing files after upload."""
        # Upload a file first
        test_file_path = os.path.join(temp_dir, "test2.txt")
        with open(test_file_path, "w") as f:
            f.write("Test content")
        
        with open(test_file_path, "rb") as f:
            client.post(
                "/files/upload",
                files={"file": ("test2.txt", f, "text/plain")},
                data={"file_path": "test2.txt", "client_id": "test_client"},
                headers={"Authorization": authenticated_headers["Authorization"]}
            )
        
        # List files
        response = client.get("/files/", headers=authenticated_headers)
        assert response.status_code == 200
        files = response.json()
        assert len(files) == 1
        assert files[0]["file_name"] == "test2.txt"


class TestFileWatcher:
    """Test file watcher functionality."""
    
    def test_file_state_database(self, temp_dir):
        """Test local file state management."""
        from client.file_watcher import LocalFileState
        
        db_path = os.path.join(temp_dir, "test.db")
        file_state = LocalFileState(db_path)
        
        # Test updating file state
        file_state.update_file_state("test.txt", "checksum123", 1000, 1234567890.0)
        
        # Test retrieving file state
        state = file_state.get_file_state("test.txt")
        assert state is not None
        assert state["checksum"] == "checksum123"
        assert state["file_size"] == 1000
    
    def test_sync_queue(self, temp_dir):
        """Test sync queue functionality."""
        from client.file_watcher import LocalFileState
        
        db_path = os.path.join(temp_dir, "test2.db")
        file_state = LocalFileState(db_path)
        
        # Add events to sync queue
        file_state.add_to_sync_queue("file1.txt", "create")
        file_state.add_to_sync_queue("file2.txt", "modify")
        
        # Get pending events
        events = file_state.get_pending_sync_events()
        assert len(events) == 2
        assert events[0]["event_type"] == "create"
        assert events[1]["event_type"] == "modify"


class TestSyncClient:
    """Test sync client functionality."""
    
    def test_client_config(self):
        """Test client configuration."""
        from shared.models import ClientConfig, ConflictResolution
        
        config = ClientConfig(
            server_url="http://localhost:8000",
            sync_folder="./test_sync"
        )
        
        assert config.server_url == "http://localhost:8000"
        assert config.sync_folder == "./test_sync"
        assert config.chunk_size == 5 * 1024 * 1024  # 5MB
        assert config.conflict_resolution.strategy == "auto_lww"


class TestUtilities:
    """Test utility functions."""
    
    def test_file_checksum(self, temp_dir):
        """Test file checksum calculation."""
        from shared.models import calculate_file_checksum
        
        test_file = os.path.join(temp_dir, "checksum_test.txt")
        with open(test_file, "w") as f:
            f.write("Test content for checksum")
        
        checksum = calculate_file_checksum(test_file)
        assert len(checksum) == 64  # SHA-256 hex length
        assert checksum != ""
    
    def test_encryption(self):
        """Test encryption utilities."""
        from shared.utils import EncryptionManager
        
        encryption = EncryptionManager()
        test_data = b"This is test data for encryption"
        password = "test_password_123"
        
        # Encrypt data
        encrypted_data, salt = encryption.encrypt_data(test_data, password)
        assert encrypted_data != test_data
        
        # Decrypt data
        decrypted_data = encryption.decrypt_data(encrypted_data, password, salt)
        assert decrypted_data == test_data
    
    def test_compression(self):
        """Test compression utilities."""
        from shared.utils import CompressionManager
        
        # Test data that should compress well
        test_data = b"A" * 1000  # Repetitive data
        
        compressed = CompressionManager.compress_data(test_data)
        assert len(compressed) < len(test_data)
        
        decompressed = CompressionManager.decompress_data(compressed)
        assert decompressed == test_data
    
    def test_file_chunking(self, temp_dir):
        """Test file chunking functionality."""
        from shared.utils import FileChunker
        
        # Create test file
        test_file = os.path.join(temp_dir, "chunk_test.txt")
        test_content = b"X" * (10 * 1024)  # 10KB
        
        with open(test_file, "wb") as f:
            f.write(test_content)
        
        # Test chunking with small chunk size
        chunker = FileChunker(chunk_size=4096)  # 4KB chunks
        chunks = chunker.chunk_file(test_file)
        
        assert len(chunks) == 3  # 10KB / 4KB = 2.5, rounded up to 3
        
        # Test reconstruction
        output_file = os.path.join(temp_dir, "reconstructed.txt")
        success = chunker.reconstruct_file(chunks, output_file)
        assert success
        
        # Verify content
        with open(output_file, "rb") as f:
            reconstructed_content = f.read()
        
        assert reconstructed_content == test_content


if __name__ == "__main__":
    pytest.main([__file__])
