const metrics = ["Precision", "Recall", "F1"];
const formatPercent = new Intl.NumberFormat("vi-VN", {
  style: "percent",
  maximumFractionDigits: 1,
});

function percent(value) {
  return value == null ? "—" : formatPercent.format(value);
}

function append(parent, tag, text = "", className = "") {
  const element = document.createElement(tag);
  element.textContent = text;
  element.className = className;
  parent.append(element);
  return element;
}

const report = benchmarkSummary;
document.getElementById("coverage").textContent =
  `Run ${report.run_id} · ${report.documents} chứng từ · ${report.confirmed_gt_documents} mẫu có GT · ${report.success} xử lý thành công · ${report.failed} lỗi`;

const core = document.getElementById("core");
for (const metric of metrics) {
  const card = append(core, "div");
  append(card, "span", metric);
  append(card, "strong", percent(report.Core_Micro[metric]));
}

const chart = document.getElementById("chart");
const scores = document.getElementById("scores");
for (const group of report.groups) {
  const cluster = append(chart, "div", "", "group");
  const bars = append(cluster, "div", "", "bars");
  for (const metric of metrics) {
    const column = append(bars, "div", "", "column");
    append(column, "span", percent(group[metric]));
    const fill = append(column, "div", "", `fill ${metric.toLowerCase()}`);
    fill.style.height = `${(group[metric] ?? 0) * 140}px`;
  }
  append(cluster, "strong", group.name);
  append(cluster, "small", `${group.eligible} mẫu đủ GT`);

  const row = append(scores, "tr");
  append(row, "th", group.name).scope = "row";
  for (const key of ["eligible", "TP", "FP", "FN"]) {
    append(row, "td", String(group[key] ?? "—"));
  }
  for (const metric of metrics) {
    append(row, "td", percent(group[metric]));
  }
}
