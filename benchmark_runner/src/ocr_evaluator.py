import unicodedata


def distance(a, b):
    if a==b:return 0
    if not a:return len(b)
    if not b:return len(a)
    previous = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        current = [i]
        for j, y in enumerate(b, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j-1] + (x != y)))
        previous = current
    return previous[-1]


def evaluate_ocr(text, truth):
    if not truth or not truth.get('checked'):
        return None
    a = unicodedata.normalize('NFC', truth['text']).replace('\r\n', '\n')
    b = unicodedata.normalize('NFC', text or '').replace('\r\n', '\n')
    chars, words = distance(a, b), distance(a.split(), b.split())
    return dict(char_errors=chars, char_count=len(a), word_errors=words, word_count=len(a.split()),
                CER=chars/len(a) if a else None, WER=words/len(a.split()) if a.split() else None)
