package com.example.invoice.exception;
public class RbacConflictException extends RuntimeException {
    public RbacConflictException() { super("Dữ liệu phân quyền đã thay đổi. Hãy tải lại trước khi lưu."); }
}
