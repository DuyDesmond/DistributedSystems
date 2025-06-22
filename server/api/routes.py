from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File as FastAPIFile
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from pydantic import BaseModel
from datetime import datetime, timedelta
from typing import List, Optional
import io
import sys
import os

# Add parent directory to path for imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.models import FileEvent, UserModel, FileMetadata
from models.file import User, File
from core.auth import get_current_user, create_access_token, verify_password, get_password_hash
from core.sync_manager import SyncManager
from core.file_manager import FileManager

router = APIRouter()

# Database dependency (will be injected)
def get_db():
    # This will be implemented in main.py
    pass

# Request/Response models
class UserCreate(BaseModel):
    username: str
    password: str
    email: Optional[str] = None

class UserLogin(BaseModel):
    username: str
    password: str

class Token(BaseModel):
    access_token: str
    token_type: str

# Authentication endpoints
@router.post("/auth/register", response_model=Token)
async def register(user_data: UserCreate, db: Session = Depends(get_db)):
    """Register a new user"""
    # Check if user already exists
    existing_user = db.query(User).filter(User.username == user_data.username).first()
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Username already registered"
        )
    
    # Create new user
    hashed_password = get_password_hash(user_data.password)
    new_user = User(
        username=user_data.username,
        email=user_data.email,
        hashed_password=hashed_password
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    # Create access token
    access_token = create_access_token(data={"sub": new_user.username})
    return {"access_token": access_token, "token_type": "bearer"}

@router.post("/auth/login", response_model=Token)
async def login(user_data: UserLogin, db: Session = Depends(get_db)):
    """Login user"""
    user = db.query(User).filter(User.username == user_data.username).first()
    if not user or not verify_password(user_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password"
        )
    
    access_token = create_access_token(data={"sub": user.username})
    return {"access_token": access_token, "token_type": "bearer"}

# File management endpoints
@router.post("/files/upload")
async def upload_file(
    file: UploadFile = FastAPIFile(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Upload a file"""
    file_manager = FileManager()
    sync_manager = SyncManager(db, file_manager)
    
    # Read file data
    file_data = await file.read()
    
    # Create file event
    file_event = FileEvent(
        file_path=file.filename,
        event_type="create",
        checksum="",  # Will be calculated
        size=len(file_data),
        timestamp=datetime.utcnow(),
        user_id=current_user.id
    )
    
    # Store file
    file_path = file_manager.store_file(file_data, file_event.id)
    
    # Process event
    result = sync_manager.process_file_event(file_event, current_user)
    
    return {"message": "File uploaded successfully", "result": result}

@router.get("/files/download/{file_id}")
async def download_file(
    file_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Download a file"""
    file_record = db.query(File).filter(
        File.id == file_id,
        File.owner_id == current_user.id
    ).first()
    
    if not file_record:
        raise HTTPException(status_code=404, detail="File not found")
    
    file_manager = FileManager()
    file_data = file_manager.retrieve_file(file_id)
    
    if not file_data:
        raise HTTPException(status_code=404, detail="File content not found")
    
    return StreamingResponse(
        io.BytesIO(file_data),
        media_type="application/octet-stream",
        headers={"Content-Disposition": f"attachment; filename={file_record.path}"}
    )

@router.get("/files/list")
async def list_files(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """List user's files"""
    sync_manager = SyncManager(db, FileManager())
    files = sync_manager.get_file_list(current_user.id)
    return {"files": files}

@router.post("/sync/event")
async def sync_event(
    event: FileEvent,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Process a sync event from client"""
    sync_manager = SyncManager(db, FileManager())
    result = sync_manager.process_file_event(event, current_user)
    return result

@router.get("/sync/changes")
async def get_changes(
    since: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Get changes since a specific timestamp"""
    sync_manager = SyncManager(db, FileManager())
    
    if since:
        timestamp = datetime.fromisoformat(since)
    else:
        timestamp = datetime.utcnow() - timedelta(days=30)
    
    changes = sync_manager.get_changes_since(current_user.id, timestamp)
    return {"changes": changes}
