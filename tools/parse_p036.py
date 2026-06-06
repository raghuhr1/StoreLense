import openpyxl
from collections import defaultdict
import json, glob, os

DATA_DIR = r'd:\StoreLense\P036\P036'

all_styles = {}
all_barcodes = defaultdict(set)
depts = set()

files = sorted(glob.glob(os.path.join(DATA_DIR, '*.xlsx')))
print(f'Files found: {len(files)}')

# Read all files to aggregate expected qty per style (use max across days)
style_expected = defaultdict(int)

for fpath in files:
    fname = os.path.basename(fpath)
    wb = openpyxl.load_workbook(fpath, read_only=True, data_only=True)
    ws = wb.active
    rows = 0
    for i, row in enumerate(ws.iter_rows(values_only=True)):
        if i == 0:
            continue
        if not row[0]:
            continue
        dept    = str(row[0]).strip() if row[0] else ''
        style   = str(row[1]).strip() if row[1] else ''
        barcode = str(row[2]).strip() if row[2] else ''
        desc    = str(row[4]).strip() if row[4] else ''
        color   = str(row[5]).strip() if row[5] else ''
        size    = str(row[6]).strip() if row[6] else ''
        cost    = row[7] or 0
        price   = row[8] or 0
        expected= int(row[9]) if row[9] else 0
        scan    = int(row[10]) if row[10] else 0

        if dept:
            depts.add(dept)
        if style and style not in all_styles:
            # Extract clean product name: remove style code prefix
            clean_name = desc.replace(style, '').strip()
            # Remove trailing size/color noise, keep meaningful part
            clean_name = clean_name.strip()
            all_styles[style] = {
                'dept': dept,
                'desc': clean_name,
                'color': color,
                'size': size,
                'cost': float(cost) if cost else 0,
                'price': float(price) if price else 0,
                'barcode': barcode if barcode not in ('', '?', 'None') else '',
            }
        if style:
            style_expected[style] = max(style_expected[style], expected)

        if barcode and barcode not in ('', '?', 'None') and len(barcode) >= 8:
            all_barcodes[style].add(barcode)

        rows += 1
    wb.close()
    print(f'  {fname}: {rows} rows')

print()
print(f'=== DEPARTMENTS ({len(depts)}) ===')
for d in sorted(depts):
    print(f'  {d}')

print()
print(f'=== UNIQUE STYLES: {len(all_styles)} ===')

# Group by dept for summary
from collections import Counter
dept_counts = Counter(v['dept'] for v in all_styles.values())
print('By department:')
for dept, cnt in sorted(dept_counts.items(), key=lambda x: -x[1]):
    print(f'  {dept}: {cnt} styles')

print()
print('=== SAMPLE STYLES (first 40) ===')
for k, v in list(all_styles.items())[:40]:
    print(f'  {k} | {v["dept"]} | {v["desc"]} | price={v["price"]} | barcodes={len(all_barcodes[k])}')

# Save full data as JSON for seed script generation
out = {
    'store_code': 'P036',
    'store_name': 'Pantaloons P036',
    'departments': sorted(depts),
    'styles': all_styles,
    'style_expected': dict(style_expected),
    'barcodes': {k: list(v) for k, v in all_barcodes.items()},
}
out_path = r'd:\StoreLense\tools\p036_data.json'
with open(out_path, 'w') as f:
    json.dump(out, f, indent=2)
print()
print(f'Full data saved to: {out_path}')
print(f'Total styles with barcodes: {sum(1 for v in all_barcodes.values() if v)}')
print(f'Total barcodes: {sum(len(v) for v in all_barcodes.values())}')
