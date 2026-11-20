// Converts MuscleMapJS (MIT, github.com/abdofallah/MuscleMapJS) body path data into the app's asset.
// Usage: node tools/musclemap-to-json.js <MuscleMapJS/src> app/src/main/assets/musclemap.json
'use strict';
const fs = require('fs');
const path = require('path');
const [src, out] = process.argv.slice(2);
const VIEW_BOXES = { // from src/core/body-path-data.ts
  'male-front': [0, 95, 727, 1280], 'male-back': [718, 95, 727, 1280],
  'female-front': [0, 0, 650, 1450], 'female-back': [823, 0, 650, 1450],
};
const result = {};
for (const key of Object.keys(VIEW_BOXES)) {
  const ts = fs.readFileSync(path.join(src, 'data', `${key}-paths.ts`), 'utf8');
  // The file is `export const xPaths: BodyPartPathData[] = [ ...object literals... ];`
  const literal = ts.slice(ts.indexOf('= [') + 2, ts.lastIndexOf(']') + 1);
  const parts = new Function(`return ${literal}`)();
  result[key] = {
    viewBox: VIEW_BOXES[key],
    parts: parts.map(p => ({ slug: p.slug, paths: [...(p.common || []), ...(p.left || []), ...(p.right || [])] })),
  };
}
fs.writeFileSync(out, JSON.stringify(result));
console.log(Object.entries(result).map(([k, v]) => `${k}: ${v.parts.length} parts`).join(', '));
