from __future__ import annotations

import logging
import uuid
from dataclasses import dataclass
from typing import Final

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.family_alert_policy import (
    FamilyAlertPolicy,
)
from app.models.risk import RiskEvent
from app.repositories.family_alert import (
    create_family_alert,
    get_existing_family_alert,
    list_eligible_trusted_contacts,
    save_family_alert_delivery_state,
)
from app.repositories.family_alert_automation import (
    list_active_family_ids_for_user,
)
from app.repositories.family_alert_policy import (
    get_or_create_family_alert_policy,
)
from app.services.family_alert_service import (
    build_alert_recipients,
    build_family_alert_summary,
    build_family_alert_title,
    deliver_family_alert_notifications,
)

logger = logging.getLogger(__name__)

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

@dataclass(frozen=True, slots=True)
class FamilyAlertAutomationExecutionItem:
    """单个家庭的自动告警执行结果。"""

    family_id: uuid.UUID

    alert_id: uuid.UUID | None

    created: bool

    dispatched: bool

    recipient_count: int

    reason: str


@dataclass(frozen=True, slots=True)
class FamilyAlertAutomationExecutionResult:
    """风险事件的家庭告警自动化执行结果。"""

    event_id: uuid.UUID

    items: tuple[
        FamilyAlertAutomationExecutionItem,
        ...,
    ]

    @property
    def created_count(self) -> int:
        """成功创建的告警数量。"""

        return sum(
            1
            for item in self.items
            if item.created
        )

    @property
    def dispatched_count(self) -> int:
        """完成自动发送的告警数量。"""

        return sum(
            1
            for item in self.items
            if item.dispatched
        )

    @property
    def skipped_count(self) -> int:
        """未创建告警的家庭数量。"""

        return sum(
            1
            for item in self.items
            if not item.created
        )


async def execute_family_alert_automation_plan(
    db: AsyncSession,
    *,
    event: RiskEvent,
    plan: FamilyAlertAutomationPlan | None = None,
) -> FamilyAlertAutomationExecutionResult:
    """
    执行家庭告警自动化计划。

    执行过程：
    1. 生成或接收自动化计划；
    2. 跳过不符合策略的家庭；
    3. 避免同一事件重复创建告警；
    4. 按优先级选取指定数量的可信联系人；
    5. 创建家庭告警；
    6. 根据策略决定是否自动发送。
    """

    if plan is None:
        plan = await build_family_alert_automation_plan(
            db,
            event=event,
        )

    execution_items: list[
        FamilyAlertAutomationExecutionItem
    ] = []

    for plan_item in plan.items:
        if not plan_item.should_create:
            execution_items.append(
                FamilyAlertAutomationExecutionItem(
                    family_id=plan_item.family_id,
                    alert_id=None,
                    created=False,
                    dispatched=False,
                    recipient_count=0,
                    reason=plan_item.reason,
                )
            )
            continue

        existing_alert = (
            await get_existing_family_alert(
                db,
                family_id=plan_item.family_id,
                risk_event_id=event.id,
            )
        )

        if existing_alert is not None:
            execution_items.append(
                FamilyAlertAutomationExecutionItem(
                    family_id=plan_item.family_id,
                    alert_id=existing_alert.id,
                    created=False,
                    dispatched=False,
                    recipient_count=len(
                        existing_alert.recipients
                        or []
                    ),
                    reason="already_exists",
                )
            )
            continue

        contacts = (
            await list_eligible_trusted_contacts(
                db,
                family_id=plan_item.family_id,
                subject_user_id=event.user_id,
            )
        )

        selected_contacts = contacts[
            : plan_item.max_recipients
        ]

        recipient_payloads = (
            build_alert_recipients(
                selected_contacts
            )
        )

        if not recipient_payloads:
            execution_items.append(
                FamilyAlertAutomationExecutionItem(
                    family_id=plan_item.family_id,
                    alert_id=None,
                    created=False,
                    dispatched=False,
                    recipient_count=0,
                    reason=(
                        "no_eligible_recipients"
                    ),
                )
            )
            continue

        alert_title = build_family_alert_title(
            event
        )

        alert_summary = build_family_alert_summary(
            event
        )

        alert = await create_family_alert(
            db,
            family_id=plan_item.family_id,
            risk_event_id=event.id,
            subject_user_id=event.user_id,
            risk_level=normalize_enum_or_string(
                event.risk_level
            ),
            title=alert_title,
            summary=alert_summary,
            recipients=recipient_payloads,
        )

        dispatched = False

        if plan_item.should_dispatch:
            await deliver_family_alert_notifications(
                alert.recipients,
                title=alert_title,
                message=alert_summary,
                simulated_failure_recipient_ids=set(),
            )

            alert = (
                await save_family_alert_delivery_state(
                    db,
                    alert=alert,
                )
            )

            dispatched = True

        execution_items.append(
            FamilyAlertAutomationExecutionItem(
                family_id=plan_item.family_id,
                alert_id=alert.id,
                created=True,
                dispatched=dispatched,
                recipient_count=len(
                    alert.recipients
                ),
                reason=(
                    "created_and_dispatched"
                    if dispatched
                    else "created"
                ),
            )
        )

    return FamilyAlertAutomationExecutionResult(
        event_id=event.id,
        items=tuple(execution_items),
    )


async def trigger_family_alert_automation_safely(
    db: AsyncSession,
    *,
    event: RiskEvent,
) -> FamilyAlertAutomationExecutionResult | None:
    """
    安全触发家庭告警自动化。

    家庭告警自动化发生异常时：
    1. 回滚当前失败事务；
    2. 记录异常日志；
    3. 不影响原风险分析接口返回结果。
    """

    try:
        return await execute_family_alert_automation_plan(
            db,
            event=event,
        )
    except Exception:
        await db.rollback()

        logger.exception(
            "家庭告警自动化执行失败："
            "event_id=%s, user_id=%s, "
            "source_type=%s, risk_level=%s",
            event.id,
            event.user_id,
            event.source_type,
            event.risk_level,
        )

        return None