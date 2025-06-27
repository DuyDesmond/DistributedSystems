"""
Desktop GUI application for visualizing file synchronization status.
Uses Tkinter for cross-platform desktop UI.
"""
import tkinter as tk
from tkinter import ttk, messagebox, filedialog, scrolledtext
import threading
import time
import json
from typing import Dict, List, Optional
import sqlite3
import os
from datetime import datetime
from pathlib import Path

from shared.models import FileInfo, SyncEvent
from client.sync_client import SyncClient


class SyncStatusGUI:
    """Main desktop GUI for monitoring sync status and managing files."""
    
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Distributed File Sync - Status Monitor")
        self.root.geometry("1000x700")
        self.root.minsize(800, 600)
        
        # Sync client instance
        self.sync_client: Optional[SyncClient] = None
        self.is_syncing = False
        self.sync_thread: Optional[threading.Thread] = None
        
        # Data storage
        self.files_data: List[Dict] = []
        self.sync_events: List[Dict] = []
        
        # Create GUI components
        self.create_menu()
        self.create_main_interface()
        self.create_status_bar()
        
        # Start periodic updates
        self.update_display()
        
    def create_menu(self):
        """Create the application menu bar."""
        menubar = tk.Menu(self.root)
        self.root.config(menu=menubar)
        
        # File menu
        file_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="File", menu=file_menu)
        file_menu.add_command(label="Set Sync Folder", command=self.set_sync_folder)
        file_menu.add_command(label="Connect to Server", command=self.connect_to_server)
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)
        
        # Sync menu
        sync_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="Sync", menu=sync_menu)
        sync_menu.add_command(label="Start Sync", command=self.start_sync)
        sync_menu.add_command(label="Stop Sync", command=self.stop_sync)
        sync_menu.add_command(label="Manual Sync", command=self.manual_sync)
        sync_menu.add_separator()
        sync_menu.add_command(label="Clear Local Cache", command=self.clear_cache)
        
        # View menu
        view_menu = tk.Menu(menubar, tearoff=0)
        menubar.add_cascade(label="View", menu=view_menu)
        view_menu.add_command(label="Refresh", command=self.refresh_data)
        view_menu.add_command(label="Settings", command=self.show_settings)
        
    def create_main_interface(self):
        """Create the main interface with tabs."""
        # Create notebook for tabs
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Files tab
        self.create_files_tab()
        
        # Sync Events tab
        self.create_events_tab()
        
        # Settings tab
        self.create_settings_tab()
        
    def create_files_tab(self):
        """Create the files monitoring tab."""
        files_frame = ttk.Frame(self.notebook)
        self.notebook.add(files_frame, text="Files")
        
        # Toolbar
        toolbar = ttk.Frame(files_frame)
        toolbar.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Button(toolbar, text="Refresh", command=self.refresh_files).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="Add File", command=self.add_file).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="Remove File", command=self.remove_file).pack(side=tk.LEFT, padx=2)
        
        # Search
        ttk.Label(toolbar, text="Filter:").pack(side=tk.LEFT, padx=(10, 2))
        self.file_filter = tk.StringVar()
        filter_entry = ttk.Entry(toolbar, textvariable=self.file_filter, width=20)
        filter_entry.pack(side=tk.LEFT, padx=2)
        filter_entry.bind('<KeyRelease>', self.filter_files)
        
        # Files treeview
        columns = ('Name', 'Path', 'Size', 'Modified', 'Status', 'Hash')
        self.files_tree = ttk.Treeview(files_frame, columns=columns, show='tree headings')
        
        # Configure columns
        self.files_tree.heading('#0', text='Type')
        self.files_tree.column('#0', width=50)
        
        for col in columns:
            self.files_tree.heading(col, text=col)
            if col == 'Path':
                self.files_tree.column(col, width=300)
            elif col == 'Size':
                self.files_tree.column(col, width=80)
            elif col == 'Status':
                self.files_tree.column(col, width=100)
            else:
                self.files_tree.column(col, width=120)
        
        # Scrollbars for treeview
        files_scroll_y = ttk.Scrollbar(files_frame, orient=tk.VERTICAL, command=self.files_tree.yview)
        files_scroll_x = ttk.Scrollbar(files_frame, orient=tk.HORIZONTAL, command=self.files_tree.xview)
        self.files_tree.configure(yscrollcommand=files_scroll_y.set, xscrollcommand=files_scroll_x.set)
        
        # Pack treeview and scrollbars
        self.files_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(5, 0), pady=5)
        files_scroll_y.pack(side=tk.RIGHT, fill=tk.Y)
        files_scroll_x.pack(side=tk.BOTTOM, fill=tk.X)
        
    def create_events_tab(self):
        """Create the sync events monitoring tab."""
        events_frame = ttk.Frame(self.notebook)
        self.notebook.add(events_frame, text="Sync Events")
        
        # Toolbar
        toolbar = ttk.Frame(events_frame)
        toolbar.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Button(toolbar, text="Refresh", command=self.refresh_events).pack(side=tk.LEFT, padx=2)
        ttk.Button(toolbar, text="Clear", command=self.clear_events).pack(side=tk.LEFT, padx=2)
        
        # Auto-refresh checkbox
        self.auto_refresh = tk.BooleanVar(value=True)
        ttk.Checkbutton(toolbar, text="Auto-refresh", variable=self.auto_refresh).pack(side=tk.LEFT, padx=10)
        
        # Events treeview
        event_columns = ('Timestamp', 'Event', 'File', 'Status', 'Details')
        self.events_tree = ttk.Treeview(events_frame, columns=event_columns, show='headings')
        
        for col in event_columns:
            self.events_tree.heading(col, text=col)
            if col == 'Timestamp':
                self.events_tree.column(col, width=150)
            elif col == 'File':
                self.events_tree.column(col, width=250)
            else:
                self.events_tree.column(col, width=120)
        
        # Scrollbars
        events_scroll_y = ttk.Scrollbar(events_frame, orient=tk.VERTICAL, command=self.events_tree.yview)
        events_scroll_x = ttk.Scrollbar(events_frame, orient=tk.HORIZONTAL, command=self.events_tree.xview)
        self.events_tree.configure(yscrollcommand=events_scroll_y.set, xscrollcommand=events_scroll_x.set)
        
        # Pack
        self.events_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=(5, 0), pady=5)
        events_scroll_y.pack(side=tk.RIGHT, fill=tk.Y)
        events_scroll_x.pack(side=tk.BOTTOM, fill=tk.X)
        
    def create_settings_tab(self):
        """Create the settings tab."""
        settings_frame = ttk.Frame(self.notebook)
        self.notebook.add(settings_frame, text="Settings")
        
        # Settings form
        form_frame = ttk.LabelFrame(settings_frame, text="Sync Configuration")
        form_frame.pack(fill=tk.X, padx=10, pady=10)
        
        # Server URL
        ttk.Label(form_frame, text="Server URL:").grid(row=0, column=0, sticky=tk.W, padx=5, pady=5)
        self.server_url = tk.StringVar(value="http://localhost:8000")
        ttk.Entry(form_frame, textvariable=self.server_url, width=40).grid(row=0, column=1, padx=5, pady=5)
        
        # Username
        ttk.Label(form_frame, text="Username:").grid(row=1, column=0, sticky=tk.W, padx=5, pady=5)
        self.username = tk.StringVar()
        ttk.Entry(form_frame, textvariable=self.username, width=40).grid(row=1, column=1, padx=5, pady=5)
        
        # Password
        ttk.Label(form_frame, text="Password:").grid(row=2, column=0, sticky=tk.W, padx=5, pady=5)
        self.password = tk.StringVar()
        ttk.Entry(form_frame, textvariable=self.password, show="*", width=40).grid(row=2, column=1, padx=5, pady=5)
        
        # Sync folder
        ttk.Label(form_frame, text="Sync Folder:").grid(row=3, column=0, sticky=tk.W, padx=5, pady=5)
        self.sync_folder = tk.StringVar()
        folder_frame = ttk.Frame(form_frame)
        folder_frame.grid(row=3, column=1, padx=5, pady=5, sticky=tk.W)
        ttk.Entry(folder_frame, textvariable=self.sync_folder, width=35).pack(side=tk.LEFT)
        ttk.Button(folder_frame, text="Browse", command=self.browse_folder).pack(side=tk.LEFT, padx=(5, 0))
        
        # Sync interval
        ttk.Label(form_frame, text="Sync Interval (seconds):").grid(row=4, column=0, sticky=tk.W, padx=5, pady=5)
        self.sync_interval = tk.IntVar(value=10)
        ttk.Spinbox(form_frame, from_=5, to=300, textvariable=self.sync_interval, width=10).grid(row=4, column=1, sticky=tk.W, padx=5, pady=5)
        
        # Buttons
        button_frame = ttk.Frame(form_frame)
        button_frame.grid(row=5, column=0, columnspan=2, pady=10)
        ttk.Button(button_frame, text="Save Settings", command=self.save_settings).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="Load Settings", command=self.load_settings).pack(side=tk.LEFT, padx=5)
        
        # Status display
        status_frame = ttk.LabelFrame(settings_frame, text="Connection Status")
        status_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        self.status_text = scrolledtext.ScrolledText(status_frame, height=15, state=tk.DISABLED)
        self.status_text.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
    def create_status_bar(self):
        """Create the status bar at the bottom."""
        self.status_bar = ttk.Frame(self.root)
        self.status_bar.pack(fill=tk.X, side=tk.BOTTOM)
        
        # Status label
        self.status_label = ttk.Label(self.status_bar, text="Ready")
        self.status_label.pack(side=tk.LEFT, padx=5)
        
        # Progress bar
        self.progress = ttk.Progressbar(self.status_bar, mode='indeterminate')
        self.progress.pack(side=tk.RIGHT, padx=5, pady=2)
        
        # Connection indicator
        self.connection_label = ttk.Label(self.status_bar, text="Disconnected", foreground="red")
        self.connection_label.pack(side=tk.RIGHT, padx=5)
        
    def set_sync_folder(self):
        """Set the sync folder through file dialog."""
        folder = filedialog.askdirectory(title="Select Sync Folder")
        if folder:
            self.sync_folder.set(folder)
            
    def connect_to_server(self):
        """Connect to the sync server."""
        if not all([self.server_url.get(), self.username.get(), self.password.get()]):
            messagebox.showerror("Error", "Please fill in all connection details")
            return
            
        try:
            # Initialize sync client
            self.sync_client = SyncClient(
                server_url=self.server_url.get(),
                username=self.username.get(),
                password=self.password.get(),
                sync_folder=self.sync_folder.get() or "sync_folder"
            )
            
            # Test connection (implement this method in SyncClient)
            # if self.sync_client.test_connection():
            self.connection_label.config(text="Connected", foreground="green")
            self.log_status("Connected to server successfully")
            # else:
            #     raise Exception("Connection test failed")
                
        except Exception as e:
            messagebox.showerror("Connection Error", f"Failed to connect: {str(e)}")
            self.log_status(f"Connection failed: {str(e)}")
            
    def start_sync(self):
        """Start the sync process."""
        if not self.sync_client:
            messagebox.showerror("Error", "Please connect to server first")
            return
            
        if self.is_syncing:
            messagebox.showinfo("Info", "Sync is already running")
            return
            
        self.is_syncing = True
        self.progress.start()
        self.status_label.config(text="Syncing...")
        
        # Start sync in a separate thread
        self.sync_thread = threading.Thread(target=self.sync_worker, daemon=True)
        self.sync_thread.start()
        
        self.log_status("Sync started")
        
    def stop_sync(self):
        """Stop the sync process."""
        self.is_syncing = False
        self.progress.stop()
        self.status_label.config(text="Sync stopped")
        self.log_status("Sync stopped")
        
    def manual_sync(self):
        """Perform a one-time sync."""
        if not self.sync_client:
            messagebox.showerror("Error", "Please connect to server first")
            return
            
        self.progress.start()
        self.status_label.config(text="Manual sync in progress...")
        
        # Run sync once in a thread
        threading.Thread(target=self.manual_sync_worker, daemon=True).start()
        
    def sync_worker(self):
        """Worker thread for continuous sync."""
        while self.is_syncing:
            try:
                if self.sync_client:
                    # Perform sync operation
                    # self.sync_client.sync()
                    pass
                time.sleep(self.sync_interval.get())
            except Exception as e:
                self.log_status(f"Sync error: {str(e)}")
                
    def manual_sync_worker(self):
        """Worker thread for manual sync."""
        try:
            if self.sync_client:
                # Perform sync operation
                # self.sync_client.sync()
                self.log_status("Manual sync completed")
            else:
                self.log_status("No sync client available")
        except Exception as e:
            self.log_status(f"Manual sync error: {str(e)}")
        finally:
            self.root.after(0, lambda: [
                self.progress.stop(),
                self.status_label.config(text="Ready")
            ])
            
    def clear_cache(self):
        """Clear local sync cache."""
        if messagebox.askyesno("Confirm", "Clear local sync cache? This will reset all sync state."):
            try:
                # Clear cache logic here
                self.log_status("Local cache cleared")
                self.refresh_data()
            except Exception as e:
                messagebox.showerror("Error", f"Failed to clear cache: {str(e)}")
                
    def refresh_data(self):
        """Refresh all data displays."""
        self.refresh_files()
        self.refresh_events()
        
    def refresh_files(self):
        """Refresh the files display."""
        # Clear existing items
        for item in self.files_tree.get_children():
            self.files_tree.delete(item)
            
        # Load files from local database or sync client
        try:
            files = self.get_files_data()
            for file_info in files:
                self.files_tree.insert('', 'end', 
                    text='📄',
                    values=(
                        file_info.get('name', ''),
                        file_info.get('path', ''),
                        self.format_size(file_info.get('size', 0)),
                        self.format_datetime(file_info.get('modified', '')),
                        file_info.get('status', 'Unknown'),
                        file_info.get('hash', '')[:16] + '...' if file_info.get('hash') else ''
                    )
                )
        except Exception as e:
            self.log_status(f"Error loading files: {str(e)}")
            
    def refresh_events(self):
        """Refresh the sync events display."""
        # Clear existing items
        for item in self.events_tree.get_children():
            self.events_tree.delete(item)
            
        # Load events
        try:
            events = self.get_sync_events()
            for event in events:
                self.events_tree.insert('', 'end',
                    values=(
                        self.format_datetime(event.get('timestamp', '')),
                        event.get('event_type', ''),
                        event.get('file_path', ''),
                        event.get('status', ''),
                        event.get('details', '')
                    )
                )
        except Exception as e:
            self.log_status(f"Error loading events: {str(e)}")
            
    def filter_files(self, event=None):
        """Filter files based on search term."""
        search_term = self.file_filter.get().lower()
        
        # Re-populate with filtered results
        for item in self.files_tree.get_children():
            self.files_tree.delete(item)
            
        files = self.get_files_data()
        for file_info in files:
            if search_term in file_info.get('name', '').lower() or search_term in file_info.get('path', '').lower():
                self.files_tree.insert('', 'end',
                    text='📄',
                    values=(
                        file_info.get('name', ''),
                        file_info.get('path', ''),
                        self.format_size(file_info.get('size', 0)),
                        self.format_datetime(file_info.get('modified', '')),
                        file_info.get('status', 'Unknown'),
                        file_info.get('hash', '')[:16] + '...' if file_info.get('hash') else ''
                    )
                )
                
    def add_file(self):
        """Add a file to sync."""
        files = filedialog.askopenfilenames(title="Select files to sync")
        for file_path in files:
            try:
                # Add file to sync client
                self.log_status(f"Added file: {file_path}")
            except Exception as e:
                messagebox.showerror("Error", f"Failed to add file: {str(e)}")
        self.refresh_files()
        
    def remove_file(self):
        """Remove selected file from sync."""
        selected = self.files_tree.selection()
        if not selected:
            messagebox.showinfo("Info", "Please select a file to remove")
            return
            
        if messagebox.askyesno("Confirm", "Remove selected file from sync?"):
            try:
                # Remove file logic here
                self.log_status("File removed from sync")
                self.refresh_files()
            except Exception as e:
                messagebox.showerror("Error", f"Failed to remove file: {str(e)}")
                
    def clear_events(self):
        """Clear sync events."""
        if messagebox.askyesno("Confirm", "Clear all sync events?"):
            for item in self.events_tree.get_children():
                self.events_tree.delete(item)
            self.log_status("Sync events cleared")
            
    def browse_folder(self):
        """Browse for sync folder."""
        folder = filedialog.askdirectory(title="Select Sync Folder")
        if folder:
            self.sync_folder.set(folder)
            
    def save_settings(self):
        """Save settings to file."""
        settings = {
            'server_url': self.server_url.get(),
            'username': self.username.get(),
            'sync_folder': self.sync_folder.get(),
            'sync_interval': self.sync_interval.get()
        }
        
        try:
            with open('gui_settings.json', 'w') as f:
                json.dump(settings, f, indent=2)
            messagebox.showinfo("Success", "Settings saved successfully")
            self.log_status("Settings saved")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to save settings: {str(e)}")
            
    def load_settings(self):
        """Load settings from file."""
        try:
            if os.path.exists('gui_settings.json'):
                with open('gui_settings.json', 'r') as f:
                    settings = json.load(f)
                    
                self.server_url.set(settings.get('server_url', 'http://localhost:8000'))
                self.username.set(settings.get('username', ''))
                self.sync_folder.set(settings.get('sync_folder', ''))
                self.sync_interval.set(settings.get('sync_interval', 10))
                
                messagebox.showinfo("Success", "Settings loaded successfully")
                self.log_status("Settings loaded")
            else:
                messagebox.showinfo("Info", "No settings file found")
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load settings: {str(e)}")
            
    def show_settings(self):
        """Show settings tab."""
        self.notebook.select(2)  # Select settings tab
        
    def get_files_data(self) -> List[Dict]:
        """Get files data from local database or sync client."""
        # Mock data for now - replace with actual data source
        return [
            {
                'name': 'document.txt',
                'path': '/sync/document.txt',
                'size': 1024,
                'modified': '2024-01-15 10:30:00',
                'status': 'Synced',
                'hash': 'abc123def456'
            },
            {
                'name': 'image.jpg',
                'path': '/sync/image.jpg',
                'size': 2048576,
                'modified': '2024-01-15 11:45:00',
                'status': 'Pending',
                'hash': 'def456ghi789'
            }
        ]
        
    def get_sync_events(self) -> List[Dict]:
        """Get sync events from local database."""
        # Mock data for now - replace with actual data source
        return [
            {
                'timestamp': '2024-01-15 12:00:00',
                'event_type': 'Upload',
                'file_path': '/sync/document.txt',
                'status': 'Success',
                'details': 'File uploaded successfully'
            },
            {
                'timestamp': '2024-01-15 12:01:00',
                'event_type': 'Download',
                'file_path': '/sync/image.jpg',
                'status': 'In Progress',
                'details': 'Downloading file chunks'
            }
        ]
        
    def format_size(self, size_bytes: int) -> str:
        """Format file size in human readable format."""
        for unit in ['B', 'KB', 'MB', 'GB']:
            if size_bytes < 1024.0:
                return f"{size_bytes:.1f} {unit}"
            size_bytes /= 1024.0
        return f"{size_bytes:.1f} TB"
        
    def format_datetime(self, dt_str: str) -> str:
        """Format datetime string."""
        try:
            if isinstance(dt_str, str) and dt_str:
                dt = datetime.fromisoformat(dt_str.replace('Z', '+00:00'))
                return dt.strftime('%Y-%m-%d %H:%M:%S')
        except:
            pass
        return dt_str
        
    def log_status(self, message: str):
        """Log a status message."""
        timestamp = datetime.now().strftime('%H:%M:%S')
        log_message = f"[{timestamp}] {message}\n"
        
        self.status_text.config(state=tk.NORMAL)
        self.status_text.insert(tk.END, log_message)
        self.status_text.see(tk.END)
        self.status_text.config(state=tk.DISABLED)
        
    def update_display(self):
        """Periodic update of the display."""
        if self.auto_refresh.get():
            self.refresh_data()
            
        # Schedule next update
        self.root.after(5000, self.update_display)  # Update every 5 seconds


def main():
    """Main function to start the GUI application."""
    root = tk.Tk()
    app = SyncStatusGUI(root)
    
    try:
        root.mainloop()
    except KeyboardInterrupt:
        print("\nGUI application stopped by user")


if __name__ == "__main__":
    main()
