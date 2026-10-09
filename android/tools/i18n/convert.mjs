// Converts the web dictionaries (src/i18n/*.ts) + Android-only extras into Android string resources.
// Usage (from the repo root): node --experimental-strip-types android/tools/i18n/convert.mjs .
// Then validate: python3 android/tools/i18n/check-resources.py .
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { extras } from './extras.mjs'

const repo = process.argv[2]
const LANGS = ['en', 'ug', 'ar', 'tr']
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'spi18n-'))
for (const l of LANGS) {
  const src = fs.readFileSync(path.join(repo, 'src/i18n', l + '.ts'), 'utf8').replace(/from '\.\/(\w+)'/g, "from './$1.ts'")
  fs.writeFileSync(path.join(tmp, l + '.ts'), src)
}
const dicts = {}
for (const l of LANGS) dicts[l] = (await import(path.join(tmp, l + '.ts')))[l]

const resName = (k) => k.replace(/[^A-Za-z0-9_]/g, '_')
const names = new Map()
const all = {} // lang -> name -> text
const order = []
const source = {}
function add(key, perLang, origin) {
  const n = resName(key)
  if (names.has(n) && names.get(n) !== key) throw new Error(`resource name collision: ${key} vs ${names.get(n)}`)
  if (!names.has(n)) order.push(n)
  names.set(n, key)
  source[n] = origin
  for (const l of LANGS) {
    const v = perLang[l]
    if (v == null) throw new Error(`missing ${l} for ${key}`)
    ;(all[l] ??= {})[n] = v
  }
}
for (const key of Object.keys(dicts.en)) {
  add(key, Object.fromEntries(LANGS.map((l) => [l, dicts[l][key] ?? null])), 'web')
}
for (const [key, v] of Object.entries(extras)) {
  if (dicts.en[key] !== undefined) { console.warn('extra shadows web key, using web:', key); continue }
  add(key, v, 'android')
}

// placeholder order is defined by the English text
const phOrder = {}
for (const n of order) {
  const ph = [...all.en[n].matchAll(/\{(\w+)\}/g)].map((m) => m[1])
  phOrder[n] = [...new Set(ph)]
}

function escape(text, n) {
  const ph = phOrder[n]
  let s = text
  if (ph.length) s = s.replace(/%/g, '%%')
  s = s.replace(/\\/g, '\\\\').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  s = s.replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, '\\n')
  if (/^[@?]/.test(s)) s = '\\' + s
  if (/^\s|\s$/.test(s)) s = '"' + s + '"'
  for (const [i, p] of ph.entries()) s = s.split(`{${p}}`).join(`%${i + 1}$s`)
  const unknown = s.match(/\{\w+\}/)
  if (unknown) throw new Error(`placeholder ${unknown[0]} in ${n} not present in English`)
  return s
}

const resDir = path.join(repo, 'android/app/src/main/res')
for (const l of LANGS) {
  const dir = path.join(resDir, l === 'en' ? 'values' : `values-${l}`)
  fs.mkdirSync(dir, { recursive: true })
  let out = '<?xml version="1.0" encoding="utf-8"?>\n'
  out += '<!-- GENERATED from src/i18n/' + l + '.ts (web, source of truth) + Android extras. Do not edit by hand;\n     re-run android/tools/i18n/convert.mjs (see android/README.md). -->\n'
  out += '<resources xmlns:tools="http://schemas.android.com/tools"' + (l === 'en' ? '' : ' tools:ignore="MissingTranslation"') + '>\n'
  if (l === 'en') out += '    <string name="app_name" translatable="false">SolarPulse</string>\n'
  for (const n of order) {
    const raw = all[l][n]
    const formatted = phOrder[n].length === 0 && raw.includes('%') ? ' formatted="false"' : ''
    out += `    <string name="${n}"${formatted}>${escape(raw, n)}</string>\n`
  }
  out += '</resources>\n'
  fs.writeFileSync(path.join(dir, 'strings.xml'), out)
}
console.log(`wrote ${order.length} strings (${order.filter((n) => source[n] === 'web').length} from web, ${order.filter((n) => source[n] === 'android').length} Android extras) x ${LANGS.length} languages`)
