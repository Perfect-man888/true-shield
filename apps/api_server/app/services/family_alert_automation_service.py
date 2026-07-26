from __future__ import annotations

from dataclasses import dataclass
from typing import Final

from app.models.family_alert_policy import (
    FamilyAlertPolicy,
)
from app.models.risk import RiskEvent

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