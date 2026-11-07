import re
import unicodedata
from datetime import datetime
from decimal import Decimal, InvalidOperation

MONEY = {'subtotal', 'vatAmount', 'totalAmount', 'unitPrice', 'amount', 'taxAmount', 'quantity', 'taxRate'}


def normalize(key, value):
    if value is None or value == '':
        return None
    text = ' '.join(unicodedata.normalize('NFC', str(value)).split())
    if key.endswith('Date'):
        for fmt in ('%Y-%m-%d', '%d/%m/%Y', '%d-%m-%Y'):
            try:
                return datetime.strptime(text, fmt).date().isoformat()
            except ValueError:
                pass
        return text
    if key in MONEY or key.endswith('Amount'):
        try:
            numeric = isinstance(value, (int, float, Decimal))
            percent = text.endswith('%')
            number = text.rstrip('%').strip()
            if not numeric:
                if key == 'taxRate':
                    number = number.replace(',', '.')
                elif ',' in number and '.' in number:
                    number = number.replace('.', '').replace(',', '.') if number.rfind(',') > number.rfind('.') else number.replace(',', '')
                elif ',' in number:
                    number = number.replace(',', '') if re.fullmatch(r'-?\d{1,3}(,\d{3})+', number) else number.replace(',', '.')
                elif re.fullmatch(r'-?\d{1,3}(\.\d{3})+', number):
                    number = number.replace('.', '')
            result = Decimal(number)
            if not result.is_finite():
                return text
            if key == 'taxRate' and (percent or abs(result) > 1):
                result /= 100
            return result
        except InvalidOperation:
            return text
    return text


def prediction(result):
    values = {k: v for k, v in (result.get('invoice') or {}).items() if k not in {'items', 'retailDetails'}}
    for field in result.get('fields') or []:
        values.setdefault(field['fieldName'], field.get('fieldValue'))
    for field in result.get('extraFields') or []:
        values.setdefault(field['name'], field.get('value'))
    values['documentType'] = result.get('documentType')
    values['accountingCategoryCode'] = result.get('accountingCategoryCode')
    return values, (result.get('invoice') or {}).get('items') or []
