#!/usr/bin/env python3
"""
Desktop File Sync Application Launcher
Provides options to run CLI or GUI interface
"""
import argparse
import sys
import os
from pathlib import Path

# Add project root to path
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))


def launch_cli():
    """Launch the CLI interface."""
    try:
        from cli import main as cli_main
        print("Starting File Sync CLI...")
        cli_main()
    except ImportError as e:
        print(f"Error importing CLI: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Error starting CLI: {e}")
        sys.exit(1)


def launch_gui():
    """Launch the desktop GUI interface."""
    try:
        import tkinter as tk
        from gui.sync_gui import main as gui_main
        print("Starting File Sync Desktop GUI...")
        gui_main()
    except ImportError as e:
        if "tkinter" in str(e):
            print("Error: Tkinter is not available. Please install tkinter:")
            print("- On Ubuntu/Debian: sudo apt-get install python3-tk")
            print("- On Windows: Tkinter should be included with Python")
            print("- On macOS: Tkinter should be included with Python")
        else:
            print(f"Error importing GUI: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Error starting GUI: {e}")
        sys.exit(1)


def launch_sync_client():
    """Launch the sync client directly."""
    try:
        from client.sync_client import main as sync_main
        print("Starting File Sync Client...")
        sync_main()
    except ImportError as e:
        print(f"Error importing sync client: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Error starting sync client: {e}")
        sys.exit(1)


def main():
    """Main launcher function."""
    parser = argparse.ArgumentParser(
        description="Distributed File Sync Application",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python launcher.py gui              # Start desktop GUI
  python launcher.py cli              # Start CLI interface
  python launcher.py sync             # Start sync client only
  python launcher.py --help           # Show this help
        """
    )
    
    parser.add_argument(
        'interface',
        choices=['gui', 'cli', 'sync'],
        nargs='?',
        default='gui',
        help='Interface to launch (default: gui)'
    )
    
    parser.add_argument(
        '--version',
        action='version',
        version='Distributed File Sync v1.0.0'
    )
    
    args = parser.parse_args()
    
    print("=" * 50)
    print("Distributed File Sync Application")
    print("=" * 50)
    
    if args.interface == 'gui':
        launch_gui()
    elif args.interface == 'cli':
        launch_cli()
    elif args.interface == 'sync':
        launch_sync_client()
    else:
        print(f"Unknown interface: {args.interface}")
        sys.exit(1)


if __name__ == "__main__":
    main()
