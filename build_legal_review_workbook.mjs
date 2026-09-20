import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const root = process.cwd();
const dataPath = `${root}/outputs/legal-golden-review-v0.1/legal-review-data.json`;
const outputDir = `${root}/outputs/legal-golden-review-v0.1`;
const data = JSON.parse(await fs.readFile(dataPath, "utf8"));

const workbook = Workbook.create();
const review = workbook.worksheets.add("审核表");
const readme = workbook.worksheets.add("说明");
const font = "Aptos";

review.showGridLines = false;
readme.showGridLines = false;
review.tabColor = "#1F4E78";
readme.tabColor = "#A6A6A6";

review.getRange("A1:N1").values = [["法规检索黄金评测集 v0.1｜单人快速确认表", null, null, null, null, null, null, null, null, null, null, null, null, null]];
review.getRange("A2:N2").values = [["知识库：30份PDF法律法规｜集合：123123｜快照：2026-09-16｜只需在“人工判断”列选择：待确认 / 正确 / 错误 / 不确定", null, null, null, null, null, null, null, null, null, null, null, null, null]];
review.getRange("A4:F4").values = [["总题数", null, "待确认", null, "正确", null]];
review.getRange("A5:F5").values = [[null, null, null, null, null, null]];
review.getRange("B4").formulas = [["=COUNTA(A7:A43)"]];
review.getRange("D4").formulas = [["=COUNTIF(L7:L43,\"待确认\")"]];
review.getRange("F4").formulas = [["=COUNTIF(L7:L43,\"正确\")"]];

const headers = ["题号", "评测分组", "问题", "问题类型", "可回答性", "目标文档", "标准号", "目标条款号", "目标条款原文（请直接对照）", "参考答案", "证据组", "人工判断", "备注", "chunk_id"];
review.getRange("A6:N6").values = [headers];

const rows = data.cases.map((c) => {
  const groups = c.gold_evidence_groups || [];
  const evidence = groups.flatMap((g) => (g.positive_chunks || []).map((p) => ({ ...p, group_id: g.group_id, requirement: g.requirement })));
  const docs = [...new Set(evidence.map((p) => p.document_title || ""))].join("\n");
  const standards = [...new Set(evidence.map((p) => p.standard_no || "未填"))].join("\n");
  const clauses = evidence.map((p) => `${p.group_id}｜${p.clause_no || "未填"}`).join("\n");
  const texts = evidence.map((p) => `${p.group_id}（${p.clause_no || "未填"}）：${p.content || "（当前知识库未返回原文）"}`).join("\n\n");
  const ids = evidence.map((p) => p.chunk_id).join("\n");
  const answer = c.gold_answer || c.expected_behavior || "当前知识库证据不足";
  return [c.case_id.replace("LRG-SEED-", ""), c.evaluation_bucket, c.question, c.question_type, c.answerability, docs, standards, clauses, texts, answer, groups.map((g) => `${g.group_id}:${g.requirement}`).join("\n"), "待确认", "", ids];
});
review.getRange(`A7:N${6 + rows.length}`).values = rows;

review.getRange("A1:N1").format = { font: { name: font, size: 14, bold: true, color: "#1F2937" } };
review.getRange("A2:N2").format = { font: { name: font, size: 10, italic: true, color: "#5B6573" }, wrapText: false };
review.getRange("A4:F4").format = { fill: "#D9EAF7", font: { name: font, size: 10, bold: true, color: "#1F2937" }, horizontalAlignment: "center", verticalAlignment: "center" };
review.getRange("A5:F5").format = { font: { name: font, size: 12, bold: true, color: "#1F4E78" }, horizontalAlignment: "center" };
review.getRange("A6:N6").format = { fill: "#1F4E78", font: { name: font, size: 10, bold: true, color: "#FFFFFF" }, horizontalAlignment: "center", verticalAlignment: "center", wrapText: true };
review.getRange(`A7:N${6 + rows.length}`).format = { font: { name: font, size: 10, color: "#1F2937" }, verticalAlignment: "top", wrapText: true };
review.getRange(`L7:L${6 + rows.length}`).format = { fill: "#FFF2CC", font: { name: font, size: 10, bold: true, color: "#7F6000" }, horizontalAlignment: "center", verticalAlignment: "center", wrapText: true };
review.getRange(`A6:N${6 + rows.length}`).format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
review.getRange("A1:N1").format.rowHeight = 24;
review.getRange("A2:N2").format.rowHeight = 22;
review.getRange("A6:N6").format.rowHeight = 32;
review.getRange(`A7:N${6 + rows.length}`).format.rowHeight = 86;

const widths = { A: 9, B: 14, C: 42, D: 20, E: 14, F: 30, G: 16, H: 18, I: 82, J: 48, K: 28, L: 14, M: 22, N: 24 };
for (const [col, width] of Object.entries(widths)) review.getRange(`${col}:${col}`).format.columnWidth = width;
review.freezePanes.freezeRows(6);
review.freezePanes.freezeColumns(2);
review.getRange(`L7:L${6 + rows.length}`).dataValidation = { rule: { type: "list", values: ["待确认", "正确", "错误", "不确定"] } };
review.tables.add(`A6:N${6 + rows.length}`, true, "LegalReviewTable");

readme.getRange("A1:B1").values = [["使用说明", null]];
readme.getRange("A3:B10").values = [
  ["审核目标", "判断问题与右侧法规条款是否对应。"],
  ["人工操作", "只修改“审核表”L列的人工判断：待确认、正确、错误或不确定。"],
  ["复合题", "必须确认问题中的每个隐患都被对应证据组覆盖。"],
  ["不确定题", "不要猜测；保留为不确定，不进入冻结测试集。"],
  ["目标条款原文", "已从当前知识库按chunk_id读取，供逐题直接对照。"],
  ["数据集配额", "单条款20题；数值5题；复合10题；不可回答2题。"],
  ["当前状态", "37题均为草案，人工确认完成后再冻结。"],
  ["语料指纹", "1d94b8474ff34ca236a200f5d9a9f651"],
];
readme.getRange("A1:B1").format = { font: { name: font, size: 14, bold: true, color: "#1F2937" } };
readme.getRange("A3:A10").format = { fill: "#EAF2F8", font: { name: font, size: 10, bold: true, color: "#1F2937" }, verticalAlignment: "top" };
readme.getRange("B3:B10").format = { font: { name: font, size: 10, color: "#1F2937" }, wrapText: true, verticalAlignment: "top" };
readme.getRange("A1:B10").format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
readme.getRange("A:A").format.columnWidth = 18;
readme.getRange("B:B").format.columnWidth = 76;
readme.getRange("A3:B10").format.rowHeight = 30;

workbook.recalculate();
const inspection = await workbook.inspect({ kind: "table", sheetId: "审核表", range: "A1:N43", include: "values,formulas", tableMaxRows: 8, tableMaxCols: 14, maxChars: 7000 });
console.log(inspection.ndjson);
const preview = await workbook.render({ sheetName: "审核表", range: "A1:N18", scale: 1, format: "png" });
await fs.writeFile(`${outputDir}/legal-review-preview.png`, new Uint8Array(await preview.arrayBuffer()));
const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(`${outputDir}/法规检索黄金评测集-人工审核.xlsx`);
console.log(`saved=${outputDir}/法规检索黄金评测集-人工审核.xlsx`);
