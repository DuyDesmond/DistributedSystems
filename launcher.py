#!/usr/bin/env python3
"""
File Synchronization System - Launcher Script
Provides easy commands to run server, client, or tests
"""

import argparse
import asyncio
import subprocess
import sys
import os
from pathlib import Path

def run_server():
    """Start the file sync server"""
    print("Starting File Sync Server...")
    os.chdir(Path(__file__).parent)
    subprocess.run([sys.executable, "server/main.py"])

def run_client(args):
    """Start a file sync client"""
    print(f"Starting File Sync Client...")
    print(f"Watch directory: {args.watch_dir}")
    print(f"Server: {args.server_url}")
    
    os.chdir(Path(__file__).parent)
    cmd = [
        sys.executable, "client/main.py",
        "--watch-dir", args.watch_dir,
        "--server-url", args.server_url,
        "--username", args.username,
        "--password", args.password
    ]
    subprocess.run(cmd)

def run_setup():
    """Run database setup"""
    print("Running database setup...")
    os.chdir(Path(__file__).parent)
    subprocess.run([sys.executable, "setup.py"])

def run_tests():
    """Run system tests"""
    print("Running system tests...")
    os.chdir(Path(__file__).parent)
    subprocess.run([sys.executable, "test_system.py"])

def main():
    parser = argparse.ArgumentParser(description='File Synchronization System Launcher')
    subparsers = parser.add_subparsers(dest='command', help='Available commands')
    
    # Server command
    server_parser = subparsers.add_parser('server', help='Start the file sync server')
    
    # Client command
    client_parser = subparsers.add_parser('client', help='Start a file sync client')
    client_parser.add_argument('--watch-dir', required=True, help='Directory to watch')
    client_parser.add_argument('--server-url', default='http://localhost:8000', help='Server URL')
    client_parser.add_argument('--username', required=True, help='Username')
    client_parser.add_argument('--password', required=True, help='Password')
    
    # Setup command
    setup_parser = subparsers.add_parser('setup', help='Run database setup')
    
    # Test command
    test_parser = subparsers.add_parser('test', help='Run system tests')
    
    args = parser.parse_args()
    
    if args.command == 'server':
        run_server()
    elif args.command == 'client':
        run_client(args)
    elif args.command == 'setup':
        run_setup()
    elif args.command == 'test':
        run_tests()
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
