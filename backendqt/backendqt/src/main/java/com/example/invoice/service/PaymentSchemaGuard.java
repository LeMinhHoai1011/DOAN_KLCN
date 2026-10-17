package com.example.invoice.service;
import java.util.*;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Inspect existing unique constraints only; never change schema or guess migration state. */
@Service @RequiredArgsConstructor
public class PaymentSchemaGuard {
 private final DataSource dataSource;
 public record Limits(boolean documentUnique,boolean invoiceUnique) {}
 public Limits inspect(){
  try{return new JdbcTemplate(dataSource).execute((ConnectionCallback<Limits>) connection->{
   var meta=connection.getMetaData();String schema=connection.getSchema();boolean document=false,invoice=false,inspected=false;
   for(String table:List.of("financial_transactions","FINANCIAL_TRANSACTIONS")){
    var indexes=new HashMap<String,List<String>>();
    try(var rows=meta.getIndexInfo(connection.getCatalog(),schema,table,true,false)){
     while(rows.next()){String name=rows.getString("INDEX_NAME"),column=rows.getString("COLUMN_NAME");if(name!=null&&column!=null&&!rows.getBoolean("NON_UNIQUE")){inspected=true;indexes.computeIfAbsent(name,k->new ArrayList<>()).add(column.toLowerCase(Locale.ROOT));}}
    }
    for(var columns:indexes.values()){if(columns.size()==1){document|="document_id".equals(columns.getFirst());invoice|="invoice_id".equals(columns.getFirst());}}
   }
   if(!inspected)throw new java.sql.SQLException("Cannot verify financial transaction index metadata");
   return new Limits(document,invoice);
  });}catch(Exception error){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_SCHEMA_CHECK_FAILED: Chưa kiểm tra được ràng buộc thanh toán; không được ghi phiếu.");}
 }
 public boolean blocks(PaymentLinkService.Link link,List<com.example.invoice.entity.FinancialTransaction> rows,Long exclude){
  if(link.document()==null)return false;var limits=inspect();
  return rows.stream().filter(t->!Objects.equals(t.getId(),exclude)).anyMatch(t->
   (limits.documentUnique()&&t.getDocument()!=null&&t.getDocument().getId().equals(link.document().getId()))||
   (limits.invoiceUnique()&&link.invoice()!=null&&t.getInvoice()!=null&&t.getInvoice().getId().equals(link.invoice().getId())));
 }
 public void requireWritable(PaymentLinkService.Link link,List<com.example.invoice.entity.FinancialTransaction> rows,Long exclude){
  if(blocks(link,rows,exclude))throw new ResponseStatusException(HttpStatus.CONFLICT,"PAYMENT_SCHEMA_REVIEW_REQUIRED: Ràng buộc hiện tại chưa cho phép thêm phiếu cho hồ sơ này; cần phê duyệt migration riêng.");
 }
}
