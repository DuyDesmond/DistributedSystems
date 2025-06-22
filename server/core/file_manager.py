import os
import shutil
from pathlib import Path
from typing import Optional, List
import sys

# Add parent directory to path for imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.constants import FILES_DIRECTORY, CHUNK_SIZE
from utils.encryption import calculate_checksum, FileEncryption

class FileManager:
    def __init__(self, storage_directory: str = FILES_DIRECTORY):
        self.storage_directory = Path(storage_directory)
        self.storage_directory.mkdir(exist_ok=True)
        self.encryption = FileEncryption()
    
    def store_file(self, file_data: bytes, file_id: str, encrypt: bool = True) -> str:
        """Store file data and return the storage path"""
        file_path = self.storage_directory / file_id
        
        if encrypt:
            encrypted_data = self.encryption.encrypt_data(file_data)
            with open(file_path, 'wb') as f:
                f.write(encrypted_data)
        else:
            with open(file_path, 'wb') as f:
                f.write(file_data)
        
        return str(file_path)
    
    def retrieve_file(self, file_id: str, decrypt: bool = True) -> Optional[bytes]:
        """Retrieve file data by file ID"""
        file_path = self.storage_directory / file_id
        
        if not file_path.exists():
            return None
            
        with open(file_path, 'rb') as f:
            data = f.read()
            
        if decrypt:
            try:
                return self.encryption.decrypt_data(data)
            except Exception:
                # If decryption fails, return raw data
                return data
        
        return data
    
    def delete_file(self, file_id: str) -> bool:
        """Delete a file by file ID"""
        file_path = self.storage_directory / file_id
        
        if file_path.exists():
            file_path.unlink()
            return True
        
        return False
    
    def chunk_file(self, file_data: bytes, chunk_size: int = CHUNK_SIZE) -> List[bytes]:
        """Split file data into chunks"""
        chunks = []
        for i in range(0, len(file_data), chunk_size):
            chunks.append(file_data[i:i + chunk_size])
        return chunks
    
    def combine_chunks(self, chunks: List[bytes]) -> bytes:
        """Combine file chunks back into complete file data"""
        return b''.join(chunks)
    
    def get_file_size(self, file_id: str) -> Optional[int]:
        """Get file size by file ID"""
        file_path = self.storage_directory / file_id
        
        if file_path.exists():
            return file_path.stat().st_size
        
        return None
