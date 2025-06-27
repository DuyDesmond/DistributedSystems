"""File system watcher for monitoring local file changes."""

import os
import time
import hashlib
from pathlib import Path
from typing import Dict, Set, Callable, Optional
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler, FileModifiedEvent, FileCreatedEvent, FileDeletedEvent, FileMovedEvent
from shared.models import EventType, SyncEvent
from shared.utils import calculate_content_hash, generate_client_id
from datetime import datetime
import sqlite3
import threading


class LocalFileState:
    """Manages local file state using SQLite."""
    
    def __init__(self, db_path: str):
        self.db_path = db_path
        self.init_database()
    
    def init_database(self):
        """Initialize SQLite database for local state."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS file_state (
                file_path TEXT PRIMARY KEY,
                checksum TEXT NOT NULL,
                file_size INTEGER NOT NULL,
                modified_time REAL NOT NULL,
                sync_status TEXT DEFAULT 'pending',
                last_sync_time REAL
            )
        """)
        
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                event_type TEXT NOT NULL,
                timestamp REAL NOT NULL,
                status TEXT DEFAULT 'pending'
            )
        """)
        
        conn.commit()
        conn.close()
    
    def update_file_state(self, file_path: str, checksum: str, file_size: int, modified_time: float):
        """Update file state in database."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("""
            INSERT OR REPLACE INTO file_state 
            (file_path, checksum, file_size, modified_time, sync_status)
            VALUES (?, ?, ?, ?, 'pending')
        """, (file_path, checksum, file_size, modified_time))
        
        conn.commit()
        conn.close()
    
    def get_file_state(self, file_path: str) -> Optional[Dict]:
        """Get file state from database."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT checksum, file_size, modified_time, sync_status, last_sync_time
            FROM file_state WHERE file_path = ?
        """, (file_path,))
        
        result = cursor.fetchone()
        conn.close()
        
        if result:
            return {
                'checksum': result[0],
                'file_size': result[1],
                'modified_time': result[2],
                'sync_status': result[3],
                'last_sync_time': result[4]
            }
        return None
    
    def add_to_sync_queue(self, file_path: str, event_type: str):
        """Add file event to sync queue."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("""
            INSERT INTO sync_queue (file_path, event_type, timestamp)
            VALUES (?, ?, ?)
        """, (file_path, event_type, time.time()))
        
        conn.commit()
        conn.close()
    
    def get_pending_sync_events(self) -> list:
        """Get pending sync events from queue."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT id, file_path, event_type, timestamp
            FROM sync_queue WHERE status = 'pending'
            ORDER BY timestamp
        """)
        
        results = cursor.fetchall()
        conn.close()
        
        return [
            {
                'id': row[0],
                'file_path': row[1],
                'event_type': row[2],
                'timestamp': row[3]
            }
            for row in results
        ]
    
    def mark_sync_event_completed(self, event_id: int):
        """Mark sync event as completed."""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        cursor.execute("""
            UPDATE sync_queue SET status = 'completed'
            WHERE id = ?
        """, (event_id,))
        
        conn.commit()
        conn.close()


class FileWatcherHandler(FileSystemEventHandler):
    """Handles file system events from watchdog."""
    
    def __init__(self, sync_folder: str, file_state: LocalFileState, callback: Callable):
        self.sync_folder = Path(sync_folder)
        self.file_state = file_state
        self.callback = callback
        self.ignore_patterns = {'.tmp', '.swp', '~', '.git', '.DS_Store', 'Thumbs.db'}
        self._processing_events = set()
        self._lock = threading.Lock()
    
    def should_ignore_file(self, file_path: str) -> bool:
        """Check if file should be ignored."""
        path = Path(file_path)
        
        # Ignore temporary files and system files
        for pattern in self.ignore_patterns:
            if pattern in path.name or path.name.endswith(pattern):
                return True
        
        # Ignore hidden files and directories
        for part in path.parts:
            if part.startswith('.') and part not in ['.', '..']:
                return True
        
        return False
    
    def get_relative_path(self, absolute_path: str) -> str:
        """Get path relative to sync folder."""
        try:
            return os.path.relpath(absolute_path, self.sync_folder)
        except ValueError:
            return absolute_path
    
    def calculate_file_info(self, file_path: str) -> Optional[Dict]:
        """Calculate file checksum and size."""
        try:
            if not os.path.exists(file_path) or not os.path.isfile(file_path):
                return None
            
            stat = os.stat(file_path)
            with open(file_path, 'rb') as f:
                content = f.read()
                checksum = hashlib.sha256(content).hexdigest()
            
            return {
                'checksum': checksum,
                'file_size': stat.st_size,
                'modified_time': stat.st_mtime
            }
        except Exception:
            return None
    
    def process_file_event(self, event_type: EventType, file_path: str):
        """Process a file system event."""
        if self.should_ignore_file(file_path):
            return
        
        relative_path = self.get_relative_path(file_path)
        
        with self._lock:
            # Avoid duplicate processing
            event_key = f"{event_type}:{relative_path}"
            if event_key in self._processing_events:
                return
            self._processing_events.add(event_key)
        
        try:
            if event_type == EventType.DELETE:
                # File deleted
                self.file_state.add_to_sync_queue(relative_path, event_type.value)
                self.callback(event_type, relative_path, None)
            else:
                # File created or modified
                file_info = self.calculate_file_info(file_path)
                if file_info:
                    # Check if file actually changed
                    current_state = self.file_state.get_file_state(relative_path)
                    
                    if (not current_state or 
                        current_state['checksum'] != file_info['checksum'] or
                        current_state['file_size'] != file_info['file_size']):
                        
                        # File is new or changed
                        self.file_state.update_file_state(
                            relative_path,
                            file_info['checksum'],
                            file_info['file_size'],
                            file_info['modified_time']
                        )
                        
                        self.file_state.add_to_sync_queue(relative_path, event_type.value)
                        self.callback(event_type, relative_path, file_info)
        finally:
            with self._lock:
                self._processing_events.discard(event_key)
    
    def on_created(self, event):
        """Handle file creation events."""
        if not event.is_directory:
            # Small delay to ensure file is fully written
            time.sleep(0.1)
            self.process_file_event(EventType.CREATE, event.src_path)
    
    def on_modified(self, event):
        """Handle file modification events."""
        if not event.is_directory:
            # Small delay to ensure file is fully written
            time.sleep(0.1)
            self.process_file_event(EventType.MODIFY, event.src_path)
    
    def on_deleted(self, event):
        """Handle file deletion events."""
        if not event.is_directory:
            self.process_file_event(EventType.DELETE, event.src_path)
    
    def on_moved(self, event):
        """Handle file move events."""
        if not event.is_directory:
            # Treat as delete + create
            self.process_file_event(EventType.DELETE, event.src_path)
            time.sleep(0.1)
            self.process_file_event(EventType.CREATE, event.dest_path)


class FileWatcher:
    """Main file watcher class."""
    
    def __init__(self, sync_folder: str, db_path: str):
        self.sync_folder = sync_folder
        self.file_state = LocalFileState(db_path)
        self.observer = Observer()
        self.is_watching = False
        self.event_callbacks = []
    
    def add_event_callback(self, callback: Callable):
        """Add callback for file events."""
        self.event_callbacks.append(callback)
    
    def _notify_callbacks(self, event_type: EventType, file_path: str, file_info: Optional[Dict]):
        """Notify all registered callbacks."""
        for callback in self.event_callbacks:
            try:
                callback(event_type, file_path, file_info)
            except Exception as e:
                print(f"Error in callback: {e}")
    
    def start_watching(self):
        """Start watching the sync folder."""
        if self.is_watching:
            return
        
        # Ensure sync folder exists
        os.makedirs(self.sync_folder, exist_ok=True)
        
        # Set up event handler
        event_handler = FileWatcherHandler(
            self.sync_folder,
            self.file_state,
            self._notify_callbacks
        )
        
        # Schedule the observer
        self.observer.schedule(event_handler, self.sync_folder, recursive=True)
        self.observer.start()
        self.is_watching = True
        
        print(f"Started watching: {self.sync_folder}")
    
    def stop_watching(self):
        """Stop watching the sync folder."""
        if self.is_watching:
            self.observer.stop()
            self.observer.join()
            self.is_watching = False
            print("Stopped watching")
    
    def scan_folder(self) -> Dict[str, Dict]:
        """Perform initial scan of sync folder."""
        print("Scanning sync folder...")
        files_info = {}
        
        if not os.path.exists(self.sync_folder):
            return files_info
        
        for root, dirs, files in os.walk(self.sync_folder):
            # Skip hidden directories
            dirs[:] = [d for d in dirs if not d.startswith('.')]
            
            for file_name in files:
                file_path = os.path.join(root, file_name)
                relative_path = os.path.relpath(file_path, self.sync_folder)
                
                if not FileWatcherHandler(self.sync_folder, self.file_state, lambda: None).should_ignore_file(file_path):
                    handler = FileWatcherHandler(self.sync_folder, self.file_state, lambda: None)
                    file_info = handler.calculate_file_info(file_path)
                    
                    if file_info:
                        files_info[relative_path] = file_info
                        
                        # Update file state
                        self.file_state.update_file_state(
                            relative_path,
                            file_info['checksum'],
                            file_info['file_size'],
                            file_info['modified_time']
                        )
        
        print(f"Found {len(files_info)} files")
        return files_info
    
    def get_pending_sync_events(self) -> list:
        """Get pending sync events."""
        return self.file_state.get_pending_sync_events()
    
    def mark_event_synced(self, event_id: int):
        """Mark sync event as completed."""
        self.file_state.mark_sync_event_completed(event_id)
