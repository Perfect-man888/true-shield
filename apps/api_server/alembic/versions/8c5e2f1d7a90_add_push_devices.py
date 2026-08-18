"""add push devices

Revision ID: 8c5e2f1d7a90
Revises: 5a7c91f2e4b6
Create Date: 2026-07-31
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "8c5e2f1d7a90"
down_revision: Union[str, Sequence[str], None] = "5a7c91f2e4b6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "push_devices",
        sa.Column(
            "id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "user_id",
            sa.Uuid(),
            nullable=False,
        ),
        sa.Column(
            "installation_id",
            sa.String(length=255),
            nullable=False,
        ),
        sa.Column(
            "platform",
            sa.String(length=20),
            server_default="android",
            nullable=False,
        ),
        sa.Column(
            "device_name",
            sa.String(length=128),
            nullable=True,
        ),
        sa.Column(
            "app_version",
            sa.String(length=32),
            nullable=True,
        ),
        sa.Column(
            "status",
            sa.String(length=20),
            server_default="active",
            nullable=False,
        ),
        sa.Column(
            "last_seen_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.CheckConstraint(
            "platform IN ('android', 'ios')",
            name="ck_push_devices_platform",
        ),
        sa.CheckConstraint(
            "status IN ('active', 'revoked')",
            name="ck_push_devices_status",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("installation_id"),
    )

    op.create_index(
        "ix_push_devices_installation_id",
        "push_devices",
        ["installation_id"],
        unique=True,
    )
    op.create_index(
        "ix_push_devices_status",
        "push_devices",
        ["status"],
        unique=False,
    )
    op.create_index(
        "ix_push_devices_user_id",
        "push_devices",
        ["user_id"],
        unique=False,
    )
    op.create_index(
        "ix_push_devices_user_status",
        "push_devices",
        ["user_id", "status"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        "ix_push_devices_user_status",
        table_name="push_devices",
    )
    op.drop_index(
        "ix_push_devices_user_id",
        table_name="push_devices",
    )
    op.drop_index(
        "ix_push_devices_status",
        table_name="push_devices",
    )
    op.drop_index(
        "ix_push_devices_installation_id",
        table_name="push_devices",
    )
    op.drop_table("push_devices")
