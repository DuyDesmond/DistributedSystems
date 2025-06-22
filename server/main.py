from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
import sys
import os

# Add current directory to path for imports
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
sys.path.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))

from api.routes import router as api_router
from api.websocket import websocket_endpoint
from models.file import Base
from common.constants import POSTGRES_URL, SERVER_HOST, SERVER_PORT

# Database setup
engine = create_engine(POSTGRES_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Create tables
Base.metadata.create_all(bind=engine)

# FastAPI app
app = FastAPI(title="File Sync Server", version="1.0.0")

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Dependency to get database session
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# Update the dependency in routes
api_router.dependency_overrides[get_db] = get_db

# Include API routes
app.include_router(api_router, prefix="/api/v1")

# WebSocket endpoint
@app.websocket("/ws/{user_id}")
async def websocket_handler(websocket, user_id: str):
    await websocket_endpoint(websocket, user_id)

@app.get("/")
async def root():
    return {"message": "File Sync Server is running"}

@app.get("/health")
async def health_check():
    return {"status": "healthy"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=SERVER_HOST, port=SERVER_PORT)
