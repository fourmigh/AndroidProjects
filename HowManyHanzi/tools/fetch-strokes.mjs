// 下载 hanzi-writer-data 并只把 8105 字表中命中的字模写入 Android assets。
// 输出：app/src/main/assets/strokes/<码点>.json（格式 {"s":["M ...", ...],"m":[[[x,y],...],...]}）
// s 是笔画轮廓，m 是笔画中心线（逐笔动画沿中心线书写）。
// 用法：node tools/fetch-strokes.mjs
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const CACHE = path.join(__dirname, ".cache");
const PKG_VERSION = "2.0.1";
const TGZ = path.join(CACHE, `hanzi-writer-data-${PKG_VERSION}.tgz`);
const SRC = path.join(CACHE, "package");
const OUT = path.join(ROOT, "app/src/main/assets/strokes");
const DATA = path.join(ROOT, "app/src/main/assets/data");

fs.mkdirSync(CACHE, { recursive: true });
fs.mkdirSync(OUT, { recursive: true });

if (!fs.existsSync(SRC)) {
    if (!fs.existsSync(TGZ)) {
        const url = `https://registry.npmjs.org/hanzi-writer-data/-/hanzi-writer-data-${PKG_VERSION}.tgz`;
        console.log(`下载 ${url}`);
        execFileSync("curl", ["-L", "--fail", "--retry", "3", "-o", TGZ, url], { stdio: "inherit" });
    }
    console.log("解包字模…");
    execFileSync("tar", ["-xzf", TGZ, "-C", CACHE], { stdio: "inherit" });
}

if (!fs.existsSync(path.join(DATA, "chars.tsv"))) {
    console.error("缺少 assets/data/chars.tsv，请先运行 node tools/build-data.mjs");
    process.exit(1);
}

const chars = fs
    .readFileSync(path.join(DATA, "chars.tsv"), "utf8")
    .split("\n")
    .filter(Boolean)
    .map((line) => line.split("\t")[0]);

let hit = 0;
const missing = [];
let bytes = 0;
// 清空旧产物，文件名统一用码点十六进制，避免 APK 打包非 ASCII 资源名。
for (const f of fs.readdirSync(OUT)) {
    if (f.endsWith(".json")) fs.unlinkSync(path.join(OUT, f));
}
for (let i = 0; i < chars.length; i++) {
    const ch = chars[i];
    const file = path.join(SRC, `${ch}.json`);
    if (!fs.existsSync(file)) {
        missing.push(ch);
        continue;
    }
    const raw = JSON.parse(fs.readFileSync(file, "utf8"));
    if (!Array.isArray(raw.strokes) || !raw.strokes.length) {
        missing.push(ch);
        continue;
    }
    const slim = JSON.stringify({ s: raw.strokes, m: raw.medians || [] });
    fs.writeFileSync(path.join(OUT, `${ch.codePointAt(0).toString(16)}.json`), slim, "utf8");
    bytes += Buffer.byteLength(slim);
    hit++;
    if ((i + 1) % 500 === 0) process.stdout.write(`  ${i + 1}/${chars.length}\r`);
}

fs.writeFileSync(
    path.join(__dirname, "report-missing.txt"),
    `缺少笔顺字模（${missing.length} 字）：\n${missing.join("")}\n`,
    "utf8"
);
console.log(`\n字表 ${chars.length} 字，写入 ${hit} 个笔顺字模，缺失 ${missing.length} 字`);
console.log(`字模总计 ${(bytes / 1024 / 1024).toFixed(1)} MB，清单见 tools/report-missing.txt`);
