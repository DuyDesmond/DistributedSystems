#!/usr/bin/env python3
"""Command-line interface for File Sync management."""

import click
import requests
import json
import os
import sys
from pathlib import Path
from datetime import datetime
from typing import Optional


@click.group()
@click.option('--server', default='http://localhost:8000', help='Server URL')
@click.pass_context
def cli(ctx, server):
    """File Sync CLI Management Tool."""
    ctx.ensure_object(dict)
    ctx.obj['server'] = server


@cli.group()
@click.pass_context
def user(ctx):
    """User management commands."""
    pass


@user.command()
@click.option('--username', prompt=True, help='Username')
@click.option('--email', prompt=True, help='Email address')
@click.option('--password', prompt=True, hide_input=True, help='Password')
@click.pass_context
def create(ctx, username, email, password):
    """Create a new user."""
    server = ctx.obj['server']
    
    data = {
        'username': username,
        'email': email,
        'password': password
    }
    
    try:
        response = requests.post(f"{server}/auth/register", json=data)
        
        if response.status_code == 200:
            result = response.json()
            click.echo(f"✓ User created successfully!")
            click.echo(f"User ID: {result['data']['user_id']}")
        else:
            click.echo(f"✗ Failed to create user: {response.status_code}")
            if response.text:
                click.echo(response.text)
                
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@user.command()
@click.option('--username', prompt=True, help='Username')
@click.option('--password', prompt=True, hide_input=True, help='Password')
@click.pass_context
def login(ctx, username, password):
    """Login and get access token."""
    server = ctx.obj['server']
    
    data = {
        'username': username,
        'password': password
    }
    
    try:
        response = requests.post(f"{server}/auth/login", json=data)
        
        if response.status_code == 200:
            result = response.json()
            token = result['access_token']
            
            # Save token to file for other commands
            token_file = Path.home() / '.filesync_token'
            with open(token_file, 'w') as f:
                f.write(token)
            
            click.echo("✓ Login successful!")
            click.echo(f"Access token saved to {token_file}")
        else:
            click.echo(f"✗ Login failed: {response.status_code}")
            
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@cli.group()
@click.pass_context
def files(ctx):
    """File management commands."""
    pass


def get_auth_headers() -> Optional[dict]:
    """Get authentication headers from saved token."""
    token_file = Path.home() / '.filesync_token'
    
    if not token_file.exists():
        click.echo("✗ Not logged in. Run 'user login' first.")
        return None
    
    with open(token_file, 'r') as f:
        token = f.read().strip()
    
    return {'Authorization': f'Bearer {token}'}


@files.command()
@click.pass_context
def list(ctx):
    """List user files."""
    server = ctx.obj['server']
    headers = get_auth_headers()
    
    if not headers:
        return
    
    try:
        response = requests.get(f"{server}/files/", headers=headers)
        
        if response.status_code == 200:
            files_list = response.json()
            
            if not files_list:
                click.echo("No files found.")
                return
            
            click.echo(f"{'File Name':<30} {'Size':<10} {'Modified':<20} {'Status':<12}")
            click.echo("-" * 80)
            
            for file_info in files_list:
                name = file_info['file_name'][:29]
                size = f"{file_info['file_size']:,} B"
                modified = datetime.fromisoformat(file_info['modified_at'].replace('Z', '+00:00')).strftime('%Y-%m-%d %H:%M')
                status = file_info['sync_status']
                
                click.echo(f"{name:<30} {size:<10} {modified:<20} {status:<12}")
                
        else:
            click.echo(f"✗ Failed to list files: {response.status_code}")
            
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@files.command()
@click.argument('file_path', type=click.Path(exists=True))
@click.option('--remote-path', help='Remote path (default: same as local)')
@click.pass_context
def upload(ctx, file_path, remote_path):
    """Upload a file."""
    server = ctx.obj['server']
    headers = get_auth_headers()
    
    if not headers:
        return
    
    if not remote_path:
        remote_path = os.path.basename(file_path)
    
    try:
        with open(file_path, 'rb') as f:
            files = {'file': (os.path.basename(file_path), f, 'application/octet-stream')}
            data = {'file_path': remote_path, 'client_id': 'cli_client'}
            
            # Remove Content-Type from headers for file upload
            upload_headers = {k: v for k, v in headers.items() if k != 'Content-Type'}
            
            response = requests.post(
                f"{server}/files/upload",
                files=files,
                data=data,
                headers=upload_headers
            )
            
            if response.status_code == 200:
                result = response.json()
                click.echo("✓ File uploaded successfully!")
                click.echo(f"File ID: {result['data']['file_id']}")
            else:
                click.echo(f"✗ Upload failed: {response.status_code}")
                if response.text:
                    click.echo(response.text)
                    
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@files.command()
@click.argument('file_id')
@click.option('--output', '-o', help='Output file path')
@click.pass_context
def download(ctx, file_id, output):
    """Download a file."""
    server = ctx.obj['server']
    headers = get_auth_headers()
    
    if not headers:
        return
    
    try:
        response = requests.get(f"{server}/files/{file_id}/download", headers=headers)
        
        if response.status_code == 200:
            if not output:
                # Try to get filename from Content-Disposition header
                content_disposition = response.headers.get('Content-Disposition', '')
                if 'filename=' in content_disposition:
                    output = content_disposition.split('filename=')[1].strip('"')
                else:
                    output = f"downloaded_file_{file_id}"
            
            with open(output, 'wb') as f:
                f.write(response.content)
            
            click.echo(f"✓ File downloaded: {output}")
        else:
            click.echo(f"✗ Download failed: {response.status_code}")
            
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@cli.group()
@click.pass_context
def sync(ctx):
    """Synchronization commands."""
    pass


