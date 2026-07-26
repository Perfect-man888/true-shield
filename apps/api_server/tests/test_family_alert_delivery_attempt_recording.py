from __future__ import annotations

import uuid
from types import SimpleNamespace

import pytest

from app.repositories.family_alert import (
    record_family_alert_delivery_attempts,
)

pytestmark = pytest.mark.asyncio


class FakeScalarResult:
    def __init__(
        self,
        value: int,
    ) -> None:
        self.value = value

    def scalar_one(self) -> int:
        return self.value


class FakeDatabase:
    def __init__(
        self,
        *,
        previous_attempt_number: int = 0,
    ) -> None:
        self.previous_attempt_number = (
            previous_attempt_number
        )

        self.added: list[object] = []
        self.flushed = False

    async def execute(
        self,
        statement: object,
    ) -> FakeScalarResult:
        return FakeScalarResult(
            self.previous_attempt_number
        )

    def add(
        self,
        value: object,
    ) -> None:
        self.added.append(value)

    async def flush(self) -> None:
        self.flushed = True


def build_recipient() -> SimpleNamespace:
    return SimpleNamespace(
        id=uuid.uuid4(),
        channel="sms",
        destination="13800138000",
        delivery_provider="simulated",
        delivery_status="sent",
        external_message_id="sim-test-message",
        failure_reason=None,
    )


async def test_first_delivery_attempt_is_recorded() -> None:
    db = FakeDatabase()
    recipient = build_recipient()

    attempts = (
        await record_family_alert_delivery_attempts(
            db,  # type: ignore[arg-type]
            recipients=[recipient],
            attempted_recipient_ids={
                recipient.id
            },
        )
    )

    assert len(attempts) == 1
    assert len(db.added) == 1
    assert db.flushed is True

    attempt = attempts[0]

    assert attempt.recipient_id == recipient.id
    assert attempt.attempt_number == 1
    assert attempt.provider == "simulated"
    assert attempt.status == "sent"
    assert (
        attempt.external_message_id
        == "sim-test-message"
    )


async def test_attempt_number_is_incremented() -> None:
    db = FakeDatabase(
        previous_attempt_number=2,
    )

    recipient = build_recipient()

    attempts = (
        await record_family_alert_delivery_attempts(
            db,  # type: ignore[arg-type]
            recipients=[recipient],
            attempted_recipient_ids={
                recipient.id
            },
        )
    )

    assert attempts[0].attempt_number == 3


async def test_non_attempted_recipient_is_ignored() -> None:
    db = FakeDatabase()
    recipient = build_recipient()

    attempts = (
        await record_family_alert_delivery_attempts(
            db,  # type: ignore[arg-type]
            recipients=[recipient],
            attempted_recipient_ids=set(),
        )
    )

    assert attempts == []
    assert db.added == []
    assert db.flushed is False