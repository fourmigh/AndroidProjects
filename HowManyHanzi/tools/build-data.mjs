// 把微信小程序的数据与 cnchar 预计算结果导出成 Android 端可直接读取的紧凑 TSV。
// 输出：app/src/main/assets/data/{chars.tsv,tiers.tsv,poly.txt}
// 用法：node tools/build-data.mjs
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const MINI = process.env.HOW_MANY_HANZI_SRC
    ? path.resolve(process.env.HOW_MANY_HANZI_SRC)
    : "F:/Github/WeChatMiniProgram/how-many-hanzi";
const OUT = path.join(ROOT, "app/src/main/assets/data");

const requireMini = createRequire(path.join(MINI, "package.json"));
const data = requireMini("./miniprogram/lib/chars.data.js");

const cnchar = requireMini("cnchar");
cnchar.use(requireMini("cnchar-order"));
cnchar.use(requireMini("cnchar-radical"));
cnchar.use(requireMini("cnchar-words"));

const clean = (v) => String(v == null ? "" : v).replace(/[\t\r\n]/g, " ").trim();
const cleanList = (list, dedupe = true) => {
    const arr = Array.isArray(list) ? list : [list];
    const seen = new Set();
    const out = [];
    for (const raw of arr) {
        const s = String(raw == null ? "" : raw)
            .split("|")[0]
            .replace(/\d+$/, "")
            .replace(/[\t\r\n,，;；]/g, "")
            .trim();
        if (!s || s === "null" || s === "undefined" || s === "[object Object]") continue;
        if (dedupe && seen.has(s)) continue;
        seen.add(s);
        out.push(s);
    }
    return out;
};

const parseMap = (str) => {
    const map = {};
    if (!str) return map;
    for (const line of String(str).split("\n")) {
        const i = line.indexOf("|");
        if (i <= 0) continue;
        map[line.slice(0, i)] = line.slice(i + 1);
    }
    return map;
};
const parseCsvMap = (str) => {
    const map = {};
    if (!str) return map;
    for (const line of String(str).split("\n")) {
        const i = line.indexOf("|");
        if (i <= 0) continue;
        const list = line.slice(i + 1).split(",").filter(Boolean);
        if (list.length) map[line.slice(0, i)] = list;
    }
    return map;
};

const explainMap = parseMap(data.explains);
const radicalMap = parseMap(data.radicals);
const strokeMap = parseMap(data.strokes);
const extraStrokeMap = parseCsvMap(data.extraStrokes);
const extraWordMap = parseCsvMap(data.extraWords);

const readingMap = {};
for (const line of String(data.readings || "").split("\n")) {
    const i = line.indexOf("|");
    if (i <= 0) continue;
    readingMap[line.slice(0, i)] = line
        .slice(i + 1)
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
}

const senseMap = {};
for (const line of String(data.senses || "").split("\n")) {
    const i = line.indexOf("|");
    if (i <= 0) continue;
    const entries = [];
    for (const seg of line.slice(i + 1).split(";")) {
        const c = seg.indexOf(":");
        if (c <= 0) continue;
        const p = seg.slice(0, c).trim();
        const m = seg.slice(c + 1).trim();
        if (p && m) entries.push([p, m]);
    }
    if (entries.length >= 2) senseMap[line.slice(0, i)] = entries;
}

const tierChars = data.tierChars.map((s) => Array.from(s));
const allChars = tierChars.flat();
const tierOf = new Map();
tierChars.forEach((list, tier) => list.forEach((ch) => tierOf.set(ch, tier)));

function strokeNames(ch) {
    const extra = extraStrokeMap[ch];
    if (extra && extra.length) return cleanList(extra, false);
    try {
        const arr = cnchar.stroke(ch, "order", "name");
        if (!arr || !arr.length) return [];
        const first = arr[0];
        return cleanList(Array.isArray(first) ? first : [first], false);
    } catch (e) {
        return [];
    }
}

function words(ch) {
    const extra = extraWordMap[ch];
    if (extra && extra.length) {
        return cleanList(extra).slice(0, 24);
    }
    try {
        const arr = cnchar.words(ch);
        if (!Array.isArray(arr)) return [];
        return cleanList(arr).slice(0, 24);
    } catch (e) {
        return [];
    }
}

fs.mkdirSync(OUT, { recursive: true });

const tiers = data.tierNames.map((name, i) => `${clean(name)}\t${tierChars[i].join("")}`);

const lines = [];
let withStrokes = 0;
let withWords = 0;
let totalSenses = 0;
for (let i = 0; i < allChars.length; i++) {
    const ch = allChars[i];
    const readings = readingMap[ch] || [];
    const senses = senseMap[ch] || [];
    const names = strokeNames(ch);
    const ws = words(ch);
    if (names.length) withStrokes++;
    if (ws.length) withWords++;
    totalSenses += senses.length;

    lines.push([
        ch,
        clean(explainMap[ch] || ""),
        clean(radicalMap[ch] || ""),
        Number(strokeMap[ch]) || 0,
        readings.join(","),
        senses.map(([p, m]) => `${p}:${m}`).join(";").replace(/[\t\r\n]/g, " "),
        names.join(","),
        ws.join(","),
        tierOf.get(ch) ?? 0,
    ].join("\t"));

    if ((i + 1) % 1000 === 0) process.stdout.write(`  ${i + 1}/${allChars.length}\r`);
}

fs.writeFileSync(path.join(OUT, "chars.tsv"), lines.join("\n") + "\n", "utf8");
fs.writeFileSync(path.join(OUT, "tiers.tsv"), tiers.join("\n") + "\n", "utf8");
fs.writeFileSync(path.join(OUT, "poly.txt"), data.polyphones || "", "utf8");

const kb = (p) => (fs.statSync(p).size / 1024).toFixed(1) + " KB";
console.log(`\n字表 ${allChars.length} 字，笔顺名覆盖 ${withStrokes}，组词覆盖 ${withWords}，义项 ${totalSenses} 条`);
console.log(`chars.tsv  ${kb(path.join(OUT, "chars.tsv"))}`);
console.log(`tiers.tsv  ${kb(path.join(OUT, "tiers.tsv"))}`);
console.log(`poly.txt   ${kb(path.join(OUT, "poly.txt"))}`);
