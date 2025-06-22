#!/usr/bin/env python3
"""
File Synchronization System - Test Script
Tests basic functionality of the file sync system
"""

import asyncio
import aiohttp
import tempfile
import os
import shutil
from pathlib import Path

class FileSyncTester:
    def __init__(self, server_url="http://localhost:8000"):
        self.server_url = server_url
        self.session = None
        self.access_token = None
        
    async def setup(self):
        """Setup test environment"""
        self.session = aiohttp.ClientSession()
        
    async def cleanup(self):
        """Cleanup test environment"""
        if self.session:
            await self.session.close()
            
    async def test_authentication(self):
        """Test user authentication"""
        print("Testing authentication...")
        
        # Test login
        async with self.session.post(
            f"{self.server_url}/api/v1/auth/login",
            json={"username": "testuser", "password": "testpass123"}
        ) as response:
            if response.status == 200:
                data = await response.json()
                self.access_token = data["access_token"]
                print("✓ Authentication successful")
                return True
            else:
                print(f"✗ Authentication failed: {response.status}")
                return False
                
    async def test_file_upload(self):
        """Test file upload"""
        print("Testing file upload...")
        
        if not self.access_token:
            print("✗ Cannot test upload: not authenticated")
            return False
            
        # Create a test file
        test_content = b"Hello, this is a test file!"
        
        headers = {"Authorization": f"Bearer {self.access_token}"}
        data = aiohttp.FormData()
        data.add_field('file', test_content, filename="test.txt")
        
        async with self.session.post(
            f"{self.server_url}/api/v1/files/upload",
            data=data,
            headers=headers
        ) as response:
            if response.status == 200:
                print("✓ File upload successful")
                return True
            else:
                print(f"✗ File upload failed: {response.status}")
                return False
                
    async def test_file_list(self):
        """Test file listing"""
        print("Testing file listing...")
        
        if not self.access_token:
            print("✗ Cannot test file list: not authenticated")
            return False
            
        headers = {"Authorization": f"Bearer {self.access_token}"}
        
        async with self.session.get(
            f"{self.server_url}/api/v1/files/list",
            headers=headers
        ) as response:
            if response.status == 200:
                data = await response.json()
                files = data.get("files", [])
                print(f"✓ File listing successful ({len(files)} files)")
                return True
            else:
                print(f"✗ File listing failed: {response.status}")
                return False
                
    async def test_server_health(self):
        """Test server health endpoint"""
        print("Testing server health...")
        
        async with self.session.get(f"{self.server_url}/health") as response:
            if response.status == 200:
                print("✓ Server health check passed")
                return True
            else:
                print(f"✗ Server health check failed: {response.status}")
                return False
                
    async def run_all_tests(self):
        """Run all tests"""
        print("File Synchronization System - Test Suite")
        print("=" * 50)
        
        await self.setup()
        
        tests = [
            self.test_server_health,
            self.test_authentication,
            self.test_file_upload,
            self.test_file_list
        ]
        
        passed = 0
        total = len(tests)
        
        for test in tests:
            try:
                if await test():
                    passed += 1
            except Exception as e:
                print(f"✗ Test failed with exception: {e}")
        
        await self.cleanup()
        
        print("\n" + "=" * 50)
        print(f"Tests completed: {passed}/{total} passed")
        
        if passed == total:
            print("🎉 All tests passed!")
        else:
            print("⚠️  Some tests failed. Check server status and configuration.")

async def main():
    tester = FileSyncTester()
    await tester.run_all_tests()

if __name__ == "__main__":
    asyncio.run(main())
