#!/bin/bash
# Integration test script for account isolation and sync behavior
# This script would be run after starting the server and two client instances

echo "=== Distributed File Sync Integration Test ==="
echo "Testing account isolation and deleted file handling"

# Test 1: Account Isolation
echo ""
echo "TEST 1: Account Isolation"
echo "- Start two clients with different accounts (user1, user2)"
echo "- User1 uploads file1.txt"
echo "- User2 uploads file2.txt"
echo "- Verify user1 can only see file1.txt"
echo "- Verify user2 can only see file2.txt"

# Test 2: Deleted File Resurrection Prevention
echo ""
echo "TEST 2: Deleted File Resurrection Prevention"
echo "- User1 uploads test_delete.txt"
echo "- Wait for sync completion"
echo "- User1 deletes test_delete.txt"
echo "- Wait for sync completion"
echo "- Verify file does not reappear in user1's sync folder"
echo "- Verify file is marked as DELETED in user1's database"

# Test 3: Cross-Account File Isolation
echo ""
echo "TEST 3: Cross-Account File Isolation"
echo "- User1 creates sensitive_file.txt"
echo "- User2 should never be able to access sensitive_file.txt"
echo "- User1 deletes sensitive_file.txt"
echo "- User2 should never see traces of sensitive_file.txt"

# Test 4: Authentication Flow
echo ""
echo "TEST 4: Authentication Flow"
echo "- Test login sets currentUsername in DatabaseService"
echo "- Test logout clears currentUsername in DatabaseService"
echo "- Test operations fail when not authenticated"

echo ""
echo "=== Manual Testing Instructions ==="
echo "1. Start the server: ./start-server.bat"
echo "2. Start client1: ./start-client.bat"
echo "3. Start client2 in another terminal from client2/ directory"
echo "4. Register two different users in each client"
echo "5. Upload different files from each client"
echo "6. Verify files don't cross between accounts"
echo "7. Delete a file from one client and verify it doesn't reappear"
echo ""
echo "Expected Results:"
echo "- Files are strictly isolated per user account"
echo "- Deleted files stay deleted and don't resurrect"
echo "- Database operations are filtered by currentUsername"
echo "- No cross-account data leakage occurs"

# Test queries to run manually on the SQLite databases
echo ""
echo "=== Manual Database Verification Queries ==="
echo "In client1/file_sync.db:"
echo "SELECT file_path, sync_status, username FROM file_version_vector WHERE username IS NOT NULL;"
echo "SELECT file_path, operation, username FROM sync_queue WHERE username IS NOT NULL;"
echo ""
echo "In client2/file_sync.db:"
echo "SELECT file_path, sync_status, username FROM file_version_vector WHERE username IS NOT NULL;"
echo "SELECT file_path, operation, username FROM sync_queue WHERE username IS NOT NULL;"
echo ""
echo "Expected: Each database should only contain records for its respective user"
