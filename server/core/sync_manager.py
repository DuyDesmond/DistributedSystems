from typing import Dict, List, Optional
from sqlalchemy.orm import Session
from datetime import datetime
import json
import sys
import os

# Add parent directory to path for imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.models import FileEvent, FileEventType, ConflictResolution
from models.file import File, User
from core.file_manager import FileManager

class SyncManager:
    def __init__(self, db: Session, file_manager: FileManager):
        self.db = db
        self.file_manager = file_manager
    
    def process_file_event(self, event: FileEvent, user: User) -> Dict:
        """Process a file event from client"""
        existing_file = self.db.query(File).filter(
            File.path == event.file_path,
            File.owner_id == user.id
        ).first()
        
        if event.event_type == FileEventType.DELETE:
            return self._handle_delete(existing_file, event)
        
        elif event.event_type in [FileEventType.CREATE, FileEventType.MODIFY]:
            return self._handle_create_or_modify(existing_file, event, user)
    
    def _handle_delete(self, existing_file: Optional[File], event: FileEvent) -> Dict:
        """Handle file deletion"""
        if existing_file:
            existing_file.is_deleted = True
            existing_file.modified_at = event.timestamp
            self.db.commit()
            
            # Delete actual file
            self.file_manager.delete_file(existing_file.id)
            
            return {"status": "deleted", "file_id": existing_file.id}
        
        return {"status": "not_found"}
    
    def _handle_create_or_modify(self, existing_file: Optional[File], event: FileEvent, user: User) -> Dict:
        """Handle file creation or modification"""
        if existing_file:
            # Check for conflicts
            conflict = self._detect_conflict(existing_file, event)
            if conflict:
                return self._resolve_conflict(existing_file, event, user)
            
            # Update existing file
            existing_file.checksum = event.checksum
            existing_file.size = event.size
            existing_file.version_vector = event.version_vector
            existing_file.modified_at = event.timestamp
            self.db.commit()
            
            return {"status": "updated", "file_id": existing_file.id}
        else:
            # Create new file
            new_file = File(
                path=event.file_path,
                checksum=event.checksum,
                size=event.size,
                owner_id=user.id,
                version_vector=event.version_vector,
                modified_at=event.timestamp
            )
            self.db.add(new_file)
            self.db.commit()
            
            return {"status": "created", "file_id": new_file.id}
    
    def _detect_conflict(self, existing_file: File, event: FileEvent) -> bool:
        """Detect if there's a conflict between existing file and new event"""
        # Simple conflict detection: check if checksums differ and timestamps are close
        if existing_file.checksum != event.checksum:
            time_diff = abs((existing_file.modified_at - event.timestamp).total_seconds())
            return time_diff < 300  # 5 minutes threshold
        
        return False
    
    def _resolve_conflict(self, existing_file: File, event: FileEvent, user: User) -> Dict:
        """Resolve conflict using Last-Write-Wins strategy"""
        if event.timestamp > existing_file.modified_at:
            # New event wins
            existing_file.checksum = event.checksum
            existing_file.size = event.size
            existing_file.version_vector = event.version_vector
            existing_file.modified_at = event.timestamp
            self.db.commit()
            
            return {
                "status": "conflict_resolved",
                "resolution": "new_version_wins",
                "file_id": existing_file.id
            }
        else:
            # Existing file wins
            return {
                "status": "conflict_resolved",
                "resolution": "existing_version_wins",
                "file_id": existing_file.id,
                "current_version": {
                    "checksum": existing_file.checksum,
                    "modified_at": existing_file.modified_at.isoformat()
                }
            }
    
    def get_file_list(self, user_id: str) -> List[Dict]:
        """Get list of files for a user"""
        files = self.db.query(File).filter(
            File.owner_id == user_id,
            File.is_deleted == False
        ).all()
        
        return [
            {
                "id": file.id,
                "path": file.path,
                "checksum": file.checksum,
                "size": file.size,
                "modified_at": file.modified_at.isoformat(),
                "version_vector": file.version_vector
            }
            for file in files
        ]
    
    def get_changes_since(self, user_id: str, timestamp: datetime) -> List[Dict]:
        """Get all file changes since a specific timestamp"""
        files = self.db.query(File).filter(
            File.owner_id == user_id,
            File.modified_at > timestamp
        ).all()
        
        return [
            {
                "id": file.id,
                "path": file.path,
                "checksum": file.checksum,
                "size": file.size,
                "modified_at": file.modified_at.isoformat(),
                "version_vector": file.version_vector,
                "is_deleted": file.is_deleted
            }
            for file in files
        ]
