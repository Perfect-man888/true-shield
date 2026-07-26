from __future__ import annotations

import uuid
from dataclasses import dataclass
from typing import Final

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family_alert_policy import (
    FamilyAlertPolicy,
)
from app.models.risk import RiskEvent
from app.repositories.family_alert_automation import (
    list_active_family_ids_for_user,
)
from app.repositories.family_alert_policy import (
    get_or_create_family_alert_policy,
)

RISK_LEVEL_RANK: Final[dict[str, int]] = {
    "low": 1,
    "medium": 2,
    "high": 3,
}

SUPPORTED_SOURCE_TYPES: Final[set[str]] = {
    "text",
    "image",
    "url",
}


@dataclass(frozen=True, slots=True)
class FamilyAlertAutomationDecision:
    """家庭告警自动化决策结果。"""

    should_create: bool

    should_dispatch: bool

    reason: str


def normalize_enum_or_string(
    value: object,
) -> str:
    """将字符串或枚举统一转换为小写字符串。"""

    raw_value = getattr(
        value,
        "value",
        value,
    )

    return str(raw_value).strip().lower()


def risk_level_meets_threshold(
    *,
    actual_risk_level: object,
    minimum_risk_level: object,
) -> bool:
    """判断实际风险等级是否达到策略阈值。"""

    actual = normalize_enum_or_string(
        actual_risk_level
    )

    minimum = normalize_enum_or_string(
        minimum_risk_level
    )

    actual_rank = RISK_LEVEL_RANK.get(actual)
    minimum_rank = RISK_LEVEL_RANK.get(minimum)

    if (
        actual_rank is None
        or minimum_rank is None
    ):
        return False

    return actual_rank >= minimum_rank


def evaluate_family_alert_policy(
    *,
    policy: FamilyAlertPolicy,
    event: RiskEvent,
) -> FamilyAlertAutomationDecision:
    """
    根据家庭策略判断风险事件是否自动触发告警。

    判断顺序：
    1. 是否启用自动创建；
    2. 风险来源是否受策略支持；
    3. 来源是否包含在家庭启用范围内；
    4. 风险等级是否达到最低阈值；
    5. 是否启用自动发送。
    """

    if not policy.auto_create_enabled:
        return FamilyAlertAutomationDecision(
            should_create=False,
            should_dispatch=False,
            reason="auto_create_disabled",
        )

    source_type = normalize_enum_or_string(
        event.source_type
    )

    if source_type not in SUPPORTED_SOURCE_TYPES:
        return FamilyAlertAutomationDecision(
            should_create=False,
            should_dispatch=False,
            reason="unsupported_source_type",
        )

    enabled_source_types = {
        normalize_enum_or_string(source)
        for source in (
            policy.enabled_source_types or []
        )
    }

    if source_type not in enabled_source_types:
        return FamilyAlertAutomationDecision(
            should_create=False,
            should_dispatch=False,
            reason="source_type_disabled",
        )

    if not risk_level_meets_threshold(
        actual_risk_level=event.risk_level,
        minimum_risk_level=(
            policy.minimum_risk_level
        ),
    ):
        return FamilyAlertAutomationDecision(
            should_create=False,
            should_dispatch=False,
            reason="risk_level_below_threshold",
        )

    return FamilyAlertAutomationDecision(
        should_create=True,
        should_dispatch=(
            policy.auto_dispatch_enabled
        ),
        reason="policy_matched",
    )


@dataclass(frozen=True, slots=True)
class FamilyAlertAutomationPlanItem:
    """单个家庭的自动告警执行计划。"""

    family_id: uuid.UUID

    should_create: bool

    should_dispatch: bool

    reason: str

    max_recipients: int


@dataclass(frozen=True, slots=True)
class FamilyAlertAutomationPlan:
    """一个风险事件对应的全部家庭执行计划。"""

    event_id: uuid.UUID

    subject_user_id: uuid.UUID

    items: tuple[
        FamilyAlertAutomationPlanItem,
        ...,
    ]

    @property
    def family_count(self) -> int:
        """参与策略判断的家庭数量。"""

        return len(self.items)

    @property
    def matched_count(self) -> int:
        """符合自动创建条件的家庭数量。"""

        return sum(
            1
            for item in self.items
            if item.should_create
        )

    @property
    def dispatch_count(self) -> int:
        """符合自动发送条件的家庭数量。"""

        return sum(
            1
            for item in self.items
            if item.should_dispatch
        )


async def build_family_alert_automation_plan(
    db: AsyncSession,
    *,
    event: RiskEvent,
) -> FamilyAlertAutomationPlan:
    """
    为一个风险事件生成家庭告警自动化计划。

    此函数只负责决策，不创建告警，也不发送消息。
    """

    family_ids = (
        await list_active_family_ids_for_user(
            db,
            user_id=event.user_id,
        )
    )

    plan_items: list[
        FamilyAlertAutomationPlanItem
    ] = []

    for family_id in family_ids:
        policy = (
            await get_or_create_family_alert_policy(
                db,
                family_id=family_id,
            )
        )

        decision = evaluate_family_alert_policy(
            policy=policy,
            event=event,
        )

        plan_items.append(
            FamilyAlertAutomationPlanItem(
                family_id=family_id,
                should_create=(
                    decision.should_create
                ),
                should_dispatch=(
                    decision.should_dispatch
                ),
                reason=decision.reason,
                max_recipients=(
                    policy.max_recipients
                ),
            )
        )

    return FamilyAlertAutomationPlan(
        event_id=event.id,
        subject_user_id=event.user_id,
        items=tuple(plan_items),
    )