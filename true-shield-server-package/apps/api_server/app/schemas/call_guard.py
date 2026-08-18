from __future__ import annotations

import uuid
from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field, field_validator


class CallDirection(StrEnum):
    """电话方向。"""

    INCOMING = "incoming"
    OUTGOING = "outgoing"
    UNKNOWN = "unknown"


class CallerVerificationStatus(StrEnum):
    """运营商号码验证状态。"""

    PASSED = "passed"
    FAILED = "failed"
    NOT_VERIFIED = "not_verified"


class CallGuardRiskLevel(StrEnum):
    """通话护航风险等级。"""

    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class CallGuardSignal(BaseModel):
    """号码风险判断中的一条证据。"""

    signal_id: str
    title: str
    score: int = Field(ge=0, le=100)
    explanation: str
    source: str = "local_rule"
    confidence: float = Field(default=0.5, ge=0, le=1)
    confirmed: bool = False


class CallGuardRuleItem(BaseModel):
    """可安全下发到客户端的一条号码规则。"""

    rule_id: str
    match_type: str
    value: str
    score: int = Field(ge=0, le=100)
    title: str
    explanation: str
    source: str
    confidence: float = Field(ge=0, le=1)
    confirmed: bool = False


class CallGuardRuleBundle(BaseModel):
    """Android 离线筛查使用的版本化规则包。"""

    version: str
    updated_at: datetime
    checksum: str
    expires_at: datetime
    rules: list[CallGuardRuleItem]
    disclaimer: str


class CallGuardReportType(StrEnum):
    SUSPICIOUS = "suspicious"
    SCAM = "scam"
    SAFE = "safe"
    FALSE_POSITIVE = "false_positive"


class CallGuardReportRequest(BaseModel):
    phone_number: str = Field(min_length=1, max_length=64)
    report_type: CallGuardReportType
    note: str | None = Field(default=None, max_length=500)

    @field_validator("phone_number")
    @classmethod
    def normalize_report_phone_number(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("电话号码不能为空。")
        return cleaned

    @field_validator("note")
    @classmethod
    def normalize_note(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return value.strip() or None


class CallGuardReportResponse(BaseModel):
    id: uuid.UUID
    report_type: CallGuardReportType
    masked_number: str
    status: str
    created_at: datetime
    message: str


class CallNumberAnalyzeRequest(BaseModel):
    """号码风险筛查请求。"""

    phone_number: str = Field(min_length=1, max_length=64)
    direction: CallDirection = CallDirection.UNKNOWN
    verification_status: CallerVerificationStatus = (
        CallerVerificationStatus.NOT_VERIFIED
    )

    @field_validator("phone_number")
    @classmethod
    def normalize_phone_number(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("电话号码不能为空。")
        return cleaned


class CallNumberAnalyzeResponse(BaseModel):
    """号码风险筛查结果。"""

    normalized_number: str
    masked_number: str
    direction: CallDirection
    verification_status: CallerVerificationStatus
    risk_level: CallGuardRiskLevel
    score: int = Field(ge=0, le=100)
    is_trusted_contact: bool
    trusted_contact_name: str | None = None
    summary: str
    signals: list[CallGuardSignal] = Field(default_factory=list)
    actions: list[str] = Field(default_factory=list)
    rule_version: str
    persisted: bool = False


class CallGuardHelpRequest(BaseModel):
    """用户主动发起家庭求助。"""

    family_id: uuid.UUID
    phone_number: str = Field(min_length=1, max_length=64)
    direction: CallDirection = CallDirection.UNKNOWN
    verification_status: CallerVerificationStatus = (
        CallerVerificationStatus.NOT_VERIFIED
    )
    risk_level: CallGuardRiskLevel = CallGuardRiskLevel.HIGH
    score: int = Field(default=100, ge=0, le=100)
    summary: str = Field(min_length=1, max_length=500)

    @field_validator("phone_number", "summary")
    @classmethod
    def strip_text(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("内容不能为空。")
        return cleaned


class CallGuardHelpResponse(BaseModel):
    """家庭求助创建结果。"""

    family_id: uuid.UUID
    event_id: uuid.UUID
    alert_id: uuid.UUID
    recipient_count: int = Field(ge=0)
    sent_count: int = Field(ge=0)
    failed_count: int = Field(ge=0)
    skipped_count: int = Field(ge=0)
    message: str
