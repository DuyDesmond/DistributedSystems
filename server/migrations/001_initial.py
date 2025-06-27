"""
Alembic database migration script
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision = '001'
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    # Create users table
    op.create_table('users',
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('username', sa.String(length=100), nullable=False),
        sa.Column('email', sa.String(length=255), nullable=False),
        sa.Column('password_hash', sa.String(length=255), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('last_login', sa.DateTime(), nullable=True),
        sa.Column('storage_quota', sa.BigInteger(), nullable=False),
        sa.Column('used_storage', sa.BigInteger(), nullable=False),
        sa.Column('account_status', sa.String(length=20), nullable=False),
        sa.PrimaryKeyConstraint('user_id')
    )
    op.create_index(op.f('ix_users_email'), 'users', ['email'], unique=True)
    op.create_index(op.f('ix_users_username'), 'users', ['username'], unique=True)

    # Create files table
    op.create_table('files',
        sa.Column('file_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('file_path', sa.String(length=500), nullable=False),
        sa.Column('file_name', sa.String(length=255), nullable=False),
        sa.Column('file_size', sa.BigInteger(), nullable=False),
        sa.Column('checksum', sa.String(length=64), nullable=False),
        sa.Column('version_number', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('modified_at', sa.DateTime(), nullable=False),
        sa.Column('sync_status', sa.String(length=20), nullable=False),
        sa.Column('conflict_status', sa.String(length=30), nullable=False),
        sa.Column('is_deleted', sa.Boolean(), nullable=False),
        sa.Column('storage_path', sa.String(length=500), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.user_id'], ),
        sa.PrimaryKeyConstraint('file_id')
    )
    op.create_index('idx_files_checksum_size', 'files', ['checksum', 'file_size'], unique=False)
    op.create_index('idx_files_user_path', 'files', ['user_id', 'file_path'], unique=False)
    op.create_index('idx_files_user_status', 'files', ['user_id', 'sync_status'], unique=False)
    op.create_index(op.f('ix_files_checksum'), 'files', ['checksum'], unique=False)
    op.create_index(op.f('ix_files_modified_at'), 'files', ['modified_at'], unique=False)
    op.create_index(op.f('ix_files_user_id'), 'files', ['user_id'], unique=False)

    # Create file_versions table
    op.create_table('file_versions',
        sa.Column('version_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('file_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('version_number', sa.Integer(), nullable=False),
        sa.Column('checksum', sa.String(length=64), nullable=False),
        sa.Column('storage_path', sa.String(length=500), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('is_current_version', sa.Boolean(), nullable=False),
        sa.Column('file_size', sa.BigInteger(), nullable=False),
        sa.ForeignKeyConstraint(['file_id'], ['files.file_id'], ),
        sa.PrimaryKeyConstraint('version_id')
    )
    op.create_index('idx_versions_current', 'file_versions', ['file_id', 'is_current_version'], unique=False)
    op.create_index('idx_versions_file_number', 'file_versions', ['file_id', 'version_number'], unique=False)
    op.create_index(op.f('ix_file_versions_file_id'), 'file_versions', ['file_id'], unique=False)

    # Create sync_events table
    op.create_table('sync_events',
        sa.Column('event_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('file_id', postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column('event_type', sa.String(length=20), nullable=False),
        sa.Column('timestamp', sa.DateTime(), nullable=False),
        sa.Column('client_id', sa.String(length=50), nullable=False),
        sa.Column('sync_status', sa.String(length=20), nullable=False),
        sa.Column('metadata', sa.Text(), nullable=True),
        sa.ForeignKeyConstraint(['file_id'], ['files.file_id'], ),
        sa.ForeignKeyConstraint(['user_id'], ['users.user_id'], ),
        sa.PrimaryKeyConstraint('event_id')
    )
    op.create_index('idx_sync_events_status', 'sync_events', ['sync_status', 'timestamp'], unique=False)
    op.create_index('idx_sync_events_user_time', 'sync_events', ['user_id', 'timestamp'], unique=False)
    op.create_index(op.f('ix_sync_events_file_id'), 'sync_events', ['file_id'], unique=False)
    op.create_index(op.f('ix_sync_events_timestamp'), 'sync_events', ['timestamp'], unique=False)
    op.create_index(op.f('ix_sync_events_user_id'), 'sync_events', ['user_id'], unique=False)

    # Create sessions table
    op.create_table('sessions',
        sa.Column('session_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('client_id', sa.String(length=50), nullable=False),
        sa.Column('access_token', sa.String(length=500), nullable=False),
        sa.Column('refresh_token', sa.String(length=500), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('expires_at', sa.DateTime(), nullable=False),
        sa.Column('is_active', sa.Boolean(), nullable=False),
        sa.Column('last_heartbeat', sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.user_id'], ),
        sa.PrimaryKeyConstraint('session_id')
    )
    op.create_index('idx_sessions_token', 'sessions', ['access_token'], unique=False)
    op.create_index('idx_sessions_user_active', 'sessions', ['user_id', 'is_active'], unique=False)
    op.create_index(op.f('ix_sessions_user_id'), 'sessions', ['user_id'], unique=False)

    # Create file_chunks table
    op.create_table('file_chunks',
        sa.Column('chunk_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('upload_id', sa.String(length=100), nullable=False),
        sa.Column('file_id', postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column('chunk_number', sa.Integer(), nullable=False),
        sa.Column('total_chunks', sa.Integer(), nullable=False),
        sa.Column('chunk_size', sa.Integer(), nullable=False),
        sa.Column('checksum', sa.String(length=64), nullable=False),
        sa.Column('storage_path', sa.String(length=500), nullable=False),
        sa.Column('uploaded_at', sa.DateTime(), nullable=False),
        sa.ForeignKeyConstraint(['file_id'], ['files.file_id'], ),
        sa.PrimaryKeyConstraint('chunk_id')
    )
    op.create_index('idx_chunks_upload', 'file_chunks', ['upload_id', 'chunk_number'], unique=False)
    op.create_index(op.f('ix_file_chunks_upload_id'), 'file_chunks', ['upload_id'], unique=False)

    # Create audit_logs table
    op.create_table('audit_logs',
        sa.Column('log_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column('action', sa.String(length=100), nullable=False),
        sa.Column('resource_type', sa.String(length=50), nullable=True),
        sa.Column('resource_id', sa.String(length=100), nullable=True),
        sa.Column('ip_address', sa.String(length=45), nullable=True),
        sa.Column('user_agent', sa.Text(), nullable=True),
        sa.Column('timestamp', sa.DateTime(), nullable=False),
        sa.Column('success', sa.Boolean(), nullable=False),
        sa.Column('details', sa.Text(), nullable=True),
        sa.ForeignKeyConstraint(['user_id'], ['users.user_id'], ),
        sa.PrimaryKeyConstraint('log_id')
    )
    op.create_index('idx_audit_action_time', 'audit_logs', ['action', 'timestamp'], unique=False)
    op.create_index('idx_audit_user_time', 'audit_logs', ['user_id', 'timestamp'], unique=False)
    op.create_index(op.f('ix_audit_logs_timestamp'), 'audit_logs', ['timestamp'], unique=False)
    op.create_index(op.f('ix_audit_logs_user_id'), 'audit_logs', ['user_id'], unique=False)


def downgrade():
    # Drop tables in reverse order
    op.drop_table('audit_logs')
    op.drop_table('file_chunks')
    op.drop_table('sessions')
    op.drop_table('sync_events')
    op.drop_table('file_versions')
    op.drop_table('files')
    op.drop_table('users')
