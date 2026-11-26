// Quiblo — a free, open source IPTV player.
// Copyright (C) 2026 The Quiblo Authors
// Licensed under the GNU General Public License v3.0 or later. See LICENSE.
//
// Renders sting.html to intro.wav and dive.wav, normalised together to -1.5 dBFS on the intro.
//   node render.js "$PWD/sting.html" out && for k in intro dive; do
//     ffmpeg -i out/$k.wav -codec:a libmp3lame -b:a 192k ../../feature/designsystem/src/main/res/raw/splash_$k.mp3; done
const { chromium } = require('playwright'); const fs = require('fs');
function wav(L, R, rate, path, fixed) {
  const n = L.length, b = Buffer.alloc(44 + n * 4);
  b.write('RIFF', 0); b.writeUInt32LE(36 + n * 4, 4); b.write('WAVE', 8); b.write('fmt ', 12);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(2, 22); b.writeUInt32LE(rate, 24);
  b.writeUInt32LE(rate * 4, 28); b.writeUInt16LE(4, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(n * 4, 40);
  let peak = 0; for (let i = 0; i < n; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  const gain = fixed || Math.pow(10, -1.5 / 20) / peak;
  for (let i = 0; i < n; i++) {
    b.writeInt16LE(Math.round(Math.max(-1, Math.min(1, L[i] * gain)) * 32767), 44 + i * 4);
    b.writeInt16LE(Math.round(Math.max(-1, Math.min(1, R[i] * gain)) * 32767), 46 + i * 4);
  }
  fs.writeFileSync(path, b); return { peakDb: 20 * Math.log10(peak), gain };
}
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('file://' + process.argv[2]);
  const r = await p.evaluate(() => window.go());
  const f = (s) => new Float32Array(Buffer.from(s, 'base64').buffer.slice(0));
  const a = wav(f(r.intro.L), f(r.intro.R), r.intro.rate, process.argv[3] + '/intro.wav'); console.log('intro', JSON.stringify(a));
  console.log('dive', JSON.stringify(wav(f(r.dive.L), f(r.dive.R), r.dive.rate, process.argv[3] + '/dive.wav', a.gain)));
  await b.close();
})();
