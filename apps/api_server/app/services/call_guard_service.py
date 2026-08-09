from __future__ import annotations

import hashlib
import hmac
import json
import re
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path

import yaml
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.call_guard import CallGuardReport
from app.models.trusted_contact import TrustedContact
from app.schemas.call_guard import (
    CallDirection,
    CallerVerificationStatus,
    CallGuardReportRequest,
    CallGuardReportResponse,
    CallGuardRiskLevel,
    CallGuardRuleBundle,
    CallGuardRuleItem,
    CallGuardSignal,
    CallNumberAnalyzeRequest,
    CallNumberAnalyzeResponse,
)

RULE_FILE = (
    Path(__file__).resolve().parents[1]
    / "rules"
    / "phone_risk_rules.yaml"
)


@dataclass(frozen=True, slots=True)
class PhoneRule:
    """一条本地号码规则。"""

    value: str
    score: int
    title: str
    explanation: str
    rule_id: str
    match_type: str
    source: str
    confidence: float
    confirmed: bool


class CallGuardService:
    """号码风险筛查服务。"""

    def __init__(self) -> None:
        self.rule_version = "call-guard-1.0.0"
        self.exact_rules: list[PhoneRule] = []
        self.prefix_rules: list[PhoneRule] = []
        self.updated_at = datetime.now(UTC)
        self.valid_days = 30
        self._load_rules()

    def _load_rules(self) -> None:
        if not RULE_FILE.is_file():
            return

        raw = yaml.safe_load(
            RULE_FILE.read_text(encoding="utf-8")
        ) or {}

        self.rule_version = str(
            raw.get("version") or self.rule_version
        )
        raw_updated_at = str(raw.get("updated_at") or "")
        if raw_updated_at:
            self.updated_at = datetime.fromisoformat(
                raw_updated_at.replace("Z", "+00:00")
            )
        self.valid_days = max(1, int(raw.get("valid_days", 30)))
        self.exact_rules = [
            PhoneRule(
                value=self.normalize_number(
                    str(item.get("number", ""))
                ),
                score=int(item.get("score", 0)),
                title=str(item.get("title", "号码风险规则")),
                explanation=str(
                    item.get("explanation", "号码命中风险规则。")
                ),
                rule_id=str(item.get("rule_id") or "PHONE-EXACT"),
                match_type="exact",
                source=str(item.get("source") or "maintained_rule"),
                confidence=float(item.get("confidence", 0.5)),
                confirmed=bool(item.get("confirmed", False)),
            )
            for item in raw.get("exact_numbers", [])
            if item.get("number")
        ]
        self.prefix_rules = [
            PhoneRule(
                value=self.normalize_number(
                    str(item.get("prefix", ""))
                ),
                score=int(item.get("score", 0)),
                title=str(item.get("title", "号段风险规则")),
                explanation=str(
                    item.get("explanation", "号码号段需要谨慎核实。")
                ),
                rule_id=str(item.get("rule_id") or "PHONE-PREFIX"),
                match_type="prefix",
                source=str(item.get("source") or "maintained_rule"),
                confidence=float(item.get("confidence", 0.5)),
                confirmed=bool(item.get("confirmed", False)),
            )
            for item in raw.get("prefix_rules", [])
            if item.get("prefix")
        ]

    def get_rule_bundle(self) -> CallGuardRuleBundle:
        rules = [
            CallGuardRuleItem(
                rule_id=rule.rule_id,
                match_type=rule.match_type,
                value=rule.value,
                score=rule.score,
                title=rule.title,
                explanation=rule.explanation,
                source=rule.source,
                confidence=rule.confidence,
                confirmed=rule.confirmed,
            )
            for rule in [*self.exact_rules, *self.prefix_rules]
        ]
        payload = [rule.model_dump(mode="json") for rule in rules]
        checksum = hashlib.sha256(
            json.dumps(payload, ensure_ascii=False, sort_keys=True).encode()
        ).hexdigest()
        return CallGuardRuleBundle(
            version=self.rule_version,
            updated_at=self.updated_at,
            expires_at=self.updated_at + timedelta(days=self.valid_days),
            checksum=checksum,
            rules=rules,
            disclaimer=(
                "规则命中仅表示需要谨慎核验；未经复核的用户举报不会自动加入规则库。"
            ),
        )

    async def create_report(
        self,
        db: AsyncSession,
        *,
        user_id: uuid.UUID,
        request: CallGuardReportRequest,
    ) -> CallGuardReportResponse:
        number = self.normalize_number(request.phone_number)
        fingerprint = hmac.new(
            settings.jwt_secret_key.encode(),
            number.encode(),
            hashlib.sha256,
        ).hexdigest()
        report = CallGuardReport(
            user_id=user_id,
            number_fingerprint=fingerprint,
            masked_number=self.mask_number(number),
            report_type=request.report_type.value,
            note=request.note,
            status="pending",
        )
        db.add(report)
        await db.commit()
        await db.refresh(report)
        return CallGuardReportResponse(
            id=report.id,
            report_type=request.report_type,
            masked_number=report.masked_number,
            status=report.status,
            created_at=report.created_at,
            message="反馈已提交并进入复核队列，不会直接改变号码风险等级。",
        )

    @staticmethod
    def normalize_number(value: str) -> str:
        """保留开头加号并移除空格、横线和括号。"""

        text = value.strip()
        lowered = text.lower()
        if lowered in {
            "private",
            "restricted",
            "unknown",
            "anonymous",
            "隐藏号码",
            "未知号码",
        }:
            return "unknown"

        text = re.sub(r"[\s\-()]+", "", text)
        if text.startswith("00"):
            text = "+" + text[2:]

        # 中国大陆手机号码可能由系统返回 +86 前缀，
        # 而用户在可信联系人中通常保存 11 位本地号码。
        if text.startswith("+86"):
            mainland_number = text[3:]
            if len(mainland_number) == 11 and mainland_number.isdigit():
                return mainland_number

        return text

    @staticmethod
    def mask_number(number: str) -> str:
        """返回适合日志和提示展示的脱敏号码。"""

        if number == "unknown":
            return "未知/隐藏号码"

        digits = re.sub(r"\D", "", number)
        if len(digits) <= 7:
            return number

        prefix = "+" if number.startswith("+") else ""
        return f"{prefix}{digits[:3]}****{digits[-4:]}"

    async def analyze(
        self,
        db: AsyncSession,
        *,
        user_id: uuid.UUID,
        request: CallNumberAnalyzeRequest,
    ) -> CallNumberAnalyzeResponse:
        number = self.normalize_number(
            request.phone_number
        )

        trusted_name = await self._find_trusted_contact_name(
            db,
            user_id=user_id,
            normalized_number=number,
        )
        if (
            trusted_name is not None
            and request.verification_status
            != CallerVerificationStatus.FAILED
        ):
            return CallNumberAnalyzeResponse(
                normalized_number=number,
                masked_number=self.mask_number(number),
                direction=request.direction,
                verification_status=request.verification_status,
                risk_level=CallGuardRiskLevel.LOW,
                score=0,
                is_trusted_contact=True,
                trusted_contact_name=trusted_name,
                summary=f"该号码属于可信联系人“{trusted_name}”。",
                signals=[],
                actions=["仍请在涉及转账或验证码时进行二次确认。"],
                rule_version=self.rule_version,
            )

        signals: list[CallGuardSignal] = []
        score = 0

        def add_signal(
            signal_id: str,
            title: str,
            signal_score: int,
            explanation: str,
            *,
            source: str = "system_signal",
            confidence: float = 0.5,
            confirmed: bool = False,
        ) -> None:
            nonlocal score
            score += signal_score
            signals.append(
                CallGuardSignal(
                    signal_id=signal_id,
                    title=title,
                    score=min(100, signal_score),
                    explanation=explanation,
                    source=source,
                    confidence=confidence,
                    confirmed=confirmed,
                )
            )

        if number == "unknown":
            add_signal(
                "CALL-001",
                "隐藏或未知来电号码",
                45,
                "无法获得完整号码，不能完成可信联系人或风险库核验。",
            )

        if (
            request.verification_status
            == CallerVerificationStatus.FAILED
        ):
            add_signal(
                "CALL-002",
                "运营商号码验证失败",
                65,
                "系统收到的 STIR/SHAKEN 验证结果为失败，可能存在号码伪造或异常。",
            )
        elif (
            request.verification_status
            == CallerVerificationStatus.PASSED
            and number != "unknown"
        ):
            score -= 20

        digits = re.sub(r"\D", "", number)
        if number != "unknown" and len(digits) < 7:
            add_signal(
                "CALL-003",
                "号码格式异常",
                35,
                "该号码长度过短，无法按普通电话号码进行可靠核验。",
            )

        for rule in self.exact_rules:
            if rule.value and number == rule.value:
                add_signal(
                    "CALL-RULE-EXACT",
                    rule.title,
                    rule.score,
                    rule.explanation,
                    source=rule.source,
                    confidence=rule.confidence,
                    confirmed=rule.confirmed,
                )

        for rule in self.prefix_rules:
            if rule.value and number.startswith(rule.value):
                add_signal(
                    "CALL-RULE-PREFIX",
                    rule.title,
                    rule.score,
                    rule.explanation,
                    source=rule.source,
                    confidence=rule.confidence,
                    confirmed=rule.confirmed,
                )

        if (
            request.direction == CallDirection.INCOMING
            and not signals
        ):
            add_signal(
                "CALL-004",
                "陌生来电",
                20,
                "该号码不在真信盾可信联系人中，接听后不要透露验证码、银行卡或身份证信息。",
            )

        score = max(0, min(100, score))

        # 运营商验证失败代表号码可能被伪造。即使号码文本与可信联系人
        # 一致，也不能把它当作安全来电，因此强制提升为高风险。
        if (
            request.verification_status
            == CallerVerificationStatus.FAILED
        ):
            score = max(score, 75)
            risk_level = CallGuardRiskLevel.HIGH
            summary = (
                "运营商号码验证失败。即使该号码匹配可信联系人，"
                "也可能存在号码伪造，请先挂断并通过已知号码回拨核实。"
            )
        elif score >= 70:
            risk_level = CallGuardRiskLevel.HIGH
            summary = "该通话存在较高风险，请勿转账、共享验证码或安装对方指定的软件。"
        elif score >= 35:
            risk_level = CallGuardRiskLevel.MEDIUM
            summary = "该号码需要谨慎核实身份，涉及资金操作时请先联系家人确认。"
        else:
            risk_level = CallGuardRiskLevel.LOW
            summary = "暂未发现明确号码风险，但号码筛查不能代替人工核实。"

        return CallNumberAnalyzeResponse(
            normalized_number=number,
            masked_number=self.mask_number(number),
            direction=request.direction,
            verification_status=request.verification_status,
            risk_level=risk_level,
            score=score,
            is_trusted_contact=trusted_name is not None,
            trusted_contact_name=trusted_name,
            summary=summary,
            signals=signals,
            actions=[
                "不要向陌生人提供短信验证码或屏幕共享权限。",
                "对方要求转账时立即暂停，并通过官方渠道回拨核实。",
                "感到不安时使用真信盾一键家庭求助。",
            ],
            rule_version=self.rule_version,
        )

    async def _find_trusted_contact_name(
        self,
        db: AsyncSession,
        *,
        user_id: uuid.UUID,
        normalized_number: str,
    ) -> str | None:
        if normalized_number == "unknown":
            return None

        result = await db.execute(
            select(TrustedContact).where(
                TrustedContact.subject_user_id == user_id,
                TrustedContact.status == "active",
                TrustedContact.phone.is_not(None),
            )
        )

        for contact in result.scalars().all():
            if self.normalize_number(contact.phone or "") == normalized_number:
                return contact.display_name

        return None
