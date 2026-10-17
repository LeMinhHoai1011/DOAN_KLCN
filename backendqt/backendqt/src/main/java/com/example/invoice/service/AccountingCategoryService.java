package com.example.invoice.service;

import com.example.invoice.dto.accounting.*;
import com.example.invoice.entity.*;
import com.example.invoice.exception.*;
import com.example.invoice.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountingCategoryService {
 private final AccountingCategoryRepository categories;
 private final CompanyRepository companies;
 private final UserService users;
 private final ClassificationRepository classifications;
 private final ClassificationCorrectionRepository corrections;
 private final AccountingEntryRepository entries;
 private final FinancialTransactionRepository transactions;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

 @Transactional(readOnly=true)
 public List<AccountingCategoryResponse> findAll(Long companyId, boolean includeInactive, AccountingCategoryType type, Authentication auth) {
   User actor=users.loadCurrent(auth);
   if (!isAdmin(actor) && (auth == null || auth.getAuthorities().stream().noneMatch(a -> "PERMISSION_ACCOUNTING_VIEW".equals(a.getAuthority()))))
     throw new AccessDeniedException("Bạn không có quyền xem danh mục kế toán");
   Long target=resolveCompany(actor,companyId);
   if (!companies.existsById(target)) throw new ResourceNotFoundException("Không tìm thấy công ty");
   return categories.search(target,includeInactive,type).stream().map(this::response).toList();
 }

 @Transactional
 public AccountingCategoryResponse create(AccountingCategoryRequest request, Authentication auth) {
   User actor=users.loadCurrent(auth); requireAdmin(actor);
   Long target=resolveCompany(actor,request.companyId());
   Company company=companies.findByIdForUpdate(target).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công ty"));
   String code=validate(request);
   if (request.categoryType()==null) throw new BadRequestException("CATEGORY_TYPE_REQUIRED: Chọn Thu hoặc Chi cho danh mục mới");
   requireUnique(target,code,null);
   var category=new AccountingCategory(); category.setCompany(company); category.setCategoryCode(code);
   category.setCategoryName(request.categoryName().trim()); category.setDescription(request.description());
   category.setCategoryType(request.categoryType()); category.setActive(request.active());
   return response(categories.save(category));
 }

 @Transactional
 public AccountingCategoryResponse update(Long id, AccountingCategoryRequest request, Authentication auth) {
   User actor=users.loadCurrent(auth); requireAdmin(actor);
   AccountingCategory initial=categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục"));
   if(initial.getCompany()==null) throw new BadRequestException("CATEGORY_COMPANY_REQUIRED: Danh mục cũ chưa được gán công ty; cần xử lý riêng");
   Long target=initial.getCompany().getId();
   if(request.companyId()!=null && !target.equals(request.companyId())) throw new BadRequestException("Không thể chuyển danh mục sang công ty khác");
   companies.findByIdForUpdate(target).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công ty"));
   AccountingCategory category=categories.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục"));
   if(entityManager!=null) entityManager.refresh(category,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
   if(category.getCompany()==null || !target.equals(category.getCompany().getId())) throw new BadRequestException("Company của danh mục đã thay đổi; tải lại trước khi sửa");
   String code=validate(request); boolean referenced=referenced(id);
   if(referenced && !category.getCategoryCode().equals(code)) throw new BadRequestException("CATEGORY_IN_USE: Không đổi mã danh mục đã được tham chiếu");
   AccountingCategoryType next=request.categoryType()==null ? category.getCategoryType() : request.categoryType();
   if(category.getCategoryType()!=null && category.getCategoryType()!=next && referenced)
     throw new BadRequestException("CATEGORY_IN_USE: Không đổi Thu/Chi của danh mục đã được tham chiếu");
   if(next!=null && next!=category.getCategoryType() && transactions.existsByAccountingCategoryIdAndTransactionTypeNot(id,next.name()))
     throw new BadRequestException("CATEGORY_TYPE_CONFLICT: Thu/Chi không khớp giao dịch đang tham chiếu");
   requireUnique(target,code,id);
   category.setCategoryCode(code); category.setCategoryName(request.categoryName().trim()); category.setDescription(request.description());
   category.setCategoryType(next); category.setActive(request.active());
   return response(category);
 }

 private String validate(AccountingCategoryRequest r) {
   String code=r.categoryCode()==null ? "" : r.categoryCode().trim().toUpperCase(Locale.ROOT);
   if(!code.matches("[A-Z][A-Z0-9_]{0,49}") || r.categoryName()==null || r.categoryName().isBlank()
      || r.categoryName().length()>200 || (r.description()!=null && r.description().length()>255) || r.active()==null)
      throw new BadRequestException("INVALID_ACCOUNTING_CATEGORY: Thông tin danh mục không hợp lệ");
   return code;
 }
 private void requireUnique(Long company,String code,Long exclude) {
   if(categories.duplicateCode(company,code,exclude)) throw new BadRequestException("DUPLICATE_CATEGORY_CODE: Mã danh mục đã tồn tại trong công ty");
 }
 private boolean referenced(Long id) {
   return classifications.existsByAccountingCategoryId(id) || corrections.existsByOldCategoryIdOrNewCategoryId(id,id)
       || entries.existsByCategoryId(id) || transactions.existsByAccountingCategoryId(id);
 }
 private Long resolveCompany(User user,Long requested) {
   Long own=user.getCompany()==null ? null : user.getCompany().getId();
   if(!isAdmin(user) && requested!=null && !requested.equals(own)) throw new AccessDeniedException("Bạn không thể truy cập công ty khác");
   Long id=requested==null ? own : requested;
   if(id==null || id<=0) throw new BadRequestException("COMPANY_REQUIRED: Vui lòng chọn công ty");
   return id;
 }
 private boolean isAdmin(User actor) { return actor.getUserRoles().stream().anyMatch(a -> a.getRole().isActive() && "ADMIN".equals(a.getRole().getCode())); }
 private void requireAdmin(User actor) { if(!isAdmin(actor)) throw new AccessDeniedException("Chỉ quản trị viên được quản lý danh mục"); }
 private AccountingCategoryResponse response(AccountingCategory c) {
   return new AccountingCategoryResponse(c.getId(),c.getCategoryCode(),c.getCategoryName(),c.getDescription(),
       c.getCompany()==null ? null : c.getCompany().getId(),c.getCategoryType(),c.isActive());
 }
}
