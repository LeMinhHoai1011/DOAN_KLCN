package com.example.invoice.service;

import java.util.List;

/** A reconstructed reading line. Coordinates use the same normalized 0..1 space as {@link OcrWord}. */
public record OcrLine(String id, int pageNumber, String text, float confidence,
		double x, double y, double width, double height, List<String> wordIds) {
}
