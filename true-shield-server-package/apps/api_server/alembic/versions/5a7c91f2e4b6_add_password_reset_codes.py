"""add password reset codes

Revision ID: 5a7c91f2e4b6
Revises: c8f19c1107ad
Create Date: 2026-07-31 21:05:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


revision: str = "5a7c91f2e4b6"
down_revision: Union[str, Sequence[str], None] = "c8f19c1107ad"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column(
            "auth_version",
            sa.Integer(),
            server_default="1",
            nullable=False,
        ),
    )

    op.create_table(
        "password_reset_codes",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("code_hash", sa.String(length=64), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("failed_attempts", sa.Integer(), server_default="0", nullable=False),
        sa.Column("used_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        op.f("ix_password_reset_codes_user_id"),
        "password_reset_codes",
        ["user_id"],
        unique=False,
    )
    op.create_index(
        op.f("ix_password_reset_codes_expires_at"),
        "password_reset_codes",
        ["expires_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_password_reset_codes_used_at"),
        "password_reset_codes",
        ["used_at"],
        unique=False,
    )
    op.create_index(
        op.f("ix_password_reset_codes_created_at"),
        "password_reset_codes",
        ["created_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(
        op.f("ix_password_reset_codes_created_at"),
        table_name="password_reset_codes",
    )
    op.drop_index(
        op.f("ix_password_reset_codes_used_at"),
        table_name="password_reset_codes",
    )
    op.drop_index(
        op.f("ix_password_reset_codes_expires_at"),
        table_name="password_reset_codes",
    )
    op.drop_index(
        op.f("ix_password_reset_codes_user_id"),
        table_name="password_reset_codes",
    )
    op.drop_table("password_reset_codes")
    op.drop_column("users", "auth_version")
