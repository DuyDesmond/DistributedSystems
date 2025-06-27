"""FastAPI application and API routes."""

import os
import json
import asyncio
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any
from fastapi import FastAPI, HTTPException, Depends, UploadFile, File as FastAPIFile, status, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer
from sqlalchemy.orm import Session
from pydantic import BaseModel

from server.database import init_database, init_redis, init_rabbitmq, get_db_session
from server.auth import init_auth, get_current_user
from server.storage import init_storage, get_file_service
from server.models import User as DBUser, File as DBFile, SyncEvent
from shared.models import User, FileInfo, SyncEvent as SharedSyncEvent, APIResponse, AuthToken
from shared.utils import generate_client_id
import uuid


# Pydantic models for API
class UserCreate(BaseModel):
    username: str
    email: str
    password: str


class UserLogin(BaseModel):
    username: str
    password: str


class FileUpload(BaseModel):
    file_path: str
    client_id: str


class SyncChangesResponse(BaseModel):
    files: List[FileInfo]
    events: List[SharedSyncEvent]
    server_timestamp: datetime


class HeartbeatRequest(BaseModel):
    client_id: str


# WebSocket connection manager
class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}
    
    async def connect(self, websocket: WebSocket, client_id: str):
        await websocket.accept()
        self.active_connections[client_id] = websocket
    
    def disconnect(self, client_id: str):
        if client_id in self.active_connections:
            del self.active_connections[client_id]
    
    async def send_personal_message(self, message: str, client_id: str):
        if client_id in self.active_connections:
            try:
                await self.active_connections[client_id].send_text(message)
            except Exception:
                self.disconnect(client_id)
    
    async def broadcast(self, message: str):
        for connection in self.active_connections.values():
            try:
                await connection.send_text(message)
            except Exception:
                pass


# Initialize FastAPI app
app = FastAPI(title="File Sync Server", version="1.0.0")

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure properly for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global managers
manager = ConnectionManager()
auth_manager = None
file_service = None


@app.on_event("startup")
async def startup_event():
    """Initialize services on startup."""
    global auth_manager, file_service
    
    # Get configuration from environment
    database_url = os.getenv("DATABASE_URL", "postgresql://filesync:filesync_password@localhost:5432/filesync")
    redis_url = os.getenv("REDIS_URL", "redis://localhost:6379")
    rabbitmq_url = os.getenv("RABBITMQ_URL", "amqp://filesync:filesync_password@localhost:5672/")
    storage_path = os.getenv("STORAGE_PATH", "./storage")
    jwt_secret = os.getenv("JWT_SECRET", "your-secret-key-change-in-production")
    
    # Initialize services
    init_database(database_url)
    init_redis(redis_url)
    init_rabbitmq(rabbitmq_url)
    auth_manager = init_auth(jwt_secret)
    _, file_service = init_storage(storage_path)


# Authentication endpoints
@app.post("/auth/register", response_model=APIResponse)
async def register_user(user_data: UserCreate, db: Session = Depends(get_db_session)):
    """Register a new user."""
    # Check if user already exists
    existing_user = db.query(DBUser).filter(
        (DBUser.username == user_data.username) | (DBUser.email == user_data.email)
    ).first()
    
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Username or email already exists"
        )
    
    # Create new user
    hashed_password = auth_manager.hash_password(user_data.password)
    new_user = DBUser(
        username=user_data.username,
        email=user_data.email,
        password_hash=hashed_password
    )
    
    db.add(new_user)
    db.commit()
    db.refresh(new_user)
    
    return APIResponse(
        success=True,
        message="User registered successfully",
        data={"user_id": str(new_user.user_id)}
    )


@app.post("/auth/login", response_model=AuthToken)
async def login_user(user_data: UserLogin, db: Session = Depends(get_db_session)):
    """Login user and return tokens."""
    # Find user
    user = db.query(DBUser).filter(DBUser.username == user_data.username).first()
    
    if not user or not auth_manager.verify_password(user_data.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password"
        )
    
    if user.account_status != "active":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is not active"
        )
    
    # Create session
    client_id = generate_client_id()
    tokens = auth_manager.create_user_session(
        db, str(user.user_id), client_id, timedelta(hours=24)
    )
    
    # Update last login
    user.last_login = datetime.utcnow()
    db.commit()
    
    return AuthToken(**tokens)


@app.post("/auth/refresh", response_model=AuthToken)
async def refresh_token(refresh_token: str, db: Session = Depends(get_db_session)):
    """Refresh access token."""
    tokens = auth_manager.refresh_access_token(db, refresh_token)
    return AuthToken(**tokens)


