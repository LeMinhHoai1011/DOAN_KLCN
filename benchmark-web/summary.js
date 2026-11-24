const benchmarkSummary = {
  "run_id": "both-raster-001",
  "documents": 64,
  "success": 60,
  "failed": 4,
  "confirmed_gt_documents": 60,
  "Core_Micro": {
    "TP": 311,
    "FP": 100,
    "FN": 169,
    "Precision": 0.7566909975669099,
    "Recall": 0.6479166666666667,
    "F1": 0.6980920314253648
  },
  "groups": [
    {
      "name": "Chứng từ",
      "eligible": 60,
      "TP": 352,
      "FP": 172,
      "FN": 248,
      "Precision": 0.6717557251908397,
      "Recall": 0.5866666666666667,
      "F1": 0.6263345195729537
    },
    {
      "name": "Hàng hóa",
      "eligible": 60,
      "TP": 511,
      "FP": 436,
      "FN": 847,
      "Precision": 0.5395987328405492,
      "Recall": 0.37628865979381443,
      "F1": 0.44338394793926245
    },
    {
      "name": "Tiền",
      "eligible": 60,
      "TP": 154,
      "FP": 13,
      "FN": 26,
      "Precision": 0.9221556886227545,
      "Recall": 0.8555555555555555,
      "F1": 0.8876080691642652
    },
    {
      "name": "Thanh toán",
      "eligible": 60,
      "TP": 41,
      "FP": 14,
      "FN": 19,
      "Precision": 0.7454545454545455,
      "Recall": 0.6833333333333333,
      "F1": 0.7130434782608696
    },
    {
      "name": "Kế toán - đề xuất",
      "eligible": 0,
      "TP": null,
      "FP": null,
      "FN": null,
      "Precision": null,
      "Recall": null,
      "F1": null
    }
  ]
};