@sync.command()
@click.pass_context
def status(ctx):
    """Get sync status."""
    server = ctx.obj['server']
    headers = get_auth_headers()
    
    if not headers:
        return
    
    try:
        response = requests.get(f"{server}/sync/changes", headers=headers)
        
        if response.status_code == 200:
            data = response.json()
            
            click.echo(f"Server timestamp: {data['server_timestamp']}")
            click.echo(f"Files: {len(data['files'])}")
            click.echo(f"Events: {len(data['events'])}")
            
            if data['files']:
                click.echo("\nRecent files:")
                for file_info in data['files'][:5]:  # Show first 5
                    click.echo(f"  - {file_info['file_name']} ({file_info['sync_status']})")
                    
        else:
            click.echo(f"✗ Failed to get sync status: {response.status_code}")
            
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@cli.command()
@click.pass_context
def health(ctx):
    """Check server health."""
    server = ctx.obj['server']
    
    try:
        response = requests.get(f"{server}/health", timeout=5)
        
        if response.status_code == 200:
            data = response.json()
            click.echo("✓ Server is healthy")
            click.echo(f"Status: {data['status']}")
            click.echo(f"Timestamp: {data['timestamp']}")
        else:
            click.echo(f"✗ Server health check failed: {response.status_code}")
            
    except Exception as e:
        click.echo(f"✗ Server is not reachable: {e}")


@cli.command()
@click.argument('username')
@click.argument('password')
@click.option('--folder', default='./sync', help='Sync folder')
@click.pass_context
def start_client(ctx, username, password, folder):
    """Start the sync client."""
    server = ctx.obj['server']
    
    click.echo(f"Starting sync client...")
    click.echo(f"Server: {server}")
    click.echo(f"Sync folder: {folder}")
    click.echo(f"Username: {username}")
    
    try:
        # Import and run the sync client
        import sys
        sys.path.append('.')
        
        from shared.models import ClientConfig
        from client.sync_client import SyncClient
        
        config = ClientConfig(
            server_url=server,
            sync_folder=folder
        )
        
        client = SyncClient(config)
        
        if client.start(username, password):
            click.echo("✓ Client started successfully")
            click.echo("Press Ctrl+C to stop...")
            
            try:
                import time
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                click.echo("\nShutting down...")
                client.stop()
        else:
            click.echo("✗ Failed to start client")
            
    except Exception as e:
        click.echo(f"✗ Error: {e}")


@cli.command()
def gui():
    """Launch the desktop GUI interface."""
    try:
        import tkinter as tk
        from gui.sync_gui import main as gui_main
        click.echo("Starting desktop GUI...")
        gui_main()
    except ImportError as e:
        if "tkinter" in str(e):
            click.echo("Error: Tkinter is not available. Please install tkinter:")
            click.echo("- On Ubuntu/Debian: sudo apt-get install python3-tk")
            click.echo("- On Windows: Tkinter should be included with Python")
            click.echo("- On macOS: Tkinter should be included with Python")
        else:
            click.echo(f"Error importing GUI: {e}")
        sys.exit(1)
    except Exception as e:
        click.echo(f"Error starting GUI: {e}")
        sys.exit(1)


if __name__ == '__main__':
    cli()
