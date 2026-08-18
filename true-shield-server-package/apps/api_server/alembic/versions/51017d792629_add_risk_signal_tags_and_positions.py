"""add risk signal tags and positions

Revision ID: 51017d792629
Revises: a5091d7e6733
Create Date: 2026-07-23 15:12:09.909113

"""
from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision: str = '51017d792629'
down_revision: str | Sequence[str] | None = 'a5091d7e6733'
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """增加风险标签和命中位置字段。"""

    op.add_column(
        "risk_signals",
        sa.Column(
            "tags",
            sa.JSON(),
            server_default=sa.text("'[]'::json"),
            nullable=False,
        ),
    )

    op.add_column(
        "risk_signals",
        sa.Column(
            "match_positions",
            sa.JSON(),
            server_default=sa.text("'[]'::json"),
            nullable=False,
        ),
    )

    op.alter_column(
        "risk_signals",
        "tags",
        server_default=None,
    )

    op.alter_column(
        "risk_signals",
        "match_positions",
        server_default=None,
    )


def downgrade() -> None:
    """删除风险标签和命中位置字段。"""

    op.drop_column(
        "risk_signals",
        "match_positions",
    )

    op.drop_column(
        "risk_signals",
        "tags",
    )
