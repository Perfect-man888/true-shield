from __future__ import annotations

from types import SimpleNamespace

from app.services.family_alert_automation_service import (
    evaluate_family_alert_policy,
    risk_level_meets_threshold,
)


def build_policy(
    *,
    auto_create_enabled: bool = True,
    auto_dispatch_enabled: bool = False,
    minimum_risk_level: str = "high",
    enabled_source_types: (
        list[str] | None
    ) = None,
) -> SimpleNamespace:
    """构造测试策略。"""

    return SimpleNamespace(
        auto_create_enabled=(
            auto_create_enabled
        ),
        auto_dispatch_enabled=(
            auto_dispatch_enabled
        ),
        minimum_risk_level=(
            minimum_risk_level
        ),
        enabled_source_types=(
            enabled_source_types
            if enabled_source_types is not None
            else [
                "text",
                "image",
                "url",
            ]
        ),
    )


def build_event(
    *,
    risk_level: str = "high",
    source_type: str = "text",
) -> SimpleNamespace:
    """构造测试风险事件。"""

    return SimpleNamespace(
        risk_level=risk_level,
        source_type=source_type,
    )


def test_high_risk_reaches_high_threshold() -> None:
    """高风险达到 high 阈值。"""

    assert risk_level_meets_threshold(
        actual_risk_level="high",
        minimum_risk_level="high",
    )


def test_medium_does_not_reach_high_threshold() -> None:
    """中风险未达到 high 阈值。"""

    assert not risk_level_meets_threshold(
        actual_risk_level="medium",
        minimum_risk_level="high",
    )


def test_matching_policy_creates_alert() -> None:
    """满足策略时应自动创建告警。"""

    decision = evaluate_family_alert_policy(
        policy=build_policy(),
        event=build_event(),
    )

    assert decision.should_create is True
    assert decision.should_dispatch is False
    assert decision.reason == "policy_matched"


def test_auto_dispatch_enabled() -> None:
    """启用自动发送时应同时发送告警。"""

    decision = evaluate_family_alert_policy(
        policy=build_policy(
            auto_dispatch_enabled=True,
        ),
        event=build_event(),
    )

    assert decision.should_create is True
    assert decision.should_dispatch is True
    assert decision.reason == "policy_matched"


def test_auto_create_disabled() -> None:
    """关闭自动创建时不应生成告警。"""

    decision = evaluate_family_alert_policy(
        policy=build_policy(
            auto_create_enabled=False,
        ),
        event=build_event(),
    )

    assert decision.should_create is False
    assert decision.should_dispatch is False
    assert (
        decision.reason
        == "auto_create_disabled"
    )


def test_source_type_disabled() -> None:
    """风险来源未启用时不应生成告警。"""

    decision = evaluate_family_alert_policy(
        policy=build_policy(
            enabled_source_types=[
                "image",
                "url",
            ],
        ),
        event=build_event(
            source_type="text",
        ),
    )

    assert decision.should_create is False
    assert decision.should_dispatch is False
    assert (
        decision.reason
        == "source_type_disabled"
    )


def test_risk_level_below_threshold() -> None:
    """风险等级不足时不应生成告警。"""

    decision = evaluate_family_alert_policy(
        policy=build_policy(
            minimum_risk_level="high",
        ),
        event=build_event(
            risk_level="medium",
        ),
    )

    assert decision.should_create is False
    assert decision.should_dispatch is False
    assert (
        decision.reason
        == "risk_level_below_threshold"
    )