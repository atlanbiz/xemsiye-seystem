"""Regenerate ios/SolarPulse/Resources/Localizable.xcstrings from the web dictionaries
(src/i18n/*.ts) plus iOS-only strings (ios/scripts/ios_strings.json).
Usage: ios/scripts/sync-strings.sh"""
import json, re, sys, os, glob
S = os.path.dirname(os.path.abspath(__file__))
web = json.load(sys.stdin)
extra = json.load(open(os.path.join(S, 'ios_strings.json')))
langs = ['en', 'ug', 'ar', 'tr']
keys = {}
for k in web['en']:
    keys[k] = {l: web[l].get(k, web['en'][k]) for l in langs}
for k, v in extra.items():
    if k in keys:
        print('extra overrides web key (kept web):', k)
        continue
    keys[k] = v
# Latin digits only
for k, v in keys.items():
    for l, s in v.items():
        if re.search('[٠-٩۰-۹]', s):
            print('NON-LATIN DIGITS', k, l, s); sys.exit(1)
cat = {"sourceLanguage": "en", "strings": {}, "version": "1.0"}
for k in sorted(keys):
    cat["strings"][k] = {
        "extractionState": "manual",
        "localizations": {l: {"stringUnit": {"state": "translated", "value": keys[k][l]}} for l in langs},
    }
out = sys.argv[1]  # catalog path
with open(out, 'w', encoding='utf-8') as f:
    json.dump(cat, f, ensure_ascii=False, indent=2)
    f.write('\n')
print('keys:', len(keys))
# check Swift usage
src = sys.argv[2]
used = set()
for p in glob.glob(os.path.join(src, '**', '*.swift'), recursive=True):
    txt = open(p, encoding='utf-8').read()
    for m in re.finditer(r'"([a-z][a-zA-Z0-9]*(?:\.[a-zA-Z0-9_]+)+)"', txt):
        used.add(m.group(1))
ignore = {'map.fill', 'solarpulse.demo.session', 'solarpulse.alerts', 'api.open-meteo.com'}
prefixes = {k.split('.')[0] for k in keys}
missing = sorted(k for k in used if k.split('.')[0] in prefixes and k not in keys and k not in ignore)
print('missing:', missing)
# dynamic prefixes
dyn = {'status.': ['active','idle','offline','maintenance','online','warning','open','in_progress','resolved','paid','pending','overdue'],
       'siteType.': ['residential','commercial','industrial','utility'], 'devType.': ['inverter','battery','panel','meter','sensor'],
       'prio.': ['low','medium','high','critical'], 'rep.': ['energy','financial','devices','maintenance','environment'],
       'alerts.m.': ['site_yield_below','site_offline','device_efficiency_below','device_health_below','device_offline_minutes','invoice_overdue_days'],
       'alerts.d.': ['site_yield_below','site_offline','device_efficiency_below','device_health_below','device_offline_minutes','invoice_overdue_days'],
       'alerts.sev.': ['warning','danger'], 'int.v.': ['solaredge','fusionsolar','webhook'], 'int.status.': ['pending','ok','error'],
       'wx.': ['sunny','partly','cloudy','fog','rain','snow','storm'], 'hero.': ['excellent','good','attention'], 'role.': ['admin','operator','viewer']}
dmiss = [p+s for p, ss in dyn.items() for s in ss if p+s not in keys]
print('dynamic missing:', dmiss)
