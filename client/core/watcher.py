from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from pathlib import Path
from datetime import datetime
import sys
import os

# Add parent directory to path for imports
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '..'))

from common.models import FileEvent, FileEventType
from utils.encryption import calculate_file_checksum

class FileSystemWatcher(FileSystemEventHandler):
    def __init__(self, watch_directory: str, callback_function):
        self.watch_directory = Path(watch_directory)
        self.callback = callback_function
        self.observer = Observer()
    
    def start_watching(self):
        """Start watching the directory"""
        self.observer.schedule(self, str(self.watch_directory), recursive=True)
        self.observer.start()
        print(f"Started watching directory: {self.watch_directory}")
    
    def stop_watching(self):
        """Stop watching the directory"""
        self.observer.stop()
        self.observer.join()
        print("Stopped watching directory")
    
    def on_modified(self, event):
        if not event.is_directory:
            self._handle_file_event(event.src_path, FileEventType.MODIFY)
    
    def on_created(self, event):
        if not event.is_directory:
            self._handle_file_event(event.src_path, FileEventType.CREATE)
    
    def on_deleted(self, event):
        if not event.is_directory:
            self._handle_file_event(event.src_path, FileEventType.DELETE)
    
    def _handle_file_event(self, file_path: str, event_type: FileEventType):
        """Handle a file system event"""
        try:
            # Convert to relative path
            relative_path = str(Path(file_path).relative_to(self.watch_directory))
            
            # Create file event
            if event_type == FileEventType.DELETE:
                file_event = FileEvent(
                    file_path=relative_path,
                    event_type=event_type,
                    timestamp=datetime.utcnow(),
                    user_id=""  # Will be set by sync manager
                )
            else:
                # Calculate checksum and size for non-delete events
                checksum = calculate_file_checksum(file_path)
                size = Path(file_path).stat().st_size
                
                file_event = FileEvent(
                    file_path=relative_path,
                    event_type=event_type,
                    checksum=checksum,
                    size=size,
                    timestamp=datetime.utcnow(),
                    user_id=""  # Will be set by sync manager
                )
            
            # Call the callback function
            self.callback(file_event)
            
        except Exception as e:
            print(f"Error handling file event for {file_path}: {e}")
    
    def is_watching(self) -> bool:
        """Check if observer is currently watching"""
        return self.observer.is_alive()
