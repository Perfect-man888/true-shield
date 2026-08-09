"""add call guard reports

Revision ID: f2a7c91d4e10
Revises: 8c5e2f1d7a90
Create Date: 2026-08-09
"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "f2a7c91d4e10"
down_revision: str | None = "8c5e2f1d7a90"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "call_guard_reports",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.Column("number_fingerprint", sa.String(length=64), nullable=False),
        sa.Column("masked_number", sa.String(length=64), nullable=False),
        sa.Column("report_type", sa.String(length=32), nullable=False),
        sa.Column("note", sa.Text(), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["user_id"], ["users.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_call_guard_reports_user_id", "call_guard_reports", ["user_id"])
    op.create_index(
        "ix_call_guard_reports_number_fingerprint",
        "call_guard_reports",
        ["number_fingerprint"],
    )
    op.create_index(
        "ix_call_guard_reports_report_type", "call_guard_reports", ["report_type"]
    )
    op.create_index("ix_call_guard_reports_status", "call_guard_reports", ["status"])
    op.create_index(
        "ix_call_guard_reports_created_at", "call_guard_reports", ["created_at"]
    )


def downgrade() -> None:
    op.drop_index("ix_call_guard_reports_created_at", table_name="call_guard_reports")
    op.drop_index("ix_call_guard_reports_status", table_name="call_guard_reports")
    op.drop_index("ix_call_guard_reports_report_type", table_name="call_guard_reports")
    op.drop_index(
        "ix_call_guard_reports_number_fingerprint", table_name="call_guard_reports"
    )
    op.drop_index("ix_call_guard_reports_user_id", table_name="call_guard_reports")
    op.drop_table("call_guard_reports")
