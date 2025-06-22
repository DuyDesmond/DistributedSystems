"""
Client Sync Manager
Handles synchronization between local files and server
"""

import asyncio
import aiohttp
import websockets
import json
from typing import Optional, Dict, List
from datetime import datetime
from pathlib import Path
import sys
import os

# Add parent directory to path for imports
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '..'))

from common.models import FileEvent, FileEventType, FileMetadata
from storage.local_db import LocalDatabase
from utils.encryption import ClientEncryption, calculate_file_checksum


class ClientSyncManager:
    def __init__(self, local_db: LocalDatabase, server_url: str, username: str, password: str):
        self.local_db = local_db
        self.server_url = server_url
        self.username = username
        self.password = password
        self.access_token: Optional[str] = None
        self.encryption = ClientEncryption()
        self.websocket: Optional[websockets.WebSocketServerProtocol] = None
        self.user_id: Optional[str] = None
        self.session: Optional[aiohttp.ClientSession] = None
        
    async def start(self):
        """Start the sync manager"""
        self.session = aiohttp.ClientSession()
        await self.connect_websocket()
        
    async def stop(self):
        """Stop the sync manager"""
        if self.websocket:
            await self.websocket.close()
        if self.session:
            await self.session.close()
            
    async def authenticate(self) -> bool:
        """Authenticate with the server"""
        async with aiohttp.ClientSession() as session:
            try:
                async with session.post(
                    f"{self.server_url}/api/v1/auth/login",
                    json={"username": self.username, "password": self.password}
                ) as response:
                    if response.status == 200:
                        data = await response.json()
                        self.access_token = data["access_token"]
                        self.user_id = self.username  # Simplified for this implementation
                        return True
                    else:
                        print(f"Authentication failed: {response.status}")
                        return False
            except Exception as e:
                print(f"Authentication error: {e}")
                return False
                
    async def connect_websocket(self):
        """Connect to server WebSocket for real-time updates"""
        if not self.access_token or not self.user_id:
            print("Cannot connect WebSocket: not authenticated")
            return
            
        try:
            ws_url = f"{self.server_url.replace('http', 'ws')}/ws/{self.user_id}"
            self.websocket = await websockets.connect(ws_url)
            asyncio.create_task(self.websocket_listener())
            print("WebSocket connected")
        except Exception as e:
            print(f"WebSocket connection failed: {e}")
            
    async def websocket_listener(self):
        """Listen for WebSocket messages from server"""
        try:
            async for message in self.websocket:
                data = json.loads(message)
                if data.get("type") == "file_event":
                    await self.handle_remote_file_event(FileEvent(**data["data"]))
                elif data.get("type") == "ping":
                    await self.websocket.send(json.dumps({"type": "pong"}))
        except websockets.exceptions.ConnectionClosed:
            print("WebSocket connection closed")
        except Exception as e:
            print(f"WebSocket error: {e}")
            
    async def handle_local_file_event(self, event: FileEvent):
        """Handle file event detected locally"""
        print(f"Local file event: {event.event_type} - {event.file_path}")
        
        # Set user ID
        event.user_id = self.user_id or ""
        
        # Store in local database
        if event.event_type == FileEventType.DELETE:
            self.local_db.mark_file_deleted(event.file_path)
        else:
            # Create file metadata
            file_metadata = FileMetadata(
                id=event.id,
                path=event.file_path,
                checksum=event.checksum or "",
                size=event.size or 0,
                owner_id=event.user_id,
                version_vector=event.version_vector,
                modified_at=event.timestamp,
                is_deleted=False
            )
            self.local_db.store_file_metadata(file_metadata)
        
        # Send to server
        await self.send_event_to_server(event)
        
    async def handle_remote_file_event(self, event: FileEvent):
        """Handle file event received from server"""
        print(f"Remote file event: {event.event_type} - {event.file_path}")
        
        # Don't process our own events
        if event.user_id == self.user_id:
            return
            
        # Apply changes locally
        if event.event_type == FileEventType.DELETE:
            await self.delete_local_file(event.file_path)
        else:
            await self.download_and_apply_file(event)
            
    async def send_event_to_server(self, event: FileEvent):
        """Send file event to server"""
        if not self.session or not self.access_token:
            print("Cannot send event: not authenticated")
            return
            
        try:
            headers = {"Authorization": f"Bearer {self.access_token}"}
            
            if event.event_type in [FileEventType.CREATE, FileEventType.MODIFY]:
                # Upload file content
                await self.upload_file_content(event)
            
            # Send event metadata
            async with self.session.post(
                f"{self.server_url}/api/v1/sync/event",
                json=event.dict(),
                headers=headers
            ) as response:
                if response.status == 200:
                    print(f"Event sent successfully: {event.file_path}")
                else:
                    print(f"Failed to send event: {response.status}")
                    
        except Exception as e:
            print(f"Error sending event to server: {e}")
            
    async def upload_file_content(self, event: FileEvent):
        """Upload file content to server"""
        if not self.session or not self.access_token:
            return
            
        try:
            file_path = Path(event.file_path)
            if not file_path.exists():
                return
                
            headers = {"Authorization": f"Bearer {self.access_token}"}
            
            with open(file_path, 'rb') as f:
                data = aiohttp.FormData()
                data.add_field('file', f, filename=file_path.name)
                
                async with self.session.post(
                    f"{self.server_url}/api/v1/files/upload",
                    data=data,
                    headers=headers
                ) as response:
                    if response.status == 200:
                        print(f"File uploaded: {event.file_path}")
                    else:
                        print(f"File upload failed: {response.status}")
                        
        except Exception as e:
            print(f"Error uploading file: {e}")
            
    async def download_and_apply_file(self, event: FileEvent):
        """Download file from server and apply locally"""
        if not self.session or not self.access_token:
            return
            
        try:
            headers = {"Authorization": f"Bearer {self.access_token}"}
            
            async with self.session.get(
                f"{self.server_url}/api/v1/files/download/{event.id}",
                headers=headers
            ) as response:
                if response.status == 200:
                    file_data = await response.read()
                    
                    # Save file locally
                    file_path = Path(event.file_path)
                    file_path.parent.mkdir(parents=True, exist_ok=True)
                    
                    with open(file_path, 'wb') as f:
                        f.write(file_data)
                    
                    # Update local database
                    file_metadata = FileMetadata(
                        id=event.id,
                        path=event.file_path,
                        checksum=event.checksum or "",
                        size=event.size or 0,
                        owner_id=event.user_id,
                        version_vector=event.version_vector,
                        modified_at=event.timestamp,
                        is_deleted=False
                    )
                    self.local_db.store_file_metadata(file_metadata)
                    
                    print(f"File downloaded and applied: {event.file_path}")
                else:
                    print(f"File download failed: {response.status}")
                    
        except Exception as e:
            print(f"Error downloading file: {e}")
            
    async def delete_local_file(self, file_path: str):
        """Delete a local file"""
        try:
            local_path = Path(file_path)
            if local_path.exists():
                local_path.unlink()
                print(f"Local file deleted: {file_path}")
            
            self.local_db.mark_file_deleted(file_path)
            
        except Exception as e:
            print(f"Error deleting local file: {e}")
            
    async def periodic_sync(self):
        """Perform periodic synchronization with server"""
        if not self.session or not self.access_token:
            return
            
        try:
            # Get changes from server
            headers = {"Authorization": f"Bearer {self.access_token}"}
            
            # Get last sync timestamp
            last_sync = self.local_db.get_sync_state("last_sync")
            params = {"since": last_sync} if last_sync else {}
            
            async with self.session.get(
                f"{self.server_url}/api/v1/sync/changes",
                params=params,
                headers=headers
            ) as response:
                if response.status == 200:
                    data = await response.json()
                    changes = data.get("changes", [])
                    
                    for change in changes:
                        event = FileEvent(
                            id=change["id"],
                            file_path=change["path"],
                            event_type=FileEventType.DELETE if change["is_deleted"] else FileEventType.MODIFY,
                            checksum=change["checksum"],
                            size=change["size"],
                            timestamp=datetime.fromisoformat(change["modified_at"]),
                            user_id="",
                            version_vector=change["version_vector"]
                        )
                        await self.handle_remote_file_event(event)
                    
                    # Update last sync timestamp
                    self.local_db.set_sync_state("last_sync", datetime.utcnow().isoformat())
                    
        except Exception as e:
            print(f"Error during periodic sync: {e}")
