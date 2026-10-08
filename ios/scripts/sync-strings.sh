#!/usr/bin/env sh
# Rebuild Localizable.xcstrings after the web dictionaries (src/i18n/*.ts) change.
# Run from the repository root after `npm install`.
set -e
cd "$(dirname "$0")/../.."
printf "import { en } from './src/i18n/en'\nimport { ug } from './src/i18n/ug'\nimport { ar } from './src/i18n/ar'\nimport { tr } from './src/i18n/tr'\nconsole.log(JSON.stringify({ en, ug, ar, tr }))\n" \
  | node_modules/.bin/esbuild --bundle --platform=node --format=esm --loader=ts --log-level=warning --resolve-extensions=.ts \
  | node --input-type=module \
  | python3 ios/scripts/build_xcstrings.py ios/SolarPulse/Resources/Localizable.xcstrings ios/SolarPulse
