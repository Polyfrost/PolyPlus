// Emoji asset generator for PolyPlus chat emoji.
//
// Produces (into src/main/resources/assets/polyplus):
//   font/emoji.json                - MC bitmap font provider (PUA codepoints -> atlas)
//   textures/emoji/emoji_0.png     - the packed atlas
//   emoji/shortcodes.json          - { alias -> PUA glyph string } consumed by EmojiRegistry
//   ../minecraft/font/default.json - 1.8.9 only: badge.json plus the atlas on BMP PUA codepoints
//
// Run once and commit the outputs; not part of the Gradle build.
//   node generate.mjs
//
// Curated popular subset (~700). Skin-tone variations are ignored (base emoji only).

import sharp from 'sharp';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DS_ROOT = path.join(HERE, 'node_modules', 'emoji-datasource-twitter');
const data = JSON.parse(fs.readFileSync(path.join(DS_ROOT, 'emoji.json'), 'utf8'));
const IMG_DIR = path.join(DS_ROOT, 'img', 'twitter', '64');

const OUT_ASSETS = path.resolve(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'polyplus');
const OUT_FONT = path.join(OUT_ASSETS, 'font', 'emoji.json');
// MC prepends "textures/" to a bitmap provider's `file`, so the atlas must live
// under assets/polyplus/textures/. shortcodes.json is our own data file (loaded
// from the classpath by EmojiRegistry), so it can live anywhere.
const OUT_ATLAS = path.join(OUT_ASSETS, 'textures', 'emoji', 'emoji_0.png');
const OUT_EMOJI_DIR = path.join(OUT_ASSETS, 'emoji');
const OUT_SHORTCODES = path.join(OUT_EMOJI_DIR, 'shortcodes.json');
const OUT_UNICODE = path.join(OUT_EMOJI_DIR, 'unicode.json');
const OUT_LEGACY_FONT = path.resolve(OUT_ASSETS, '..', 'minecraft', 'font', 'default.json');
const BADGE_FONT = path.join(OUT_ASSETS, 'font', 'badge.json');
const FONT_FILE_REF = 'polyplus:emoji/emoji_0.png'; // resolves to textures/emoji/emoji_0.png

// --- atlas / font tuning ---------------------------------------------------
const CELL = 48;        // source cell size in the atlas (px)
const COLS = 32;        // atlas columns  (COLS*CELL must stay <= 2048)
const FONT_HEIGHT = 9;  // in-game glyph height on ~8px chat text
const FONT_ASCENT = 8;
const PUA_START = 0xF0000; // Supplementary PUA-A; avoids badge font E000/E001
const LEGACY_PUA_START = 0xF100;
const LEGACY_PUA_END = 0xF8FF;
const MAX_ATLAS_PX = 2048;

// Per-category head counts (sorted by sort_order). Flags skipped intentionally.
const CATEGORY_CAPS = {
  'Smileys & Emotion': Infinity,
  'People & Body': 120,
  'Animals & Nature': 90,
  'Food & Drink': 90,
  'Activities': 45,
  'Travel & Places': 55,
  'Objects': 80,
  'Symbols': 100,
  'Flags': 0,
  'Component': 0,
};

// Always include these aliases regardless of category caps.
const FORCE_INCLUDE = new Set([
  'sob', 'broken_heart', 'fire', '100', 'tada', 'rocket', 'eyes', 'skull',
  'joy', 'pray', 'muscle', 'clap', 'wave', 'thumbsup', '+1', 'thumbsdown', '-1',
  'ok_hand', 'heart', 'star', 'sparkles', 'hankey', 'poop', 'shit',
  'sweat_smile', 'rofl', 'pleading_face', 'heart_eyes', 'sunglasses', 'wink',
  'cry', 'rage', 'thinking_face', 'moyai', 'raised_hands', 'point_up', 'fist',
  'v', 'partying_face', 'melting_face',
]);

const withImg = data.filter((e) => e.has_img_twitter && e.category !== 'Component');
const bySortOrder = (a, b) => a.sort_order - b.sort_order;

// Select the subset.
const perCatCount = {};
const selected = [];
const seenUnified = new Set();
for (const e of [...withImg].sort(bySortOrder)) {
  const forced = e.short_names.some((n) => FORCE_INCLUDE.has(n));
  const cap = CATEGORY_CAPS[e.category] ?? 0;
  const used = perCatCount[e.category] ?? 0;
  if (!forced && used >= cap) continue;
  if (seenUnified.has(e.unified)) continue;
  seenUnified.add(e.unified);
  perCatCount[e.category] = used + 1;
  selected.push(e);
}
// Deterministic final order: by unified codepoint (stable regeneration).
selected.sort((a, b) => (a.unified < b.unified ? -1 : a.unified > b.unified ? 1 : 0));

console.log(`selected ${selected.length} emoji`);
for (const [c, n] of Object.entries(perCatCount)) console.log(`  ${c}: ${n}`);

const rows = Math.ceil(selected.length / COLS);
const atlasW = COLS * CELL;
const atlasH = rows * CELL;
if (atlasW > MAX_ATLAS_PX || atlasH > MAX_ATLAS_PX) {
  throw new Error(`atlas ${atlasW}x${atlasH} exceeds ${MAX_ATLAS_PX}px; lower caps or CELL/COLS`);
}
console.log(`atlas ${atlasW}x${atlasH} (${COLS}x${rows} cells @ ${CELL}px)`);
if (LEGACY_PUA_START + selected.length - 1 > LEGACY_PUA_END) {
  throw new Error(`${selected.length} emoji overflow the 1.8.9 PUA range`);
}

