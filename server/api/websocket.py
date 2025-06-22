from fastapi import WebSocket, WebSocketDisconnect
import json
import sys
import os

# Add parent directory to path for imports
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from common.models import FileEvent

class ConnectionManager:
    def __init__(self):
        self.active_connections: list[WebSocket] = []
        self.user_connections: dict[str, WebSocket] = {}
    
    async def connect(self, websocket: WebSocket, user_id: str):
        await websocket.accept()
        self.active_connections.append(websocket)
        self.user_connections[user_id] = websocket
    
    def disconnect(self, websocket: WebSocket, user_id: str):
        self.active_connections.remove(websocket)
        if user_id in self.user_connections:
            del self.user_connections[user_id]
    
    async def send_personal_message(self, message: str, user_id: str):
        if user_id in self.user_connections:
            await self.user_connections[user_id].send_text(message)
    
    async def broadcast(self, message: str):
        for connection in self.active_connections:
            await connection.send_text(message)
    
    async def broadcast_file_event(self, event: FileEvent, exclude_user: str = None):
        message = {
            "type": "file_event",
            "data": event.dict()
        }
        
        for user_id, connection in self.user_connections.items():
            if user_id != exclude_user:
                await connection.send_text(json.dumps(message))

manager = ConnectionManager()

async def websocket_endpoint(websocket: WebSocket, user_id: str):
    await manager.connect(websocket, user_id)
    try:
        while True:
            data = await websocket.receive_text()
            message = json.loads(data)
            
            # Handle different message types
            if message.get("type") == "ping":
                await websocket.send_text(json.dumps({"type": "pong"}))
            elif message.get("type") == "file_event":
                # Broadcast file event to other clients
                event = FileEvent(**message["data"])
                await manager.broadcast_file_event(event, exclude_user=user_id)
    
    except WebSocketDisconnect:
        manager.disconnect(websocket, user_id)
