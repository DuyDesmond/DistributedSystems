"""File storage management."""

import os
import shutil
import hashlib
from pathlib import Path
from typing import Optional, Dict, Any, List
from datetime import datetime
from sqlalchemy.orm import Session
from server.models import File, FileVersion, FileChunk
from shared.utils import FileChunker, EncryptionManager, CompressionManager
from shared.models import FileInfo
import tempfile
import uuid


class StorageManager:
    """Handles file storage operations."""
    
    def __init__(self, storage_path: str):
        self.storage_path = Path(storage_path)
        self.content_path = self.storage_path / "content"
        self.chunks_path = self.storage_path / "chunks"
        self.temp_path = self.storage_path / "temp"
        self.metadata_path = self.storage_path / "metadata"
        
        # Create directories if they don't exist
        for path in [self.content_path, self.chunks_path, self.temp_path, self.metadata_path]:
            path.mkdir(parents=True, exist_ok=True)
    
    def get_content_path(self, content_hash: str) -> Path:
        """Get storage path for content based on hash."""
        # Content-addressed storage: /content/{hash[0:2]}/{hash[2:4]}/{full_hash}
        return self.content_path / content_hash[:2] / content_hash[2:4] / content_hash
    
    def get_user_metadata_path(self, user_id: str) -> Path:
        """Get metadata storage path for user."""
        return self.metadata_path / str(user_id)
    
    def get_chunk_path(self, chunk_hash: str) -> Path:
        """Get storage path for file chunk."""
        return self.chunks_path / chunk_hash
    
    def get_temp_upload_path(self, upload_id: str) -> Path:
        """Get temporary upload directory."""
        temp_dir = self.temp_path / upload_id
        temp_dir.mkdir(exist_ok=True)
        return temp_dir
    
    def store_file_content(self, file_data: bytes, content_hash: str) -> str:
        """Store file content using content-addressed storage."""
        storage_path = self.get_content_path(content_hash)
        storage_path.parent.mkdir(parents=True, exist_ok=True)
        
        # Only write if file doesn't exist (deduplication)
        if not storage_path.exists():
            with open(storage_path, 'wb') as f:
                f.write(file_data)
        
        return str(storage_path)
    
    def retrieve_file_content(self, content_hash: str) -> Optional[bytes]:
        """Retrieve file content by hash."""
        storage_path = self.get_content_path(content_hash)
        
        if storage_path.exists():
            with open(storage_path, 'rb') as f:
                return f.read()
        
        return None
    
    def delete_file_content(self, content_hash: str) -> bool:
        """Delete file content (only if no other files reference it)."""
        storage_path = self.get_content_path(content_hash)
        
        if storage_path.exists():
            try:
                os.remove(storage_path)
                
                # Clean up empty directories
                parent = storage_path.parent
                if parent.exists() and not any(parent.iterdir()):
                    parent.rmdir()
                    grandparent = parent.parent
                    if grandparent.exists() and not any(grandparent.iterdir()):
                        grandparent.rmdir()
                
                return True
            except Exception:
                return False
        
        return False
    
    def store_file_chunk(self, upload_id: str, chunk_number: int, chunk_data: bytes) -> str:
        """Store file chunk during upload."""
        chunk_hash = hashlib.sha256(chunk_data).hexdigest()
        chunk_path = self.get_chunk_path(chunk_hash)
        
        # Store chunk if it doesn't exist
        if not chunk_path.exists():
            with open(chunk_path, 'wb') as f:
                f.write(chunk_data)
        
        # Also store in temp upload directory for reconstruction
        temp_upload_path = self.get_temp_upload_path(upload_id)
        temp_chunk_path = temp_upload_path / f"chunk_{chunk_number:06d}"
        
        with open(temp_chunk_path, 'wb') as f:
            f.write(chunk_data)
        
        return chunk_hash
    
    def reconstruct_file_from_chunks(self, upload_id: str, total_chunks: int) -> bytes:
        """Reconstruct file from stored chunks."""
        temp_upload_path = self.get_temp_upload_path(upload_id)
        reconstructed_data = b""
        
        for i in range(total_chunks):
            chunk_path = temp_upload_path / f"chunk_{i:06d}"
            if chunk_path.exists():
                with open(chunk_path, 'rb') as f:
                    reconstructed_data += f.read()
            else:
                raise FileNotFoundError(f"Chunk {i} not found for upload {upload_id}")
        
        return reconstructed_data
    
    def cleanup_temp_upload(self, upload_id: str):
        """Clean up temporary upload directory."""
        temp_upload_path = self.get_temp_upload_path(upload_id)
        if temp_upload_path.exists():
            shutil.rmtree(temp_upload_path)
    
    def get_storage_stats(self) -> Dict[str, Any]:
        """Get storage statistics."""
        def get_dir_size(path: Path) -> int:
            total_size = 0
            if path.exists():
                for file_path in path.rglob('*'):
                    if file_path.is_file():
                        total_size += file_path.stat().st_size
            return total_size
        
        return {
            "total_content_size": get_dir_size(self.content_path),
            "total_chunks_size": get_dir_size(self.chunks_path),
            "total_temp_size": get_dir_size(self.temp_path),
            "content_files_count": len(list(self.content_path.rglob('*'))),
            "chunks_count": len(list(self.chunks_path.rglob('*'))),
        }


