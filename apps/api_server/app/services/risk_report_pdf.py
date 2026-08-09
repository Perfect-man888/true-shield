from __future__ import annotations

import re
from html import escape
from io import BytesIO

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import (
    LongTable,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from app.schemas.risk_report import RiskReportSummaryResponse


class RiskReportPdfRenderer:
    """将脱敏风险报告预览数据渲染为 PDF。"""

    FONT_NAME = "STSong-Light"

    def __init__(self) -> None:
        registered = set(pdfmetrics.getRegisteredFontNames())
        if self.FONT_NAME not in registered:
            pdfmetrics.registerFont(
                UnicodeCIDFont(self.FONT_NAME)
            )

    def render(self, report: RiskReportSummaryResponse) -> bytes:
        buffer = BytesIO()
        document = SimpleDocTemplate(
            buffer,
            pagesize=A4,
            rightMargin=16 * mm,
            leftMargin=16 * mm,
            topMargin=18 * mm,
            bottomMargin=18 * mm,
            title=report.report_title,
            author="真信盾",
            subject="脱敏风险安全报告",
        )

        styles = self._build_styles()
        story: list[object] = []

        story.append(
            Paragraph(
                escape(report.report_title),
                styles["TitleCN"],
            )
        )
        story.append(Spacer(1, 4 * mm))
        story.append(
            self._info_table(
                [
                    ["报告编号", report.report_code],
                    ["统计对象", report.subject_name],
                    [
                        "统计范围",
                        "家庭" if report.scope.value == "family" else "个人",
                    ],
                    [
                        "统计周期",
                        f"{report.period_start.isoformat()} 至 {report.period_end.isoformat()}"
                        f"（{report.period_days} 天）",
                    ],
                    [
                        "生成时间",
                        report.generated_at.astimezone().strftime("%Y-%m-%d %H:%M:%S"),
                    ],
                ],
                styles=styles,
                widths=[32 * mm, 130 * mm],
            )
        )
        story.append(Spacer(1, 6 * mm))

        overview = report.overview
        story.extend(
            self._section(
                "一、风险概览",
                self._info_table(
                    [
                        ["周期风险事件", str(overview.total_events)],
                        ["高风险事件", str(overview.high_risk_events)],
                        ["中风险事件", str(overview.medium_risk_events)],
                        ["低风险事件", str(overview.low_risk_events)],
                        ["平均风险分", f"{overview.average_score:.1f}/100"],
                        ["最高风险分", f"{overview.highest_score}/100"],
                        ["家庭告警", str(overview.alert_count)],
                        ["告警完成率", f"{overview.resolution_rate * 100:.1f}%"],
                    ],
                    styles=styles,
                    widths=[38 * mm, 124 * mm],
                ),
                styles,
            )
        )

        story.extend(
            self._section(
                "二、风险等级与检测来源",
                self._two_column_distribution_table(report, styles),
                styles,
            )
        )

        if report.top_signals:
            signal_rows = [["风险证据", "类型", "命中次数"]]
            signal_rows.extend(
                [signal.title, signal.signal_type, str(signal.count)]
                for signal in report.top_signals
            )
            story.extend(
                self._section(
                    "三、主要风险证据",
                    self._styled_table(
                        signal_rows,
                        styles=styles,
                        widths=[92 * mm, 42 * mm, 28 * mm],
                        header=True,
                    ),
                    styles,
                )
            )

        if report.recent_events:
            event_rows = [["时间", "来源", "等级/分数", "脱敏摘要"]]
            for event in report.recent_events:
                evidence = "、".join(event.evidence_titles)
                summary_parts: list[str] = []
                if event.source_excerpt:
                    summary_parts.append(
                        f"内容：{self._compact_text(event.source_excerpt, 120)}"
                    )
                if event.summary:
                    summary_parts.append(
                        f"摘要：{self._compact_text(event.summary, 150)}"
                    )
                if evidence:
                    summary_parts.append(
                        f"证据：{self._compact_text(evidence, 100)}"
                    )
                summary = self._compact_text(
                    "；".join(summary_parts),
                    320,
                )
                event_rows.append(
                    [
                        event.created_at.astimezone().strftime("%m-%d %H:%M"),
                        self._source_label(event.source_type),
                        f"{self._risk_label(event.risk_level)} / {event.risk_score}",
                        summary,
                    ]
                )
            story.extend(
                self._section(
                    "四、重点风险事件",
                    self._styled_table(
                        event_rows,
                        styles=styles,
                        widths=[28 * mm, 24 * mm, 30 * mm, 80 * mm],
                        header=True,
                    ),
                    styles,
                )
            )

        if report.recent_alerts:
            alert_rows = [["时间", "风险", "状态", "告警摘要"]]
            for alert in report.recent_alerts:
                alert_rows.append(
                    [
                        alert.created_at.astimezone().strftime("%m-%d %H:%M"),
                        self._risk_label(alert.risk_level),
                        self._alert_status_label(alert.status),
                        self._compact_text(
                            f"{alert.title}：{alert.summary}",
                            240,
                        ),
                    ]
                )
            story.extend(
                self._section(
                    "五、家庭告警处置",
                    self._styled_table(
                        alert_rows,
                        styles=styles,
                        widths=[28 * mm, 24 * mm, 28 * mm, 82 * mm],
                        header=True,
                    ),
                    styles,
                )
            )

        story.append(PageBreak())
        story.extend(
            self._section(
                "六、安全建议",
        [
            Paragraph(
                "1. 遇到转账、验证码、屏幕共享、安装未知应用或所谓安全账户要求时，"
                "立即暂停操作。",
                styles["BodyCN"],
            ),
            Paragraph(
                "2. 通过官方公开号码、原有聊天渠道或可信联系人独立核验，"
                "不使用对方提供的联系方式。",
                styles["BodyCN"],
            ),
            Paragraph(
                "3. 已发生资金损失时，立即联系银行止付，"
                "并保存脱敏后的必要证据用于报警或平台申诉。",
                styles["BodyCN"],
            ),
                ],
                styles,
            )
        )
        story.extend(
            self._section(
                "隐私说明",
                Paragraph(escape(report.privacy_notice), styles["BodyCN"]),
                styles,
            )
        )
        story.extend(
            self._section(
                "风险说明",
                Paragraph(escape(report.disclaimer), styles["BodyCN"]),
                styles,
            )
        )

        document.build(
            story,
            onFirstPage=lambda canvas, doc: self._draw_footer(
                canvas,
                doc,
                report.report_code,
            ),
            onLaterPages=lambda canvas, doc: self._draw_footer(
                canvas,
                doc,
                report.report_code,
            ),
        )
        return buffer.getvalue()

    def _build_styles(self) -> dict[str, ParagraphStyle]:
        base = getSampleStyleSheet()
        return {
            "TitleCN": ParagraphStyle(
                "TitleCN",
                parent=base["Title"],
                fontName=self.FONT_NAME,
                fontSize=22,
                leading=30,
                alignment=TA_CENTER,
                textColor=colors.HexColor("#172554"),
            ),
            "HeadingCN": ParagraphStyle(
                "HeadingCN",
                parent=base["Heading2"],
                fontName=self.FONT_NAME,
                fontSize=14,
                leading=20,
                spaceBefore=3 * mm,
                spaceAfter=2 * mm,
                textColor=colors.HexColor("#1E3A8A"),
            ),
            "BodyCN": ParagraphStyle(
                "BodyCN",
                parent=base["BodyText"],
                fontName=self.FONT_NAME,
                fontSize=9.5,
                leading=15,
                alignment=TA_LEFT,
                textColor=colors.HexColor("#1F2937"),
            ),
            "CellCN": ParagraphStyle(
                "CellCN",
                parent=base["BodyText"],
                fontName=self.FONT_NAME,
                fontSize=8.5,
                leading=12,
                textColor=colors.HexColor("#111827"),
            ),
            "HeaderCellCN": ParagraphStyle(
                "HeaderCellCN",
                parent=base["BodyText"],
                fontName=self.FONT_NAME,
                fontSize=8.5,
                leading=12,
                textColor=colors.white,
                alignment=TA_CENTER,
            ),
        }

    def _section(
        self,
        title: str,
        content: object,
        styles: dict[str, ParagraphStyle],
    ) -> list[object]:
        items: list[object] = [Paragraph(title, styles["HeadingCN"])]
        if isinstance(content, list):
            items.extend(content)
        else:
            items.append(content)
        items.append(Spacer(1, 3 * mm))
        return items

    def _info_table(
        self,
        rows: list[list[str]],
        *,
        styles: dict[str, ParagraphStyle],
        widths: list[float],
    ) -> Table:
        data = [
            [
                Paragraph(escape(str(key)), styles["CellCN"]),
                Paragraph(escape(str(value)), styles["CellCN"]),
            ]
            for key, value in rows
        ]
        table = Table(data, colWidths=widths, hAlign="LEFT")
        table.setStyle(
            TableStyle(
                [
                    ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#E0E7FF")),
                    ("BACKGROUND", (1, 0), (1, -1), colors.HexColor("#F8FAFC")),
                    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
                    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 7),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                    ("TOPPADDING", (0, 0), (-1, -1), 6),
                    ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                ]
            )
        )
        return table

    def _styled_table(
        self,
        rows: list[list[str]],
        *,
        styles: dict[str, ParagraphStyle],
        widths: list[float],
        header: bool,
    ) -> Table:
        converted: list[list[Paragraph]] = []
        for row_index, row in enumerate(rows):
            style = styles["HeaderCellCN"] if header and row_index == 0 else styles["CellCN"]
            converted.append(
                [Paragraph(escape(str(value)), style) for value in row]
            )
        table = LongTable(
            converted,
            colWidths=widths,
            repeatRows=1 if header else 0,
            splitByRow=1,
            hAlign="LEFT",
        )
        commands = [
            ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#CBD5E1")),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), 5),
            ("RIGHTPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]
        if header:
            commands.append(
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#4F67A3"))
            )
            commands.append(
                ("BACKGROUND", (0, 1), (-1, -1), colors.HexColor("#F8FAFC"))
            )
        table.setStyle(TableStyle(commands))
        return table

    def _two_column_distribution_table(
        self,
        report: RiskReportSummaryResponse,
        styles: dict[str, ParagraphStyle],
    ) -> Table:
        rows = [
            ["风险等级", "数量", "检测来源", "数量"],
            ["高风险", str(report.risk_levels.high), "文本", str(report.source_types.text)],
            ["中风险", str(report.risk_levels.medium), "图片", str(report.source_types.image)],
            ["低风险", str(report.risk_levels.low), "链接", str(report.source_types.url)],
            ["", "", "语音", str(report.source_types.voice)],
            ["", "", "通话号码", str(report.source_types.call)],
            ["", "", "其他", str(report.source_types.other)],
        ]
        return self._styled_table(
            rows,
            styles=styles,
            widths=[42 * mm, 26 * mm, 58 * mm, 36 * mm],
            header=True,
        )

    def _draw_footer(self, canvas, document, report_code: str) -> None:
        canvas.saveState()
        canvas.setFont(self.FONT_NAME, 8)
        canvas.setFillColor(colors.HexColor("#64748B"))
        canvas.drawString(16 * mm, 10 * mm, f"真信盾 · {report_code}")
        canvas.drawRightString(
            A4[0] - 16 * mm,
            10 * mm,
            f"第 {document.page} 页",
        )
        canvas.restoreState()

    @staticmethod
    def _compact_text(value: object, max_length: int) -> str:
        """压缩并截断动态内容，避免单个表格行超过页面高度。"""

        compact = re.sub(r"\s+", " ", str(value or "")).strip()
        if len(compact) <= max_length:
            return compact
        return f"{compact[:max_length].rstrip()}…"

    @staticmethod
    def _risk_label(value: str) -> str:
        return {
            "high": "高风险",
            "medium": "中风险",
            "low": "低风险",
        }.get(value, value)

    @staticmethod
    def _source_label(value: str) -> str:
        return {
            "text": "文本",
            "image": "图片",
            "url": "链接",
            "voice": "语音",
            "call": "通话号码",
        }.get(value, value)

    @staticmethod
    def _alert_status_label(value: str) -> str:
        return {
            "pending": "待确认",
            "acknowledged": "处理中",
            "resolved": "已完成",
            "cancelled": "已取消",
        }.get(value, value)
