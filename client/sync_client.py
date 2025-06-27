"""Synchronization client for communicating with the server."""

import os
import json
import time
import asyncio
import websockets
from datetime import datetime, timedelta
from typing import Optional, Dict, List, Any
from pathlib import Path
import requests
import threading
from concurrent.futures import ThreadPoolExecutor
from shared.models import FileInfo, SyncEvent, EventType, ClientConfig, APIResponse
from shared.utils import calculate_content_hash, generate_client_id
from client.file_watcher import FileWatcher


class SyncClient:
    """Main synchronization client."""
    
    def __init__(self, config: ClientConfig):
        self.config = config
        self.client_id = generate_client_id()
        self.access_token = None
        self.refresh_token = None
        self.websocket = None
        self.is_connected = False
        self.sync_in_progress = False
        
        # Initialize file watcher
        db_path = os.path.join(config.sync_folder, ".filesync", "client.db")
        os.makedirs(os.path.dirname(db_path), exist_ok=True)
        
        self.file_watcher = FileWatcher(config.sync_folder, db_path)
        self.file_watcher.add_event_callback(self.on_local_file_change)
        
        # Thread pool for concurrent operations
        self.executor = ThreadPoolExecutor(max_workers=5)
        
        # Sync thread control
        self.sync_thread = None
        self.stop_sync = threading.Event()
        
    def authenticate(self, username: str, password: str) -> bool:
        """Authenticate with the server."""
        try:
            response = requests.post(
                f"{self.config.server_url}/auth/login",
                json={"username": username, "password": password},
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                self.access_token = data["access_token"]
                self.refresh_token = data.get("refresh_token")
                print("Authentication successful")
                return True
            else:
                print(f"Authentication failed: {response.status_code}")
                return False
                
        except Exception as e:
            print(f"Authentication error: {e}")
            return False
    
    def refresh_access_token(self) -> bool:
        """Refresh the access token."""
        if not self.refresh_token:
            return False
        
        try:
            response = requests.post(
                f"{self.config.server_url}/auth/refresh",
                json={"refresh_token": self.refresh_token},
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                self.access_token = data["access_token"]
                return True
            else:
                return False
                
        except Exception as e:
            print(f"Token refresh error: {e}")
            return False
    
    def get_auth_headers(self) -> Dict[str, str]:
        """Get authentication headers for API requests."""
        return {
            "Authorization": f"Bearer {self.access_token}",
            "Content-Type": "application/json"
        }
    
    def on_local_file_change(self, event_type: EventType, file_path: str, file_info: Optional[Dict]):
        """Handle local file changes."""
        print(f"File change detected: {event_type.value} - {file_path}")
        
        # Add to sync queue (this is handled by file_watcher)
        # The sync loop will pick up these changes
    
    def upload_file(self, file_path: str) -> bool:
        """Upload a file to the server."""
        full_path = os.path.join(self.config.sync_folder, file_path)
        
        if not os.path.exists(full_path):
            print(f"File not found: {full_path}")
            return False
        
        try:
            with open(full_path, 'rb') as f:
                files = {'file': (os.path.basename(file_path), f, 'application/octet-stream')}
                data = {
                    'file_path': file_path,
                    'client_id': self.client_id
                }
                
                response = requests.post(
                    f"{self.config.server_url}/files/upload",
                    files=files,
                    data=data,
                    headers={"Authorization": f"Bearer {self.access_token}"},
                    timeout=30
                )
                
                if response.status_code == 200:
                    print(f"File uploaded successfully: {file_path}")
                    return True
                else:
                    print(f"Upload failed: {response.status_code} - {response.text}")
                    return False
                    
        except Exception as e:
            print(f"Upload error: {e}")
            return False
    
    def download_file(self, file_id: str, file_path: str) -> bool:
        """Download a file from the server."""
        try:
            response = requests.get(
                f"{self.config.server_url}/files/{file_id}/download",
                headers={"Authorization": f"Bearer {self.access_token}"},
                timeout=30
            )
            
            if response.status_code == 200:
                full_path = os.path.join(self.config.sync_folder, file_path)
                os.makedirs(os.path.dirname(full_path), exist_ok=True)
                
                with open(full_path, 'wb') as f:
                    f.write(response.content)
                
                print(f"File downloaded successfully: {file_path}")
                return True
            else:
                print(f"Download failed: {response.status_code}")
                return False
                
        except Exception as e:
            print(f"Download error: {e}")
            return False
    
    def delete_remote_file(self, file_id: str) -> bool:
        """Delete a file on the server."""
        try:
            response = requests.delete(
                f"{self.config.server_url}/files/{file_id}",
                headers=self.get_auth_headers(),
                timeout=10
            )
            
            return response.status_code == 200
            
        except Exception as e:
            print(f"Delete error: {e}")
            return False
    
    def get_server_changes(self, since: Optional[datetime] = None) -> Optional[Dict]:
        """Get changes from the server."""
        try:
            params = {}
            if since:
                params['since'] = since.isoformat()
            
            response = requests.get(
                f"{self.config.server_url}/sync/changes",
                params=params,
                headers=self.get_auth_headers(),
                timeout=10
            )
            
            if response.status_code == 200:
                return response.json()
            else:
                print(f"Failed to get server changes: {response.status_code}")
                return None
                
        except Exception as e:
            print(f"Get changes error: {e}")
            return None
    
    def send_heartbeat(self) -> bool:
        """Send heartbeat to server."""
        try:
            response = requests.post(
                f"{self.config.server_url}/sync/heartbeat",
                json={"client_id": self.client_id},
                headers=self.get_auth_headers(),
                timeout=5
            )
            
            return response.status_code == 200
            
        except Exception as e:
            print(f"Heartbeat error: {e}")
            return False
    
    async def connect_websocket(self):
        """Connect to WebSocket for real-time updates."""
        try:
            uri = f"{self.config.server_url.replace('http', 'ws')}/ws/sync/{self.client_id}"
            
            async with websockets.connect(uri) as websocket:
                self.websocket = websocket
                self.is_connected = True
                print("WebSocket connected")
                
                async for message in websocket:
                    try:
                        data = json.loads(message)
                        await self.handle_websocket_message(data)
                    except Exception as e:
                        print(f"WebSocket message error: {e}")
                        
        except Exception as e:
            print(f"WebSocket connection error: {e}")
            self.is_connected = False
    
    async def handle_websocket_message(self, data: Dict):
        """Handle incoming WebSocket messages."""
        message_type = data.get("type")
        
        if message_type == "file_uploaded":
            print(f"Remote file uploaded: {data.get('file_path')}")
            # Trigger sync to download new file
            self.trigger_sync()
        elif message_type == "file_deleted":
            print(f"Remote file deleted: {data.get('file_id')}")
            # Handle remote deletion
        elif message_type == "ack":
            print("Server acknowledged message")
    
    def trigger_sync(self):
        """Trigger a sync cycle."""
        if not self.sync_in_progress:
            threading.Thread(target=self.sync_once, daemon=True).start()
    
    def sync_once(self):
        """Perform one sync cycle."""
        if self.sync_in_progress:
            return
        
        self.sync_in_progress = True
        try:
            print("Starting sync cycle...")
            
            # 1. Upload local changes
            self.upload_local_changes()
            
            # 2. Download remote changes
            self.download_remote_changes()
            
            # 3. Send heartbeat
            self.send_heartbeat()
            
            print("Sync cycle completed")
            
        except Exception as e:
            print(f"Sync error: {e}")
        finally:
            self.sync_in_progress = False
    
    def upload_local_changes(self):
        """Upload pending local changes."""
        pending_events = self.file_watcher.get_pending_sync_events()
        
        for event in pending_events:
            try:
                event_type = event['event_type']
                file_path = event['file_path']
                
                if event_type == 'create' or event_type == 'modify':
                    success = self.upload_file(file_path)
                elif event_type == 'delete':
                    # Would need to track file_id for deletion
                    success = True  # Simplified for now
                else:
                    success = True
                
                if success:
                    self.file_watcher.mark_event_synced(event['id'])
                    
            except Exception as e:
                print(f"Error uploading change: {e}")
    
    def download_remote_changes(self):
        """Download changes from server."""
        # Get last sync time (simplified - would be stored)
        since = datetime.utcnow() - timedelta(hours=1)
        
        changes = self.get_server_changes(since)
        if not changes:
            return
        
        # Process file changes
        for file_info in changes.get('files', []):
            try:
                # Check if we need to download this file
                local_path = file_info['file_path']
                full_path = os.path.join(self.config.sync_folder, local_path)
                
                should_download = True
                
                if os.path.exists(full_path):
                    # Check if local file is different
                    with open(full_path, 'rb') as f:
                        local_checksum = calculate_content_hash(f.read())
                    
                    if local_checksum == file_info['checksum']:
                        should_download = False
                
                if should_download and not file_info['is_deleted']:
                    self.download_file(file_info['file_id'], local_path)
                elif file_info['is_deleted'] and os.path.exists(full_path):
                    os.remove(full_path)
                    print(f"Deleted local file: {local_path}")
                    
            except Exception as e:
                print(f"Error processing remote change: {e}")
    
    def start_sync_loop(self):
        """Start the continuous sync loop."""
        self.stop_sync.clear()
        self.sync_thread = threading.Thread(target=self._sync_loop, daemon=True)
        self.sync_thread.start()
        print("Sync loop started")
    
    def stop_sync_loop(self):
        """Stop the sync loop."""
        self.stop_sync.set()
        if self.sync_thread:
            self.sync_thread.join()
        print("Sync loop stopped")
    
    def _sync_loop(self):
        """Main sync loop."""
        while not self.stop_sync.is_set():
            try:
                self.sync_once()
                time.sleep(30)  # Sync every 30 seconds
            except Exception as e:
                print(f"Sync loop error: {e}")
                time.sleep(60)  # Wait longer on error
    
    def start(self, username: str, password: str):
        """Start the sync client."""
        print("Starting File Sync Client...")
        
        # Authenticate
        if not self.authenticate(username, password):
            print("Failed to authenticate")
            return False
        
        # Start file watcher
        self.file_watcher.start_watching()
        
        # Perform initial scan
        self.file_watcher.scan_folder()
        
        # Start sync loop
        self.start_sync_loop()
        
        # Start WebSocket connection (in background)
        def start_websocket():
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            loop.run_until_complete(self.connect_websocket())
        
        websocket_thread = threading.Thread(target=start_websocket, daemon=True)
        websocket_thread.start()
        
        print("File Sync Client started successfully")
        return True
    
    def stop(self):
        """Stop the sync client."""
        print("Stopping File Sync Client...")
        
        # Stop sync loop
        self.stop_sync_loop()
        
        # Stop file watcher
        self.file_watcher.stop_watching()
        
        # Close WebSocket
        self.is_connected = False
        
        print("File Sync Client stopped")


def main():
    """Main entry point for the client."""
    import argparse
    
    parser = argparse.ArgumentParser(description="File Sync Client")
    parser.add_argument("--server", default="http://localhost:8000", help="Server URL")
    parser.add_argument("--folder", default="./sync", help="Sync folder path")
    parser.add_argument("--username", required=True, help="Username")
    parser.add_argument("--password", required=True, help="Password")
    
    args = parser.parse_args()
    
    # Create config
    config = ClientConfig(
        server_url=args.server,
        sync_folder=args.folder
    )
    
    # Create and start client
    client = SyncClient(config)
    
    try:
        if client.start(args.username, args.password):
            print("Press Ctrl+C to stop...")
            while True:
                time.sleep(1)
        else:
            print("Failed to start client")
    except KeyboardInterrupt:
        print("\nShutting down...")
    finally:
        client.stop()


if __name__ == "__main__":
    main()
