from datetime import datetime, timezone
from pathlib import Path
from .checkpoint_manager import read, write_new, digest
from .field_normalizer import prediction

CORE = {
 'VAT_INVOICE': ['invoiceSeries', 'invoiceNumber', 'invoiceDate', 'sellerName', 'sellerTaxCode', 'subtotal', 'vatAmount', 'totalAmount'],
 'RETAIL_INVOICE': ['sellerName', 'invoiceDate', 'totalAmount'],
 'PAYMENT_VOUCHER': ['voucherDate', 'payerName', 'payeeName', 'paymentReason', 'totalAmount', 'currency'],
 'RECEIPT_VOUCHER': ['voucherDate', 'payerName', 'recipientName', 'receiptReason', 'totalAmount', 'currency'],
 'RECEIPT': ['receiptDate', 'issuerName', 'receiptKind'],
 'CONTRACT': ['contractDate', 'partyAName', 'partyBName', 'subject'],
}
HEADER = ['invoiceSeries','invoiceNumber','invoiceDate','signDate','sellerName','sellerTaxCode','sellerAddress','sellerPhone','buyerName','buyerTaxCode','buyerAddress','subtotal','vatAmount','totalAmount','paymentMethod','amountInWords','taxAuthorityCode']
ITEM = ['productName', 'quantity', 'unit', 'unitPrice', 'taxRate', 'taxAmount', 'amount']
STATES = {'PRESENT', 'NOT_PRESENT', 'UNREADABLE'}


def core_fields(gt):
    fields = list(CORE[gt['document_type']])
    if gt['document_type'] == 'RECEIPT' and gt.get('fields', {}).get('receiptKind', {}).get('value') == 'PAYMENT':
        fields += ['payerName', 'totalAmount', 'currency']
    return fields


def make_draft(sample, result, document_type):
    if document_type not in CORE:
        raise ValueError('Unsupported document type')
    values, items = prediction(result or {})
    scope = sorted(set(CORE[document_type] + HEADER if document_type.endswith('_INVOICE') else CORE[document_type])
                   | set(values) | {'documentType', 'accountingCategoryCode'})
    return dict(schema_version='1.0', sample_id=sample['sample_id'], original_filename=sample['original_filename'],
                file_hash=sample['file_hash'], document_type=document_type, status='DRAFT', version=0,
                scope=scope, fields={k: {'state': 'UNCHECKED', 'value': values.get(k)} for k in scope},
                items_reviewed=False, items=[{k: {'state': 'UNCHECKED', 'value': item.get(k)} for k in ITEM} for item in items],
                ocr={'checked': False, 'text': ''}, confirmed_by=None, confirmed_at=None)


def validate(gt):
    import re
    if not re.fullmatch(r'S_[A-Za-z0-9_]+', gt.get('sample_id','')):
        raise ValueError('Unsafe sample ID')
    if gt.get('schema_version') != '1.0' or gt.get('document_type') not in CORE:
        raise ValueError('Unsupported GT schema/type')
    required = set(core_fields(gt)) | {'documentType', 'accountingCategoryCode'}
    if gt['document_type'].endswith('_INVOICE'):
        required |= set(HEADER)
    if not required.issubset(set(gt.get('scope', []))) or set(gt['scope']) != set(gt.get('fields', {})):
        raise ValueError('Incomplete field scope')
    cells = list(gt['fields'].values())
    if not gt.get('items_reviewed'):
        raise ValueError('Items must be checked against original, including an empty table')
    for row in gt.get('items', []):
        if set(row) != set(ITEM):
            raise ValueError('Incomplete item scope')
        cells.extend(row.values())
    for cell in cells:
        if cell.get('state') not in STATES:
            raise ValueError('Every scoped field/cell requires PRESENT/NOT_PRESENT/UNREADABLE')
        if cell['state'] == 'PRESENT' and (cell.get('value') is None or cell['value'] == ''):
            raise ValueError('PRESENT requires a value')
        if cell['state'] != 'PRESENT' and cell.get('value') not in (None, ''):
            raise ValueError('Absent/unreadable cells must not have a value')
    type_cell = gt['fields']['documentType']
    if type_cell['state'] != 'PRESENT' or type_cell['value'] != gt['document_type']:
        raise ValueError('Document type must be independently reviewed')
    if not isinstance(gt.get('ocr', {}).get('text'), str):
        raise ValueError('OCR text must be a string')


def confirm(draft, directory, identity):
    return confirm_value(read(draft), directory, identity)


def confirm_value(value, directory, identity):
    import copy
    gt = copy.deepcopy(value)
    validate(gt)
    if not identity.get('id') or not identity.get('username'):
        raise ValueError('Authenticated identity required')
    root = Path(directory) / gt['sample_id']
    root.mkdir(parents=True, exist_ok=True)
    versions = sorted(root.glob('v*.json'))
    gt['version'] = max([read(p)['version'] for p in versions], default=0) + 1
    gt['previous_hash'] = digest(read(versions[-1])) if versions else None
    gt.update(status='CONFIRMED', confirmed_by=identity, confirmed_at=datetime.now(timezone.utc).isoformat())
    path = root / f"v{gt['version']:06d}.json"
    write_new(path, gt)
    return path


def latest(directory, sample):
    files = sorted((Path(directory) / sample['sample_id']).glob('v*.json'))
    if not files:
        return None
    gt = read(files[-1])
    validate(gt)
    if gt.get('status') != 'CONFIRMED' or not gt.get('confirmed_by') or not gt.get('confirmed_at'):
        return None
    if gt['file_hash'] != sample['file_hash']:
        raise ValueError('GT file hash mismatch')
    return gt