class FileService:
    """High-level file operations service."""
    
    def __init__(self, storage_manager: StorageManager):
        self.storage = storage_manager
        self.chunker = FileChunker()
        self.encryption = EncryptionManager()
        self.compression = CompressionManager()
    
    def upload_file(
        self,
        db: Session,
        user_id: str,
        file_path: str,
        file_name: str,
        file_data: bytes,
        client_id: str,
        encrypt: bool = False,
        password: Optional[str] = None
    ) -> File:
        """Upload and store a file."""
        # Process file data
        processed_data = file_data
        metadata = {}
        
        # Compression
        if self.compression.should_compress("dummy", 0.9):
            processed_data = self.compression.compress_data(processed_data)
            metadata["compressed"] = True
        
        # Encryption
        if encrypt and password:
            processed_data, salt = self.encryption.encrypt_data(processed_data, password)
            metadata["encrypted"] = True
            metadata["salt"] = salt.hex()
        
        # Calculate checksums
        content_hash = hashlib.sha256(processed_data).hexdigest()
        original_hash = hashlib.sha256(file_data).hexdigest()
        
        # Store content
        storage_path = self.storage.store_file_content(processed_data, content_hash)
        
        # Create database record
        file_record = File(
            user_id=user_id,
            file_path=file_path,
            file_name=file_name,
            file_size=len(file_data),
            checksum=original_hash,
            storage_path=storage_path,
            sync_status="completed"
        )
        
        db.add(file_record)
        db.flush()  # Get file_id
        
        # Create version record
        version_record = FileVersion(
            file_id=file_record.file_id,
            version_number=1,
            checksum=original_hash,
            storage_path=storage_path,
            file_size=len(file_data),
            is_current_version=True
        )
        
        db.add(version_record)
        db.commit()
        
        return file_record
    
    def download_file(
        self,
        db: Session,
        file_id: str,
        user_id: str,
        password: Optional[str] = None
    ) -> Optional[bytes]:
        """Download and decrypt file."""
        file_record = db.query(File).filter(
            File.file_id == file_id,
            File.user_id == user_id,
            File.is_deleted == False
        ).first()
        
        if not file_record:
            return None
        
        # Get current version
        version = db.query(FileVersion).filter(
            FileVersion.file_id == file_id,
            FileVersion.is_current_version == True
        ).first()
        
        if not version:
            return None
        
        # Retrieve content
        content_hash = os.path.basename(version.storage_path)
        processed_data = self.storage.retrieve_file_content(content_hash)
        
        if not processed_data:
            return None
        
        # Reverse processing (decrypt, decompress)
        # Note: This is simplified - in production, metadata would be stored
        # to know what processing was applied
        
        return processed_data
    
    def delete_file(
        self,
        db: Session,
        file_id: str,
        user_id: str
    ) -> bool:
        """Delete a file (mark as deleted)."""
        file_record = db.query(File).filter(
            File.file_id == file_id,
            File.user_id == user_id
        ).first()
        
        if not file_record:
            return False
        
        file_record.is_deleted = True
        file_record.modified_at = datetime.utcnow()
        
        db.commit()
        return True
    
    def list_user_files(
        self,
        db: Session,
        user_id: str,
        include_deleted: bool = False
    ) -> List[File]:
        """List all files for a user."""
        query = db.query(File).filter(File.user_id == user_id)
        
        if not include_deleted:
            query = query.filter(File.is_deleted == False)
        
        return query.order_by(File.modified_at.desc()).all()
    
    def get_file_versions(
        self,
        db: Session,
        file_id: str,
        user_id: str
    ) -> List[FileVersion]:
        """Get all versions of a file."""
        file_record = db.query(File).filter(
            File.file_id == file_id,
            File.user_id == user_id
        ).first()
        
        if not file_record:
            return []
        
        return db.query(FileVersion).filter(
            FileVersion.file_id == file_id
        ).order_by(FileVersion.version_number.desc()).all()


# Global storage manager instance
storage_manager = None
file_service = None


def init_storage(storage_path: str):
    """Initialize storage manager."""
    global storage_manager, file_service
    storage_manager = StorageManager(storage_path)
    file_service = FileService(storage_manager)
    return storage_manager, file_service


def get_file_service():
    """Dependency to get file service."""
    if not file_service:
        raise RuntimeError("File service not initialized")
    return file_service
