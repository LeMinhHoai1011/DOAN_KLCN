from .field_normalizer import normalize, prediction
from .ground_truth_manager import core_fields, ITEM, HEADER


def scores(tp, fp, fn):
    return dict(TP=tp, FP=fp, FN=fn, Precision=tp/(tp+fp) if tp+fp else None,
                Recall=tp/(tp+fn) if tp+fn else None, F1=2*tp/(2*tp+fp+fn) if 2*tp+fp+fn else None)


def compare(key, value, cell):
    if cell['state'] == 'UNREADABLE':
        return dict(match_status='UNREADABLE', TP=0, FP=0, FN=0)
    a = normalize(key, value)
    b = normalize(key, cell.get('value')) if cell['state'] == 'PRESENT' else None
    match = a == b
    return dict(match_status='MATCH' if match else 'MISMATCH', TP=int(a is not None and match),
                FP=int(a is not None and not match), FN=int(b is not None and not match))


def assignment(weights):
    """Hungarian maximum-weight one-to-one assignment, padded with dummy rows/columns."""
    rows, cols = len(weights), len(weights[0]) if weights else 0
    n = max(rows, cols)
    if not n:
        return []
    u, v, p, way = [0]*(n+1), [0]*(n+1), [0]*(n+1), [0]*(n+1)
    for i in range(1, n+1):
        p[0], j0 = i, 0
        minimum, used = [float('inf')]*(n+1), [False]*(n+1)
        while True:
            used[j0], i0, delta, j1 = True, p[j0], float('inf'), 0
            for j in range(1, n+1):
                if used[j]:
                    continue
                w = weights[i0-1][j-1] if i0 <= rows and j <= cols else 0
                cur = -w-u[i0]-v[j]
                if cur < minimum[j]:
                    minimum[j], way[j] = cur, j0
                if minimum[j] < delta:
                    delta, j1 = minimum[j], j
            for j in range(n+1):
                if used[j]:
                    u[p[j]] += delta
                    v[j] -= delta
                else:
                    minimum[j] -= delta
            j0 = j1
            if not p[j0]:
                break
        while True:
            j1 = way[j0]
            p[j0], j0 = p[j1], j1
            if not j0:
                break
    return [(p[j]-1, j-1) for j in range(1, n+1) if 0 < p[j] <= rows and j <= cols and weights[p[j]-1][j-1] > 0]


def evaluate_items(predicted, truth):
    # Exact normalized product name is the gate. Other exact cells break ties among duplicate names.
    def weight(a, b):
        name = b['productName']
        if name['state'] != 'PRESENT' or normalize('productName', a.get('productName')) != normalize('productName', name['value']):
            return 0
        return 1 + sum(compare(k, a.get(k), b[k])['match_status'] == 'MATCH' for k in ITEM)
    weights = [[weight(a, b) for b in truth] for a in predicted]
    pairs = assignment(weights) if truth else []
    matched_a, matched_b = {a for a, _ in pairs}, {b for _, b in pairs}
    pairs += [(a, None) for a in range(len(predicted)) if a not in matched_a]
    pairs += [(None, b) for b in range(len(truth)) if b not in matched_b]
    rows = []
    for a, b in pairs:
        for key in ITEM:
            cell = truth[b][key] if b is not None else {'state':'NOT_PRESENT', 'value':None}
            value = predicted[a].get(key) if a is not None else None
            rows.append(dict(prediction_row=a, truth_row=b, row_status='EXTRA' if b is None else 'MISSING' if a is None else 'MATCHED',
                             field_name=key, ai_value=value, ground_truth_value=cell.get('value'), **compare(key,value,cell)))
    return rows


def evaluate_ai(result, gt):
    if not gt or gt.get('status') != 'CONFIRMED':
        return None
    values, predicted_items = prediction(result)
    core = set(core_fields(gt))
    rows = []
    keys=set(gt['scope']) | {k for k,v in values.items() if v is not None and v != ''}
    for key in sorted(keys):
        cell = gt['fields'].get(key, {'state':'NOT_PRESENT','value':None})
        group = 'Core' if key in core else 'Optional' if key in {'documentType','accountingCategoryCode'} or key in HEADER else 'Extended'
        rows.append(dict(field_name=key, field_group=group, ai_value=values.get(key), ground_truth_value=cell.get('value'), **compare(key, values.get(key), cell)))
    core_rows = [r for r in rows if r['field_group'] == 'Core']
    complete = bool(core_rows) and all(r['match_status'] != 'UNREADABLE' for r in core_rows)
    count = lambda rs: scores(*(sum(r[k] for r in rs) for k in ('TP','FP','FN')))
    readable = [r for r in rows if r['match_status'] != 'UNREADABLE']
    items = evaluate_items(predicted_items, gt['items'])
    return dict(fields=rows, items=items, overall=count(rows), core=count(core_rows) if complete else None,
                table=count(items), core_complete=complete, unreadable=sum(r['match_status']=='UNREADABLE' for r in rows),
                field_exact_accuracy=sum(r['match_status']=='MATCH' for r in readable)/len(readable) if readable else None)