@app.post("/auth/logout", response_model=APIResponse)
async def logout_user(
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Logout user and invalidate session."""
    # This would need the access token from the request
    # Simplified implementation
    return APIResponse(success=True, message="Logged out successfully")


# File operation endpoints
@app.get("/files/", response_model=List[FileInfo])
async def list_files(
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """List user's files."""
    files = file_service.list_user_files(db, str(current_user.user_id))
    
    return [
        FileInfo(
            file_id=str(f.file_id),
            user_id=str(f.user_id),
            file_path=f.file_path,
            file_name=f.file_name,
            file_size=f.file_size,
            checksum=f.checksum,
            version_number=f.version_number,
            created_at=f.created_at,
            modified_at=f.modified_at,
            sync_status=f.sync_status,
            conflict_status=f.conflict_status,
            is_deleted=f.is_deleted
        )
        for f in files
    ]


@app.post("/files/upload", response_model=APIResponse)
async def upload_file(
    file: UploadFile = FastAPIFile(...),
    file_path: str = "",
    client_id: str = "",
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Upload a file."""
    if not file_path:
        file_path = file.filename
    
    if not client_id:
        client_id = generate_client_id()
    
    # Read file content
    file_content = await file.read()
    
    # Upload file
    uploaded_file = file_service.upload_file(
        db=db,
        user_id=str(current_user.user_id),
        file_path=file_path,
        file_name=file.filename,
        file_data=file_content,
        client_id=client_id
    )
    
    # Notify other clients via WebSocket
    await manager.broadcast(json.dumps({
        "type": "file_uploaded",
        "file_id": str(uploaded_file.file_id),
        "user_id": str(current_user.user_id),
        "file_path": file_path
    }))
    
    return APIResponse(
        success=True,
        message="File uploaded successfully",
        data={"file_id": str(uploaded_file.file_id)}
    )


@app.get("/files/{file_id}/download")
async def download_file(
    file_id: str,
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Download a file."""
    file_data = file_service.download_file(db, file_id, str(current_user.user_id))
    
    if not file_data:
        raise HTTPException(status_code=404, detail="File not found")
    
    # Get file info
    file_record = db.query(DBFile).filter(
        DBFile.file_id == file_id,
        DBFile.user_id == current_user.user_id
    ).first()
    
    if not file_record:
        raise HTTPException(status_code=404, detail="File not found")
    
    from fastapi.responses import Response
    return Response(
        content=file_data,
        media_type="application/octet-stream",
        headers={"Content-Disposition": f"attachment; filename={file_record.file_name}"}
    )


@app.delete("/files/{file_id}", response_model=APIResponse)
async def delete_file(
    file_id: str,
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Delete a file."""
    success = file_service.delete_file(db, file_id, str(current_user.user_id))
    
    if not success:
        raise HTTPException(status_code=404, detail="File not found")
    
    # Notify other clients
    await manager.broadcast(json.dumps({
        "type": "file_deleted",
        "file_id": file_id,
        "user_id": str(current_user.user_id)
    }))
    
    return APIResponse(success=True, message="File deleted successfully")


@app.get("/files/{file_id}/versions")
async def get_file_versions(
    file_id: str,
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Get file version history."""
    versions = file_service.get_file_versions(db, file_id, str(current_user.user_id))
    
    return [
        {
            "version_id": str(v.version_id),
            "version_number": v.version_number,
            "checksum": v.checksum,
            "created_at": v.created_at,
            "is_current_version": v.is_current_version,
            "file_size": v.file_size
        }
        for v in versions
    ]


# Synchronization endpoints
@app.get("/sync/changes", response_model=SyncChangesResponse)
async def get_sync_changes(
    since: Optional[datetime] = None,
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Get pending sync changes."""
    if not since:
        since = datetime.utcnow() - timedelta(days=1)
    
    # Get files modified since timestamp
    files = db.query(DBFile).filter(
        DBFile.user_id == current_user.user_id,
        DBFile.modified_at > since
    ).all()
    
    # Get sync events
    events = db.query(SyncEvent).filter(
        SyncEvent.user_id == current_user.user_id,
        SyncEvent.timestamp > since
    ).all()
    
    file_infos = [
        FileInfo(
            file_id=str(f.file_id),
            user_id=str(f.user_id),
            file_path=f.file_path,
            file_name=f.file_name,
            file_size=f.file_size,
            checksum=f.checksum,
            version_number=f.version_number,
            created_at=f.created_at,
            modified_at=f.modified_at,
            sync_status=f.sync_status,
            conflict_status=f.conflict_status,
            is_deleted=f.is_deleted
        )
        for f in files
    ]
    
    sync_events = [
        SharedSyncEvent(
            event_id=str(e.event_id),
            user_id=str(e.user_id),
            file_id=str(e.file_id) if e.file_id else None,
            event_type=e.event_type,
            timestamp=e.timestamp,
            client_id=e.client_id,
            sync_status=e.sync_status
        )
        for e in events
    ]
    
    return SyncChangesResponse(
        files=file_infos,
        events=sync_events,
        server_timestamp=datetime.utcnow()
    )


@app.post("/sync/heartbeat", response_model=APIResponse)
async def heartbeat(
    heartbeat_data: HeartbeatRequest,
    current_user: DBUser = Depends(get_current_user),
    db: Session = Depends(get_db_session)
):
    """Update client heartbeat."""
    # Update session heartbeat
    # This would need access to the session token
    return APIResponse(success=True, message="Heartbeat updated")


# WebSocket endpoint for real-time sync
@app.websocket("/ws/sync/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_id: str):
    """WebSocket endpoint for real-time synchronization."""
    await manager.connect(websocket, client_id)
    try:
        while True:
            data = await websocket.receive_text()
            # Process incoming sync messages
            message = json.loads(data)
            
            # Echo back for now - in production, this would handle sync events
            await manager.send_personal_message(
                json.dumps({"type": "ack", "message": "Received"}),
                client_id
            )
    except WebSocketDisconnect:
        manager.disconnect(client_id)


# Health check endpoint
@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "timestamp": datetime.utcnow()}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
