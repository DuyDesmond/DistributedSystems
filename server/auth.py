"""Authentication and authorization utilities."""

import os
import jwt
import bcrypt
from datetime import datetime, timedelta
from typing import Optional, Dict, Any
from fastapi import HTTPException, status, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from server.database import get_db_session
from server.models import User, Session as UserSession
import secrets


class AuthManager:
    """Handles authentication and authorization."""
    
    def __init__(self, secret_key: str, algorithm: str = "HS256"):
        self.secret_key = secret_key
        self.algorithm = algorithm
        self.bearer_scheme = HTTPBearer()
    
    def hash_password(self, password: str) -> str:
        """Hash password using bcrypt."""
        salt = bcrypt.gensalt()
        return bcrypt.hashpw(password.encode('utf-8'), salt).decode('utf-8')
    
    def verify_password(self, password: str, hashed_password: str) -> bool:
        """Verify password against hash."""
        return bcrypt.checkpw(password.encode('utf-8'), hashed_password.encode('utf-8'))
    
    def create_access_token(self, data: Dict[str, Any], expires_delta: Optional[timedelta] = None) -> str:
        """Create JWT access token."""
        to_encode = data.copy()
        
        if expires_delta:
            expire = datetime.utcnow() + expires_delta
        else:
            expire = datetime.utcnow() + timedelta(hours=1)
        
        to_encode.update({"exp": expire})
        encoded_jwt = jwt.encode(to_encode, self.secret_key, algorithm=self.algorithm)
        return encoded_jwt
    
    def create_refresh_token(self) -> str:
        """Create refresh token."""
        return secrets.token_urlsafe(32)
    
    def verify_token(self, token: str) -> Dict[str, Any]:
        """Verify and decode JWT token."""
        try:
            payload = jwt.decode(token, self.secret_key, algorithms=[self.algorithm])
            return payload
        except jwt.ExpiredSignatureError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token has expired"
            )
        except jwt.JWTError:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token"
            )
    
    def get_current_user(
        self,
        credentials: HTTPAuthorizationCredentials = Depends(HTTPBearer()),
        db: Session = Depends(get_db_session)
    ) -> User:
        """Get current authenticated user."""
        token = credentials.credentials
        payload = self.verify_token(token)
        
        user_id = payload.get("sub")
        if user_id is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token payload"
            )
        
        user = db.query(User).filter(User.user_id == user_id).first()
        if user is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="User not found"
            )
        
        if user.account_status != "active":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Account is not active"
            )
        
        return user
    
    def create_user_session(
        self,
        db: Session,
        user_id: str,
        client_id: str,
        expires_delta: Optional[timedelta] = None
    ) -> Dict[str, Any]:
        """Create user session with tokens."""
        if expires_delta:
            expires_at = datetime.utcnow() + expires_delta
        else:
            expires_at = datetime.utcnow() + timedelta(hours=24)
        
        # Create tokens
        access_token = self.create_access_token(
            data={"sub": str(user_id), "client_id": client_id}
        )
        refresh_token = self.create_refresh_token()
        
        # Save session to database
        session = UserSession(
            user_id=user_id,
            client_id=client_id,
            access_token=access_token,
            refresh_token=refresh_token,
            expires_at=expires_at
        )
        
        db.add(session)
        db.commit()
        
        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": int(expires_delta.total_seconds()) if expires_delta else 3600
        }
    
    def refresh_access_token(
        self,
        db: Session,
        refresh_token: str
    ) -> Dict[str, Any]:
        """Refresh access token using refresh token."""
        session = db.query(UserSession).filter(
            UserSession.refresh_token == refresh_token,
            UserSession.is_active == True,
            UserSession.expires_at > datetime.utcnow()
        ).first()
        
        if not session:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid refresh token"
            )
        
        # Create new access token
        access_token = self.create_access_token(
            data={"sub": str(session.user_id), "client_id": session.client_id}
        )
        
        # Update session
        session.access_token = access_token
        session.last_heartbeat = datetime.utcnow()
        db.commit()
        
        return {
            "access_token": access_token,
            "token_type": "bearer",
            "expires_in": 3600
        }
    
    def invalidate_session(self, db: Session, access_token: str):
        """Invalidate user session."""
        session = db.query(UserSession).filter(
            UserSession.access_token == access_token
        ).first()
        
        if session:
            session.is_active = False
            db.commit()
    
    def update_heartbeat(self, db: Session, access_token: str):
        """Update session heartbeat."""
        session = db.query(UserSession).filter(
            UserSession.access_token == access_token,
            UserSession.is_active == True
        ).first()
        
        if session:
            session.last_heartbeat = datetime.utcnow()
            db.commit()


# Global auth manager instance
auth_manager = None


def init_auth(secret_key: str):
    """Initialize authentication manager."""
    global auth_manager
    auth_manager = AuthManager(secret_key)
    return auth_manager


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(HTTPBearer()),
    db: Session = Depends(get_db_session)
) -> User:
    """Dependency to get current authenticated user."""
    if not auth_manager:
        raise RuntimeError("Auth manager not initialized")
    return auth_manager.get_current_user(credentials, db)
