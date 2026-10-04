package com.example.invoice.service;

/** OCR word coordinates are normalized to 0..1 so the UI can scale highlights. */
public record OcrWord(String id, String text, float confidence,
		double x, double y, double width, double height) {
}
