const express = require("express");
const multer = require("multer");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const app = express();
app.disable("x-powered-by");
app.use(helmet());
app.use(express.json({ limit: "32kb" }));
app.use("/api/", rateLimit({ windowMs: 60_000, limit: 120, standardHeaders: true, legacyHeaders: false }));

const PORT = Number(process.env.PORT || 8080);
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, "data");
const UPLOAD_DIR = path.join(DATA_DIR, "uploads");
const CATALOG_PATH = path.join(DATA_DIR, "catalog.json");
const PUBLISH_TOKEN = process.env.PUBLISH_TOKEN;
const MAX_BYTES = Number(process.env.MAX_UPLOAD_BYTES || 268435456);
const CONSOLES = new Set(["Game Boy", "Game Boy Color", "Game Boy Advance", "NES", "SNES", "Nintendo 64", "Nintendo DS", "Sega Master System", "Mega Drive / Genesis", "PlayStation", "PSP", "CHIP-8", "Other"]);

fs.mkdirSync(UPLOAD_DIR, { recursive: true });
if (!fs.existsSync(CATALOG_PATH)) fs.writeFileSync(CATALOG_PATH, "[]\n");

function readCatalog() {
  try {
    const value = JSON.parse(fs.readFileSync(CATALOG_PATH, "utf8"));
    return Array.isArray(value) ? value : [];
  } catch {
    throw new Error("Catalogue data is invalid; restore catalog.json from backup.");
  }
}
function writeCatalog(items) {
  const temp = CATALOG_PATH + ".tmp";
  fs.writeFileSync(temp, JSON.stringify(items, null, 2) + "\n");
  fs.renameSync(temp, CATALOG_PATH);
}
function safeName(value) {
  return String(value || "game").normalize("NFKD").replace(/[^a-zA-Z0-9._-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 100) || "game";
}
function requirePublisher(req, res, next) {
  if (!PUBLISH_TOKEN) return res.status(503).json({ error: "Publishing is disabled: configure PUBLISH_TOKEN on the server." });
  const supplied = (req.get("authorization") || "").replace(/^Bearer\s+/i, "");
  const a = Buffer.from(supplied);
  const b = Buffer.from(PUBLISH_TOKEN);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return res.status(401).json({ error: "Invalid publisher token." });
  next();
}
const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => cb(null, crypto.randomUUID() + "-" + safeName(file.originalname))
});
const upload = multer({
  storage,
  limits: { fileSize: MAX_BYTES, files: 1 },
  fileFilter: (_req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    const allowed = new Set([".gb", ".gbc", ".gba", ".nes", ".smc", ".sfc", ".n64", ".z64", ".v64", ".nds", ".iso", ".cue", ".bin", ".pbp", ".ch8", ".c8", ".chip8", ".zip", ".7z", ".rom"]);
    if (!allowed.has(ext)) return cb(new Error("Unsupported file extension. Upload a supported homebrew ROM/archive."));
    cb(null, true);
  }
});

app.get("/health", (_req, res) => res.json({ status: "ok", service: "gamehub-cloud-api" }));

app.get("/api/games", (_req, res) => {
  try {
    const games = readCatalog().map(({ id, title, console, description, version, author, fileName, size, sha256, publishedAt }) => ({
      id, title, console, description, version, author, fileName, size, sha256, publishedAt,
      downloadUrl: "/api/games/" + encodeURIComponent(id) + "/download"
    }));
    res.set("Cache-Control", "public, max-age=60");
    res.json({ games });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

app.post("/api/publish", requirePublisher, (req, res) => {
  upload.single("rom")(req, res, async (err) => {
    if (err) {
      const code = err.code === "LIMIT_FILE_SIZE" ? 413 : 400;
      return res.status(code).json({ error: err.message || "Upload failed." });
    }
    if (!req.file) return res.status(400).json({ error: "ROM file is required." });
    const title = String(req.body.title || "").trim().slice(0, 100);
    const consoleName = String(req.body.console || "").trim();
    const author = String(req.body.author || "").trim().slice(0, 80);
    const version = String(req.body.version || "1.0").trim().slice(0, 32);
    const description = String(req.body.description || "").trim().slice(0, 1000);
    const rightsConfirmed = req.body.rightsConfirmed === "true";
    if (!title || !CONSOLES.has(consoleName) || !rightsConfirmed) {
      fs.rmSync(req.file.path, { force: true });
      return res.status(400).json({ error: "Title, supported console and confirmation that you have distribution rights are required." });
    }
    try {
      const hash = await new Promise((resolve, reject) => {
        const digest = crypto.createHash("sha256");
        const stream = fs.createReadStream(req.file.path);
        stream.on("data", chunk => digest.update(chunk));
        stream.on("error", reject);
        stream.on("end", () => resolve(digest.digest("hex")));
      });
      const game = {
        id: crypto.randomUUID(), title, console: consoleName, author: author || "Unknown",
        version, description, fileName: req.file.filename, originalFileName: safeName(req.file.originalname),
        size: req.file.size, sha256: hash, publishedAt: new Date().toISOString()
      };
      const catalog = readCatalog();
      catalog.unshift(game);
      writeCatalog(catalog);
      res.status(201).json({ game: { ...game, downloadUrl: "/api/games/" + game.id + "/download" } });
    } catch (e) {
      fs.rmSync(req.file.path, { force: true });
      res.status(500).json({ error: "Could not save catalogue: " + e.message });
    }
  });
});

app.get("/api/games/:id/download", (req, res) => {
  let game;
  try { game = readCatalog().find(item => item.id === req.params.id); }
  catch (e) { return res.status(500).json({ error: e.message }); }
  if (!game) return res.status(404).json({ error: "Game not found." });
  const filePath = path.join(UPLOAD_DIR, path.basename(game.fileName));
  if (!fs.existsSync(filePath)) return res.status(410).json({ error: "File is no longer available." });
  res.set("X-Content-Type-Options", "nosniff");
  res.download(filePath, game.originalFileName || game.fileName);
});

app.use((err, _req, res, _next) => {
  console.error(err);
  res.status(500).json({ error: "Unexpected server error." });
});

app.listen(PORT, "0.0.0.0", () => console.log("GameHub API listening on port " + PORT));
