package com.example.invoice.service;

public record PdfPageImage(int pageNumber, byte[] bytes, String contentType) {
}
