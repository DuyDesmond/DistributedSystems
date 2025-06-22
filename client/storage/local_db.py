import sqlite3
from typing import List, Dict, Optional
from datetime import datetime
import json
import sys
import os

# Add parent directory to path for imports
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '..'))

from common.models import FileMetadata

class LocalDatabase:
    def __init__(self, db_path: str = "client_sync.db"):
        self.db_path = db_path
        self._init_database()
    
    def _init_database(self):
        """Initialize the local SQLite database"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    id TEXT PRIMARY KEY,
                    path TEXT NOT NULL,
                    checksum TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    modified_at TEXT NOT NULL,
                    version_vector TEXT NOT NULL,
                    is_deleted INTEGER DEFAULT 0,
                    sync_status TEXT DEFAULT 'pending'
                )
            """)
            
            conn.execute("""
                CREATE TABLE IF NOT EXISTS sync_state (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)
    
    def store_file_metadata(self, file_metadata: FileMetadata):
        """Store file metadata in local database"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                INSERT OR REPLACE INTO files 
                (id, path, checksum, size, modified_at, version_vector, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (
                file_metadata.id,
                file_metadata.path,
                file_metadata.checksum,
                file_metadata.size,
                file_metadata.modified_at.isoformat(),
                json.dumps(file_metadata.version_vector),
                file_metadata.is_deleted
            ))
    
    def get_file_metadata(self, file_path: str) -> Optional[FileMetadata]:
        """Get file metadata by path"""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.execute("""
                SELECT id, path, checksum, size, modified_at, version_vector, is_deleted
                FROM files WHERE path = ? AND is_deleted = 0
            """, (file_path,))
            
            row = cursor.fetchone()
            if row:
                return FileMetadata(
                    id=row[0],
                    path=row[1],
                    checksum=row[2],
                    size=row[3],
                    owner_id="",  # Not stored locally
                    version_vector=json.loads(row[5]),
                    modified_at=datetime.fromisoformat(row[4]),
                    is_deleted=bool(row[6])
                )
        
        return None
    
    def get_all_files(self) -> List[FileMetadata]:
        """Get all file metadata"""
        files = []
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.execute("""
                SELECT id, path, checksum, size, modified_at, version_vector, is_deleted
                FROM files WHERE is_deleted = 0
            """)
            
            for row in cursor.fetchall():
                files.append(FileMetadata(
                    id=row[0],
                    path=row[1],
                    checksum=row[2],
                    size=row[3],
                    owner_id="",
                    version_vector=json.loads(row[5]),
                    modified_at=datetime.fromisoformat(row[4]),
                    is_deleted=bool(row[6])
                ))
        
        return files
    
    def mark_file_deleted(self, file_path: str):
        """Mark a file as deleted"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                UPDATE files SET is_deleted = 1 WHERE path = ?
            """, (file_path,))
    
    def update_sync_status(self, file_id: str, status: str):
        """Update sync status of a file"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                UPDATE files SET sync_status = ? WHERE id = ?
            """, (status, file_id))
    
    def get_pending_sync_files(self) -> List[FileMetadata]:
        """Get files that need to be synced"""
        files = []
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.execute("""
                SELECT id, path, checksum, size, modified_at, version_vector, is_deleted
                FROM files WHERE sync_status = 'pending'
            """)
            
            for row in cursor.fetchall():
                files.append(FileMetadata(
                    id=row[0],
                    path=row[1],
                    checksum=row[2],
                    size=row[3],
                    owner_id="",
                    version_vector=json.loads(row[5]),
                    modified_at=datetime.fromisoformat(row[4]),
                    is_deleted=bool(row[6])
                ))
        
        return files
    
    def set_sync_state(self, key: str, value: str):
        """Set a sync state value"""
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("""
                INSERT OR REPLACE INTO sync_state (key, value) VALUES (?, ?)
            """, (key, value))
    
    def get_sync_state(self, key: str) -> Optional[str]:
        """Get a sync state value"""
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.execute("""
                SELECT value FROM sync_state WHERE key = ?
            """, (key,))
            
            row = cursor.fetchone()
            return row[0] if row else None
