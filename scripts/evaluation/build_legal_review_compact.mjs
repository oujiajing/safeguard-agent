import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const root = process.cwd();
const sourcePath = `${root}/outputs/legal-golden-review-v0.1/法规检索黄金评测集-人工审核.xlsx`;
const outputPath = `${root}/outputs/legal-golden-review-v0.1/法规检索黄金评测集-人工审核-精简.xlsx`;
const previewPath = `${root}/outputs/legal-golden-review-v0.1/legal-review-compact-preview.png`;

const source = await SpreadsheetFile.importXlsx(await FileBlob.load(sourcePath));
const sourceSheet = source.worksheets.getItem("审核表");
const sourceRows = sourceSheet.getRange("A7:N43").values;
const rows = sourceRows.map((r) => [r[2], r[3], r[8], r[11] || "待确认"]);

const workbook = Workbook.create();
const sheet = workbook.worksheets.add("审核表");
sheet.showGridLines = false;
sheet.tabColor = "#1F4E78";
sheet.getRange("A1:D1").values = [["问题", "问题类型", "目标条款原文（请直接对照）", "人工判断"]];
sheet.getRange(`A2:D${rows.length + 1}`).values = rows;

const font = "Aptos";
sheet.getRange("A1:D1").format = { fill: "#1F4E78", font: { name: font, size: 10, bold: true, color: "#FFFFFF" }, horizontalAlignment: "center", verticalAlignment: "center", wrapText: true };
sheet.getRange(`A2:D${rows.length + 1}`).format = { font: { name: font, size: 10, color: "#1F2937" }, verticalAlignment: "top", wrapText: true };
sheet.getRange(`D2:D${rows.length + 1}`).format = { fill: "#FFF2CC", font: { name: font, size: 10, bold: true, color: "#7F6000" }, horizontalAlignment: "center", verticalAlignment: "center" };
sheet.getRange(`A1:D${rows.length + 1}`).format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
sheet.getRange("A:A").format.columnWidth = 48;
sheet.getRange("B:B").format.columnWidth = 22;
sheet.getRange("C:C").format.columnWidth = 105;
sheet.getRange("D:D").format.columnWidth = 15;
sheet.getRange("A1:D1").format.rowHeight = 32;
sheet.getRange(`A2:D${rows.length + 1}`).format.rowHeight = 92;
sheet.freezePanes.freezeRows(1);
sheet.getRange(`D2:D${rows.length + 1}`).dataValidation = { rule: { type: "list", values: ["待确认", "正确", "错误", "不确定"] } };
sheet.tables.add(`A1:D${rows.length + 1}`, true, "LegalReviewCompactTable");

workbook.recalculate();
const check = await workbook.inspect({ kind: "table", sheetId: "审核表", range: "A1:D8", include: "values,formulas", tableMaxRows: 8, tableMaxCols: 4, maxChars: 5000 });
console.log(check.ndjson);
const preview = await workbook.render({ sheetName: "审核表", range: "A1:D12", scale: 1, format: "png" });
await fs.writeFile(previewPath, new Uint8Array(await preview.arrayBuffer()));
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(`saved=${outputPath}`);
