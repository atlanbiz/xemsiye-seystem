#!/usr/bin/env python3
"""Validates android/app resources: XML well-formed, string keys complete in all locales,
placeholders consistent, every R.string/R.drawable/@resource reference resolvable,
format args passed only to strings that take them."""
import glob, os, re, sys, xml.etree.ElementTree as ET
root = sys.argv[1]
app = os.path.join(root, 'android/app/src/main')
res = os.path.join(app, 'res')
errors = []

xmls = glob.glob(os.path.join(res, '**/*.xml'), recursive=True) + [os.path.join(app, 'AndroidManifest.xml')]
for f in xmls:
    try: ET.parse(f)
    except ET.ParseError as e: errors.append(f'XML parse error {f}: {e}')
print(f'{len(xmls)} XML files parsed')

locales = {'values': 'en', 'values-ug': 'ug', 'values-ar': 'ar', 'values-tr': 'tr'}
strings = {}
for d, l in locales.items():
    t = ET.parse(os.path.join(res, d, 'strings.xml')).getroot()
    strings[l] = {s.get('name'): (s.text or '') for s in t.findall('string')}
base = set(strings['en'])
for l, m in strings.items():
    if l == 'en': continue
    missing = base - set(m) - {'app_name'}
    extra = set(m) - base
    if missing: errors.append(f'{l}: missing {sorted(missing)[:10]}')
    if extra: errors.append(f'{l}: extra {sorted(extra)[:10]}')
ph = re.compile(r'%(\d+)\$s')
for k, v in strings['en'].items():
    p = sorted(set(ph.findall(v)))
    for l in ('ug', 'ar', 'tr'):
        q = sorted(set(ph.findall(strings[l].get(k, ''))))
        if q != p and strings[l].get(k, '') != '': errors.append(f'placeholder mismatch {k} en={p} {l}={q}')
    # unescaped apostrophes / quotes (aapt2 rejects them)
for l, m in strings.items():
    for k, v in m.items():
        if re.search(r"(?<!\\)'", v) and not (v.startswith('"') and v.endswith('"')): errors.append(f"unescaped ' in {l}:{k}")
        if re.search(r'(?<!\\)"', v.strip('"')): errors.append(f'unescaped " in {l}:{k}')
        if re.search(r'%(?![0-9]+\$s|%)', v) and ph.search(v): errors.append(f'stray % in formatted {l}:{k}')
print(f'{len(base)} string keys x {len(strings)} locales')

kt = glob.glob(os.path.join(app, 'kotlin/**/*.kt'), recursive=True)
src = {f: open(f, encoding='utf-8').read() for f in kt}
used = set()
for f, s in src.items():
    for k in re.findall(r'R\.string\.([A-Za-z0-9_]+)', s):
        used.add(k)
        for l, m in strings.items():
            if k not in m: errors.append(f'{os.path.basename(f)}: R.string.{k} missing in {l}')
    for k in re.findall(r'R\.drawable\.([A-Za-z0-9_]+)', s):
        if not glob.glob(os.path.join(res, 'drawable*', k + '.*')): errors.append(f'R.drawable.{k} missing')
    # stringResource / getString with args must target formatted strings, and vice versa
    for m in re.finditer(r'(?:stringResource|getString)\(\s*R\.string\.([A-Za-z0-9_]+)\s*(,)?', s):
        k, has_args = m.group(1), bool(m.group(2))
        n = len(set(ph.findall(strings['en'].get(k, ''))))
        if has_args and n == 0: errors.append(f'{os.path.basename(f)}: {k} given args but has no placeholder')
        if not has_args and n > 0: errors.append(f'{os.path.basename(f)}: {k} has {n} placeholder(s) but no args')
print(f'{len(kt)} Kotlin files, {len(used)} distinct string keys referenced')

for f in xmls:
    s = open(f, encoding='utf-8').read()
    for typ, name in re.findall(r'@(drawable|mipmap|xml|style|color|string)/([A-Za-z0-9_.]+)', s):
        if typ in ('drawable', 'mipmap', 'xml'):
            if not glob.glob(os.path.join(res, typ + '*', name + '.xml')) and not glob.glob(os.path.join(res, typ + '*', name + '.png')):
                errors.append(f'{os.path.basename(f)}: @{typ}/{name} not found')
        elif typ == 'color':
            if not re.search(rf'<color name="{name}"', ''.join(open(p).read() for p in glob.glob(os.path.join(res, 'values*/colors.xml')))):
                errors.append(f'@color/{name} not found')
        elif typ == 'string' and name not in strings['en'] and name != 'app_name':
            errors.append(f'@string/{name} not found')
        elif typ == 'style' and not re.search(rf'<style name="{re.escape(name)}"', open(os.path.join(res, 'values/themes.xml')).read()):
            errors.append(f'@style/{name} not found')

if errors:
    print('\n'.join('ERROR ' + e for e in errors)); sys.exit(1)
print('OK: resources consistent')
