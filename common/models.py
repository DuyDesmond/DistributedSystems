from pydantic import BaseModel
from typing import Optional, Dict, List
from datetime import datetime
from enum import Enum
import uuid

class FileEventType(str, Enum):
    CREATE = "create"
    MODIFY = "modify"
    DELETE = "delete"

class FileEvent(BaseModel):
    id: str = str(uuid.uuid4())
    file_path: str
    event_type: FileEventType
    checksum: Optional[str] = None
    size: Optional[int] = None
    timestamp: datetime
    user_id: str
    version_vector: Dict[str, int] = {}

class UserModel(BaseModel):
    id: str
    username: str
    email: Optional[str] = None
    created_at: datetime

class FileMetadata(BaseModel):
    id: str
    path: str
    checksum: str
    size: int
    owner_id: str
    version_vector: Dict[str, int]
    modified_at: datetime
    is_deleted: bool = False

class SyncStatus(BaseModel):
    file_id: str
    status: str  # "syncing", "completed", "conflict", "error"
    progress: float = 0.0
    message: Optional[str] = None

class ConflictResolution(BaseModel):
    file_id: str
    resolution_strategy: str  # "lww", "manual", "merge"
    chosen_version: Optional[str] = None
