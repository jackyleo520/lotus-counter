const fs = require('fs');
const vm = require('vm');
const html = fs.readFileSync('index.html', 'utf8');

// 配对所有 <script> / </script>，校验体积最大的那一段（即主应用脚本）。
// 注意：文件注释里可能出现字面量 "<script>"，不能简单用 lastIndexOf。
const openTag = '<script>';
const closeTag = '</script>';
const starts = [];
let o = 0;
while ((o = html.indexOf(openTag, o)) >= 0) { starts.push(o); o += openTag.length; }
const ends = [];
let c = 0;
while ((c = html.indexOf(closeTag, c)) >= 0) { ends.push(c + closeTag.length); c += closeTag.length; }

let best = '';
let bestLen = -1;
for (const s of starts) {
    const e = ends.find(x => x > s);
    if (e == null) continue;
    const code = html.substring(s + openTag.length, e - closeTag.length);
    if (code.length > bestLen) { bestLen = code.length; best = code; }
}

if (!best) {
    console.log('JS Syntax Error: no script block found');
    process.exit(1);
}

try {
    new vm.Script(best, { filename: 'inline.js' });
    console.log('JS Syntax: OK (' + best.length + ' chars)');
} catch (e) {
    console.log('JS Syntax Error:', e.message);
    const lines = best.split('\n');
    const m = /inline\.js:(\d+)/.exec(e.stack);
    if (m) {
        const ln = parseInt(m[1], 10) - 1;
        const from = Math.max(0, ln - 3);
        const to = Math.min(lines.length, ln + 4);
        console.log('--- around line ' + (ln + 1) + ' ---');
        for (let i = from; i < to; i++) console.log((i + 1) + ': ' + lines[i]);
    }
    process.exit(1);
}
