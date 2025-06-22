#!/usr/bin/env python3
"""
File Synchronization Client
Main entry point for the client application
"""

import asyncio
import argparse
import os
import sys
from pathlib import Path

# Add parent directory to path for imports
sys.path.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))

from core.watcher import FileSystemWatcher
from core.sync_manager import ClientSyncManager
from storage.local_db import LocalDatabase
from common.constants import SYNC_INTERVAL

class FileSyncClient:
    def __init__(self, watch_directory: str, server_url: str, username: str, password: str):
        self.watch_directory = Path(watch_directory)
        self.server_url = server_url
        self.username = username
        self.password = password
        
        # Initialize components
        self.local_db = LocalDatabase()
        self.sync_manager = ClientSyncManager(
            self.local_db, 
            self.server_url, 
            self.username, 
            self.password
        )
        self.watcher = FileSystemWatcher(
            str(self.watch_directory),
            self.sync_manager.handle_local_file_event
        )
        
    async def start(self):
        """Start the client application"""
        print(f"Starting File Sync Client")
        print(f"Watch directory: {self.watch_directory}")
        print(f"Server URL: {self.server_url}")
        
        # Authenticate with server
        if not await self.sync_manager.authenticate():
            print("Authentication failed. Exiting.")
            return
            
        print("Authentication successful")
        
        # Start file system watcher
        self.watcher.start_watching()
        
        # Start sync manager
        await self.sync_manager.start()
        
        # Keep the client running
        try:
            while True:
                await asyncio.sleep(SYNC_INTERVAL)
                await self.sync_manager.periodic_sync()
        except KeyboardInterrupt:
            print("\nShutting down client...")
            await self.stop()
    
    async def stop(self):
        """Stop the client application"""
        self.watcher.stop_watching()
        await self.sync_manager.stop()
        print("Client stopped")

async def main():
    parser = argparse.ArgumentParser(description='File Synchronization Client')
    parser.add_argument('--watch-dir', required=True, help='Directory to watch for changes')
    parser.add_argument('--server-url', default='http://localhost:8000', help='Server URL')
    parser.add_argument('--username', required=True, help='Username for authentication')
    parser.add_argument('--password', required=True, help='Password for authentication')
    
    args = parser.parse_args()
    
    # Validate watch directory
    if not os.path.exists(args.watch_dir):
        print(f"Error: Watch directory {args.watch_dir} does not exist")
        return
    
    # Create and start client
    client = FileSyncClient(
        args.watch_dir,
        args.server_url,
        args.username,
        args.password
    )
    
    await client.start()

if __name__ == "__main__":
    asyncio.run(main())
