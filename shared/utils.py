"""Shared utilities for encryption, compression, and other common operations."""

import os
import gzip
import hashlib
import secrets
from typing import Tuple, Optional
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import base64


class EncryptionManager:
    """Handles file encryption and decryption."""
    
    def __init__(self, password: Optional[str] = None):
        """Initialize encryption manager with optional password."""
        self.password = password
        self._key = None
        
    def derive_key(self, password: str, salt: bytes) -> bytes:
        """Derive encryption key from password and salt."""
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=salt,
            iterations=100000,
        )
        return base64.urlsafe_b64encode(kdf.derive(password.encode()))
    
    def generate_salt(self) -> bytes:
        """Generate a random salt."""
        return os.urandom(16)
    
    def encrypt_data(self, data: bytes, password: str) -> Tuple[bytes, bytes]:
        """Encrypt data with password. Returns (encrypted_data, salt)."""
        salt = self.generate_salt()
        key = self.derive_key(password, salt)
        f = Fernet(key)
        encrypted_data = f.encrypt(data)
        return encrypted_data, salt
    
    def decrypt_data(self, encrypted_data: bytes, password: str, salt: bytes) -> bytes:
        """Decrypt data with password and salt."""
        key = self.derive_key(password, salt)
        f = Fernet(key)
        return f.decrypt(encrypted_data)
    
    def encrypt_file(self, file_path: str, password: str, output_path: str) -> bytes:
        """Encrypt a file and save to output path. Returns salt."""
        with open(file_path, 'rb') as infile:
            data = infile.read()
        
        encrypted_data, salt = self.encrypt_data(data, password)
        
        with open(output_path, 'wb') as outfile:
            outfile.write(encrypted_data)
        
        return salt
    
    def decrypt_file(self, encrypted_path: str, password: str, salt: bytes, output_path: str):
        """Decrypt a file and save to output path."""
        with open(encrypted_path, 'rb') as infile:
            encrypted_data = infile.read()
        
        decrypted_data = self.decrypt_data(encrypted_data, password, salt)
        
        with open(output_path, 'wb') as outfile:
            outfile.write(decrypted_data)


class CompressionManager:
    """Handles file compression and decompression."""
    
    @staticmethod
    def should_compress(file_path: str, threshold_ratio: float = 0.9) -> bool:
        """Determine if a file should be compressed based on a sample."""
        try:
            # Sample first 1KB to check compression ratio
            with open(file_path, 'rb') as f:
                sample = f.read(1024)
            
            if len(sample) < 100:  # Too small to compress
                return False
            
            compressed_sample = gzip.compress(sample)
            ratio = len(compressed_sample) / len(sample)
            
            return ratio < threshold_ratio
        except Exception:
            return False
    
    @staticmethod
    def compress_data(data: bytes) -> bytes:
        """Compress data using gzip."""
        return gzip.compress(data)
    
    @staticmethod
    def decompress_data(compressed_data: bytes) -> bytes:
        """Decompress gzip-compressed data."""
        return gzip.decompress(compressed_data)
    
    @staticmethod
    def compress_file(input_path: str, output_path: str) -> bool:
        """Compress a file. Returns True if successful."""
        try:
            with open(input_path, 'rb') as infile:
                with gzip.open(output_path, 'wb') as outfile:
                    outfile.writelines(infile)
            return True
        except Exception:
            return False
    
    @staticmethod
    def decompress_file(compressed_path: str, output_path: str) -> bool:
        """Decompress a file. Returns True if successful."""
        try:
            with gzip.open(compressed_path, 'rb') as infile:
                with open(output_path, 'wb') as outfile:
                    outfile.writelines(infile)
            return True
        except Exception:
            return False


class FileChunker:
    """Handles file chunking for large file transfers."""
    
    def __init__(self, chunk_size: int = 5 * 1024 * 1024):  # 5MB default
        self.chunk_size = chunk_size
    
    def chunk_file(self, file_path: str) -> list:
        """Split file into chunks. Returns list of chunk info."""
        chunks = []
        file_size = os.path.getsize(file_path)
        total_chunks = (file_size + self.chunk_size - 1) // self.chunk_size
        
        with open(file_path, 'rb') as f:
            for i in range(total_chunks):
                chunk_data = f.read(self.chunk_size)
                chunk_hash = hashlib.sha256(chunk_data).hexdigest()
                
                chunks.append({
                    'chunk_number': i,
                    'total_chunks': total_chunks,
                    'data': chunk_data,
                    'checksum': chunk_hash,
                    'size': len(chunk_data)
                })
        
        return chunks
    
    def reconstruct_file(self, chunks: list, output_path: str) -> bool:
        """Reconstruct file from chunks."""
        try:
            # Sort chunks by chunk_number
            sorted_chunks = sorted(chunks, key=lambda x: x['chunk_number'])
            
            with open(output_path, 'wb') as f:
                for chunk in sorted_chunks:
                    f.write(chunk['data'])
            
            return True
        except Exception:
            return False
    
    def verify_chunk(self, chunk_data: bytes, expected_checksum: str) -> bool:
        """Verify chunk integrity."""
        actual_checksum = hashlib.sha256(chunk_data).hexdigest()
        return actual_checksum == expected_checksum


def generate_file_id() -> str:
    """Generate a unique file ID."""
    return secrets.token_urlsafe(16)


def generate_client_id() -> str:
    """Generate a unique client ID."""
    return secrets.token_urlsafe(8)


def sanitize_filename(filename: str) -> str:
    """Sanitize filename for safe storage."""
    # Remove or replace unsafe characters
    unsafe_chars = '<>:"/\\|?*'
    for char in unsafe_chars:
        filename = filename.replace(char, '_')
    
    # Limit length
    if len(filename) > 255:
        name, ext = os.path.splitext(filename)
        filename = name[:255-len(ext)] + ext
    
    return filename


def calculate_content_hash(data: bytes) -> str:
    """Calculate content hash for deduplication."""
    return hashlib.sha256(data).hexdigest()


class DeltaSync:
    """Handles delta synchronization for modified files."""
    
    @staticmethod
    def calculate_diff(old_data: bytes, new_data: bytes) -> bytes:
        """Calculate binary diff between two file versions."""
        # Simple implementation - in production, use a proper binary diff algorithm
        # like bsdiff or similar
        return new_data  # Placeholder - return full content for now
    
    @staticmethod
    def apply_diff(old_data: bytes, diff_data: bytes) -> bytes:
        """Apply diff to reconstruct new file version."""
        # Placeholder implementation
        return diff_data
