package com.example.invoice.service;

import java.util.List;

/** A compact group of adjacent OCR lines, retaining line IDs for traceability. */
public record OcrBlock(String id, int pageNumber, String text, float confidence,
		double x, double y, double width, double height, List<String> lineIds) {
}
