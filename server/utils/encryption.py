from cryptography.fernet import Fernet
from pathlib import Path
import os
import hashlib

class FileEncryption:
    def __init__(self, key: bytes = None):
        if key is None:
            key = Fernet.generate_key()
        self.fernet = Fernet(key)
        self.key = key
    
    def encrypt_file(self, file_path: Path) -> bytes:
        """Encrypt a file and return encrypted data"""
        with open(file_path, 'rb') as file:
            return self.fernet.encrypt(file.read())
            
    def decrypt_file(self, encrypted_data: bytes) -> bytes:
        """Decrypt file data"""
        return self.fernet.decrypt(encrypted_data)
    
    def encrypt_data(self, data: bytes) -> bytes:
        """Encrypt raw data"""
        return self.fernet.encrypt(data)
    
    def decrypt_data(self, encrypted_data: bytes) -> bytes:
        """Decrypt raw data"""
        return self.fernet.decrypt(encrypted_data)

def calculate_checksum(file_path: str) -> str:
    """Calculate SHA-256 checksum of a file"""
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(4096), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()

def calculate_data_checksum(data: bytes) -> str:
    """Calculate SHA-256 checksum of data"""
    return hashlib.sha256(data).hexdigest()