// Build atlas + maps.
const composites = [];
const shortcodes = {};       // alias -> glyph string
const unicodeMap = {};       // actual emoji string (unicode) -> glyph string
const charsGrid = [];        // rows of concatenated codepoint strings
const legacyCharsGrid = [];
let rowBuf = '';
let legacyRowBuf = '';

for (let i = 0; i < selected.length; i++) {
  const e = selected[i];
  const cp = PUA_START + i;
  const glyph = String.fromCodePoint(cp);
  for (const alias of e.short_names) {
    if (shortcodes[alias] === undefined) shortcodes[alias] = glyph;
  }
  // The actual emoji character(s) from the unified codepoint sequence.
  const emojiStr = e.unified.split('-').map((h) => String.fromCodePoint(parseInt(h, 16))).join('');
  unicodeMap[emojiStr] = glyph;
  const col = i % COLS;
  const row = (i - col) / COLS;
  composites.push({
    input: await sharp(path.join(IMG_DIR, e.image))
      .resize(CELL, CELL, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
      .png()
      .toBuffer(),
    left: col * CELL,
    top: row * CELL,
  });
  rowBuf += glyph;
  legacyRowBuf += String.fromCharCode(LEGACY_PUA_START + i);
  if (col === COLS - 1) {
    charsGrid.push(rowBuf);
    legacyCharsGrid.push(legacyRowBuf);
    rowBuf = '';
    legacyRowBuf = '';
  }
}
if (selected.length % COLS !== 0) {
  // Pad the final row to a full COLS *codepoints* with U+0000, which MC's bitmap
  // provider treats as an empty (no-glyph) cell. Count codepoints, not UTF-16
  // units: our PUA glyphs are supplementary, so each is 2 code units but 1
  // codepoint, and every row must have exactly COLS codepoints.
  const pad = COLS - (selected.length % COLS);
  rowBuf += '\u0000'.repeat(pad);
  charsGrid.push(rowBuf);
  legacyRowBuf += '\u0000'.repeat(pad);
  legacyCharsGrid.push(legacyRowBuf);
}

fs.mkdirSync(path.dirname(OUT_ATLAS), { recursive: true });
fs.mkdirSync(OUT_EMOJI_DIR, { recursive: true });
fs.mkdirSync(path.dirname(OUT_FONT), { recursive: true });

await sharp({ create: { width: atlasW, height: atlasH, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
  .composite(composites)
  .png()
  .toFile(OUT_ATLAS);
console.log(`wrote ${path.relative(process.cwd(), OUT_ATLAS)}`);

const fontJson = {
  providers: [
    {
      type: 'bitmap',
      file: FONT_FILE_REF,
      ascent: FONT_ASCENT,
      height: FONT_HEIGHT,
      chars: charsGrid,
    },
  ],
};
fs.writeFileSync(OUT_FONT, JSON.stringify(fontJson, null, 2) + '\n');
console.log(`wrote ${path.relative(process.cwd(), OUT_FONT)}`);

const legacyFontJson = {
  providers: [
    ...JSON.parse(fs.readFileSync(BADGE_FONT, 'utf8')).providers,
    { ...fontJson.providers[0], chars: legacyCharsGrid },
  ],
};
fs.mkdirSync(path.dirname(OUT_LEGACY_FONT), { recursive: true });
fs.writeFileSync(OUT_LEGACY_FONT, JSON.stringify(legacyFontJson, null, 2) + '\n');
console.log(`wrote ${path.relative(process.cwd(), OUT_LEGACY_FONT)}`);

// Sort shortcodes alphabetically for stable diffs.
const sortedShort = Object.fromEntries(Object.entries(shortcodes).sort(([a], [b]) => (a < b ? -1 : 1)));
fs.writeFileSync(OUT_SHORTCODES, JSON.stringify(sortedShort, null, 0) + '\n');
console.log(`wrote ${path.relative(process.cwd(), OUT_SHORTCODES)} (${Object.keys(sortedShort).length} aliases)`);

// Unicode map: also add a variant with the U+FE0F emoji variation selector stripped, so both
// the fully-qualified (picker output) and unqualified forms match. Longer keys win at match time.
const FE0F = String.fromCodePoint(0xFE0F);
for (const [emoji, glyph] of Object.entries({ ...unicodeMap })) {
  if (emoji.includes(FE0F)) {
    const bare = emoji.split(FE0F).join('');
    if (bare.length > 0 && unicodeMap[bare] === undefined) unicodeMap[bare] = glyph;
  }
}
// Sort keys by length descending so callers matching this map try longer (ZWJ) sequences first.
const sortedUnicode = Object.fromEntries(
  Object.entries(unicodeMap).sort(([a], [b]) => b.length - a.length || (a < b ? -1 : 1)),
);
fs.writeFileSync(OUT_UNICODE, JSON.stringify(sortedUnicode, null, 0) + '\n');
console.log(`wrote ${path.relative(process.cwd(), OUT_UNICODE)} (${Object.keys(sortedUnicode).length} sequences)`);
