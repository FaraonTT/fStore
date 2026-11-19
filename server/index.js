'use strict';
/**
 * fStore server. Runs inside GitHub Actions (see .github/workflows/server.yml).
 * Clients send end-to-end encrypted requests as issues; this script decrypts them,
 * applies them to the data, publishes the public catalog, and answers with an encrypted comment.
 * Secrets (password hashes, sessions) live only in data/private.enc, encrypted with a key
 * derived from the SERVER_SECRET repository secret.
 */
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');
const { hk, make } = require('./keys');

const REPO = process.env.GITHUB_REPOSITORY;
const [OWNER, NAME] = REPO.split('/');
const UPLOADS = `${OWNER}/${NAME}-uploads`;
const GH = process.env.GH_TOKEN;
const DROP = process.env.DROP_TOKEN || '';
const OWNER_USER = 'void_fworld';
const MAX_APK = 100 * 1024 * 1024;
const { ecdh, dataKey } = make();

class Err extends Error {
  constructor(m, code) {
    super(m);
    this.code = code || 'err';
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sha256 = (b) => crypto.createHash('sha256').update(b).digest('hex');

// ---------- GitHub helpers ----------
async function api(method, url, body, token = GH) {
  const r = await fetch(url.startsWith('http') ? url : 'https://api.github.com' + url, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const t = await r.text();
  if (!r.ok && r.status !== 404) throw new Error(`${method} ${url} -> ${r.status} ${t.slice(0, 200)}`);
  let json = null;
  try {
    json = t ? JSON.parse(t) : null;
  } catch (e) {
    json = null;
  }
  return { status: r.status, json };
}

function gh(...args) {
  const r = spawnSync('gh', args, { env: { ...process.env, GH_TOKEN: GH }, encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`gh ${args[0]} ${args[1]}: ${(r.stderr || '').slice(0, 300)}`);
  return r.stdout;
}
function ghQuiet(...args) {
  try {
    return gh(...args);
  } catch (e) {
    console.error(String(e.message).slice(0, 200));
    return '';
  }
}

// ---------- crypto ----------
function gcmEnc(key, buf) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ct = Buffer.concat([c.update(buf), c.final()]);
  return Buffer.concat([iv, ct, c.getAuthTag()]);
}
function gcmDec(key, blob) {
  const iv = blob.subarray(0, 12);
  const tag = blob.subarray(blob.length - 16);
  const ct = blob.subarray(12, blob.length - 16);
  const d = crypto.createDecipheriv('aes-256-gcm', key, iv);
  d.setAuthTag(tag);
  return Buffer.concat([d.update(ct), d.final()]);
}
function decryptRequest(bodyText) {
  const env = JSON.parse(bodyText);
  if (env.v !== 1) throw new Error('bad version');
  const key = hk('fstore-req', ecdh.computeSecret(Buffer.from(env.epk, 'base64')));
  return JSON.parse(gcmDec(key, Buffer.from(env.blob, 'base64')).toString('utf8'));
}

// ---------- data ----------
const PRIV_FILE = 'data/private.enc';
function loadPriv() {
  if (!fs.existsSync(PRIV_FILE)) return { users: {}, sessions: {}, nonces: [], fails: {} };
  const p = JSON.parse(gcmDec(dataKey, Buffer.from(fs.readFileSync(PRIV_FILE, 'utf8').trim(), 'base64')).toString('utf8'));
  p.users = p.users || {};
  p.sessions = p.sessions || {};
  p.nonces = p.nonces || [];
  p.fails = p.fails || {};
  return p;
}
const priv = loadPriv();
const rd = (f) => (fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8') || '[]') : []);
let apps = rd('data/apps.json');
let reviews = rd('data/reviews.json');
let badges = rd('data/badges.json');

const getUser = (un) => (Object.prototype.hasOwnProperty.call(priv.users, un) ? priv.users[un] : null);
const pub = (u) => ({
  username: u.username,
  name: u.name,
  desc: u.hideDesc ? '' : u.desc,
  avatar: u.avatar || '',
  role: u.role,
  banned: !!u.banned,
  verified: !!u.verified,
  glow: !!u.glow,
  nameColor: u.nameColor || '',
  glowColor: u.glowColor || '',
  badges: u.badges || [],
  friend: !!u.friend,
  virus: !!u.virus,
  hideDesc: !!u.hideDesc,
  updated: u.updated,
});

function writeCatalog() {
  fs.mkdirSync('data', { recursive: true });
  const w = (f, v) => fs.writeFileSync(f, JSON.stringify(v, null, 1) + '\n');
  w('data/users.json', Object.values(priv.users).map(pub));
  w('data/apps.json', apps);
  w('data/reviews.json', reviews);
  w('data/badges.json', badges);
  if (priv.nonces.length > 3000) priv.nonces = priv.nonces.slice(-3000);
  fs.writeFileSync(PRIV_FILE, gcmEnc(dataKey, Buffer.from(JSON.stringify(priv))).toString('base64'));
}

function git(...args) {
  return spawnSync('git', args, { encoding: 'utf8' });
}
function publish() {
  git('add', '-A', 'data');
  if (git('diff', '--cached', '--quiet').status === 0) return;
  git('commit', '-q', '-m', 'api');
  for (let i = 0; i < 5; i++) {
    if (git('push').status === 0) return;
    git('pull', '--rebase', '-X', 'theirs');
  }
  throw new Error('git push failed');
}

// ---------- validation ----------
const str = (v, min, max, label) => {
  const s = String(v == null ? '' : v).trim();
  if (s.length < min || s.length > max) throw new Err(`${label}: от ${min} до ${max} символов`);
  return s;
};
const slug = (s) => (s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'app').slice(0, 30);
const colorOk = (c) => c === '' || /^#[0-9a-fA-F]{6}$/.test(c);
const isOwner = (u) => u.username === OWNER_USER;
const isAdmin = (u) => isOwner(u) || u.role === 'admin';
const relUrl = (id, asset) => `https://github.com/${OWNER}/${NAME}/releases/download/app-${id}/${asset}`;

const hashPw = (pw, salt) =>
  crypto.scryptSync(pw, salt, 64, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 }).toString('hex');
function pwOk(u, pw) {
  const a = Buffer.from(hashPw(String(pw || ''), u.salt), 'hex');
  const b = Buffer.from(u.hash, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

// ---------- sessions ----------
const sh = (t) => sha256(String(t || ''));
function newSession(un, device) {
  const token = crypto.randomBytes(32).toString('base64url');
  priv.sessions[sh(token)] = {
    id: crypto.randomBytes(6).toString('hex'),
    u: un,
    device: String(device || 'Android').slice(0, 40),
    created: Date.now(),
    last: Date.now(),
  };
  return token;
}
function auth(req) {
  const h = sh(req.token);
  const s = Object.prototype.hasOwnProperty.call(priv.sessions, h) ? priv.sessions[h] : null;
  if (!s) throw new Err('Сессия недействительна. Войдите заново.', 'auth');
  if (Date.now() - s.last > 30 * 86400e3) {
    delete priv.sessions[h];
    throw new Err('Сессия истекла. Войдите заново.', 'auth');
  }
  const u = getUser(s.u);
  if (!u || u.banned) throw new Err('Аккаунт недоступен', 'auth');
  s.last = Date.now();
  return { u, s, h, used: [] };
}
function dropSessions(un, exceptHash) {
  for (const k of Object.keys(priv.sessions)) {
    if (priv.sessions[k].u === un && k !== exceptHash) delete priv.sessions[k];
  }
}

// ---------- uploaded files ----------
async function fetchDrop(ref, max, ext) {
  if (!ref || typeof ref.path !== 'string' || !new RegExp(`^u/[a-z0-9]{16,40}\\.${ext}$`).test(ref.path)) {
    throw new Err('Некорректная ссылка на файл');
  }
  const url = `https://raw.githubusercontent.com/${UPLOADS}/main/${ref.path}`;
  let res;
  for (let i = 0; i < 6; i++) {
    res = await fetch(url + '?t=' + Date.now());
    if (res.ok) break;
    await sleep(2500);
  }
  if (!res || !res.ok) throw new Err('Файл не найден в хранилище загрузок');
  const tmp = path.join(os.tmpdir(), crypto.randomBytes(8).toString('hex'));
  const h = crypto.createHash('sha256');
  const out = fs.createWriteStream(tmp);
  let n = 0;
  for await (const chunk of res.body) {
    n += chunk.length;
    if (n > max) {
      out.destroy();
      fs.rmSync(tmp, { force: true });
      throw new Err('Файл слишком большой');
    }
    h.update(chunk);
    out.write(chunk);
  }
  await new Promise((r) => out.end(r));
  const sha = h.digest('hex');
  if (String(ref.sha256 || '').toLowerCase() !== sha) {
    fs.rmSync(tmp, { force: true });
    throw new Err('Контрольная сумма файла не совпала');
  }
  return { file: tmp, size: n, sha };
}
async function dropDelete(p) {
  try {
    const r = await api('GET', `/repos/${UPLOADS}/contents/${p}`, null, DROP);
    if (r.status === 200 && r.json && r.json.sha) {
      await api('DELETE', `/repos/${UPLOADS}/contents/${p}`, { message: 'c', sha: r.json.sha, branch: 'main' }, DROP);
    }
  } catch (e) {
    console.error('dropDelete', String(e.message).slice(0, 120));
  }
}
async function saveImage(ref, dest, c) {
  const f = await fetchDrop(ref, 400 * 1024, 'png');
  c.used.push(ref.path);
  const head = fs.readFileSync(f.file).subarray(0, 4);
  if (!head.equals(Buffer.from([0x89, 0x50, 0x4e, 0x47]))) throw new Err('Файл не является PNG-изображением');
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(f.file, dest);
  fs.rmSync(f.file, { force: true });
  return dest;
}

function signerOf(file) {
  const home = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || '/usr/local/lib/android/sdk';
  const bt = path.join(home, 'build-tools');
  const vers = fs.existsSync(bt) ? fs.readdirSync(bt).sort() : [];
  if (!vers.length) throw new Err('На сервере нет apksigner: подпись APK проверить нельзя');
  const r = spawnSync(path.join(bt, vers[vers.length - 1], 'apksigner'), ['verify', '--print-certs', file], { encoding: 'utf8' });
  if (r.status !== 0) throw new Err('APK не подписан или подпись повреждена');
  const m = /certificate SHA-256 digest: ([0-9a-f]{64})/i.exec(r.stdout);
  if (!m) throw new Err('Не удалось прочитать сертификат APK');
  return m[1].toLowerCase();
}
function addRelease(id, title, file, assetName, first) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'a-'));
  const f = path.join(dir, assetName);
  fs.copyFileSync(file, f);
  if (first) {
    gh('release', 'create', `app-${id}`, f, '--repo', REPO, '--title', title.slice(0, 80), '--notes', 'fStore app', '--latest=false');
  } else {
    gh('release', 'upload', `app-${id}`, f, '--repo', REPO, '--clobber');
  }
}
function dropAsset(url) {
  const m = /\/releases\/download\/(app-[^/]+)\/([^/]+)$/.exec(url || '');
  if (m) ghQuiet('release', 'delete-asset', m[1], m[2], '-y', '--repo', REPO);
}
const rmFile = (p) => {
  if (p && p.startsWith('data/')) fs.rmSync(p, { force: true });
};

// ---------- operations ----------
const ops = {
  async register(_c, req, a) {
    const un = String(a.username || '').trim().toLowerCase();
    if (!/^[a-z0-9_]{3,20}$/.test(un)) throw new Err('Имя пользователя: 3–20 символов, a–z, 0–9 и _');
    if (getUser(un)) throw new Err('Это имя пользователя занято');
    const pw = String(a.password || '');
    if (pw.length < 8 || pw.length > 100) throw new Err('Пароль: от 8 до 100 символов');
    const salt = crypto.randomBytes(16).toString('hex');
    const u = {
      username: un, name: str(a.name, 1, 40, 'Имя'), desc: str(a.desc || '', 0, 300, 'Описание'),
      avatar: '', role: 'user', banned: false, verified: false, glow: false, nameColor: '', glowColor: '',
      badges: [], friend: false, virus: false, hideDesc: false, updated: Date.now(),
      salt, hash: hashPw(pw, salt),
    };
    const c = { used: [] };
    try {
      if (a.avatar) u.avatar = await saveImage(a.avatar, `data/avatars/${un}.png`, c);
    } finally {
      c.dropUsed = c.used;
    }
    priv.users[un] = u;
    return { token: newSession(un, a.device), user: pub(u), _used: c.used };
  },

  async login(_c, req, a) {
    const un = String(a.username || '').trim().toLowerCase();
    const u = getUser(un);
    const key = 'u:' + un;
    const f = priv.fails[key] || (priv.fails[key] = { n: 0, until: 0 });
    if (f.until > Date.now()) throw new Err('Слишком много попыток. Подождите 15 минут.');
    if (!u || !pwOk(u, a.password)) {
      f.n++;
      if (f.n >= 5) {
        f.n = 0;
        f.until = Date.now() + 15 * 60e3;
      }
      throw new Err('Неверное имя пользователя или пароль');
    }
    if (u.banned) throw new Err('Аккаунт заблокирован');
    f.n = 0;
    return { token: newSession(un, a.device), user: pub(u) };
  },

  async logout(c) {
    delete priv.sessions[c.h];
    return {};
  },

  async logoutAll(c, req, a) {
    if (!pwOk(c.u, a.password)) throw new Err('Неверный пароль');
    dropSessions(c.u.username, null);
    return {};
  },

  async changePassword(c, req, a) {
    if (!pwOk(c.u, a.old)) throw new Err('Текущий пароль указан неверно');
    const np = String(a.new || '');
    if (np.length < 8 || np.length > 100) throw new Err('Новый пароль: от 8 до 100 символов');
    c.u.salt = crypto.randomBytes(16).toString('hex');
    c.u.hash = hashPw(np, c.u.salt);
    dropSessions(c.u.username, c.h);
    return {};
  },

  async sessions(c) {
    const list = Object.entries(priv.sessions)
      .filter(([, s]) => s.u === c.u.username)
      .map(([h, s]) => ({ id: s.id, device: s.device, created: s.created, last: s.last, current: h === c.h }));
    return { list };
  },

  async revokeSession(c, req, a) {
    for (const [h, s] of Object.entries(priv.sessions)) {
      if (s.u === c.u.username && s.id === a.id && h !== c.h) delete priv.sessions[h];
    }
    return {};
  },

  async updateProfile(c, req, a) {
    c.u.name = str(a.name, 1, 40, 'Имя');
    c.u.desc = str(a.desc || '', 0, 300, 'Описание');
    if (a.avatar) c.u.avatar = await saveImage(a.avatar, `data/avatars/${c.u.username}.png`, c);
    c.u.updated = Date.now();
    return {};
  },

  async updatePrivacy(c, req, a) {
    c.u.hideDesc = a.hideDesc === true;
    c.u.updated = Date.now();
    return {};
  },

  async submitApp(c, req, a) {
    const u = c.u;
    const name = str(a.name, 1, 60, 'Название');
    const version = str(a.version, 1, 30, 'Версия');
    const apk = await fetchDrop(a.apk, MAX_APK, 'apk');
    c.used.push(a.apk.path);
    const fd = fs.openSync(apk.file, 'r');
    const magic = Buffer.alloc(4);
    fs.readSync(fd, magic, 0, 4, 0);
    fs.closeSync(fd);
    if (apk.size < 10000 || magic[0] !== 0x50 || magic[1] !== 0x4b) throw new Err('Файл не похож на APK');
    const signer = signerOf(apk.file);
    const ts = Date.now();
    if (a.kind === 'new') {
      const id = slug(name) + '-' + crypto.randomBytes(3).toString('hex');
      const asset = `${id}-${slug(version)}.apk`;
      const icon = a.icon ? await saveImage(a.icon, `data/icons/${id}.png`, c) : '';
      addRelease(id, name, apk.file, asset, true);
      apps.push({
        id, owner: u.username, name, version,
        shortDesc: str(a.shortDesc || '', 0, 100, 'Краткое описание'),
        desc: str(a.desc || '', 0, 3000, 'Описание'),
        whatsNew: '', icon, apkUrl: relUrl(id, asset), sha256: apk.sha, size: apk.size, signer,
        status: 'pending', fworld: false, created: ts, updated: ts,
      });
      return { id };
    }
    const app = apps.find((x) => x.id === a.appId);
    if (!app || app.owner !== u.username) throw new Err('Приложение не найдено');
    if (app.signer && app.signer !== signer) {
      throw new Err('Подпись APK не совпадает с подписью опубликованного приложения. Обновление отклонено.');
    }
    const asset = `${app.id}-${slug(version)}-${ts}.apk`;
    const icon = a.icon ? await saveImage(a.icon, `data/icons/${app.id}-${ts}.png`, c) : '';
    addRelease(app.id, name, apk.file, asset, false);
    if (app.pending) {
      dropAsset(app.pending.apkUrl);
      rmFile(app.pending.icon);
    }
    app.pending = {
      name, version, whatsNew: str(a.whatsNew || '', 0, 1000, 'Что нового'), icon,
      apkUrl: relUrl(app.id, asset), sha256: apk.sha, size: apk.size, signer, time: ts,
    };
    return { id: app.id };
  },

  async approve(c, req, a) {
    if (!isAdmin(c.u)) throw new Err('Нет прав');
    const app = apps.find((x) => x.id === a.appId);
    if (!app) throw new Err('Приложение не найдено');
    const p = app.pending;
    if (p) {
      const oldUrl = app.apkUrl;
      const oldIcon = app.icon;
      Object.assign(app, {
        name: p.name, version: p.version, whatsNew: p.whatsNew, apkUrl: p.apkUrl, sha256: p.sha256,
        size: p.size, signer: p.signer, icon: p.icon || app.icon,
      });
      delete app.pending;
      dropAsset(oldUrl);
      if (p.icon && oldIcon && oldIcon !== p.icon) rmFile(oldIcon);
    }
    app.status = 'approved';
    app.updated = Date.now();
    return {};
  },

  async reject(c, req, a) {
    if (!isAdmin(c.u)) throw new Err('Нет прав');
    const app = apps.find((x) => x.id === a.appId);
    if (!app) throw new Err('Приложение не найдено');
    if (app.pending) {
      dropAsset(app.pending.apkUrl);
      rmFile(app.pending.icon);
      delete app.pending;
    } else {
      app.status = 'rejected';
    }
    app.updated = Date.now();
    return {};
  },

  async setFworld(c, req, a) {
    if (!isAdmin(c.u)) throw new Err('Нет прав');
    const app = apps.find((x) => x.id === a.appId);
    if (!app) throw new Err('Приложение не найдено');
    app.fworld = a.on === true;
    app.updated = Date.now();
    return {};
  },

  async deleteApp(c, req, a) {
    const app = apps.find((x) => x.id === a.appId);
    if (!app) throw new Err('Приложение не найдено');
    if (app.owner !== c.u.username && !isAdmin(c.u)) throw new Err('Нет прав');
    ghQuiet('release', 'delete', `app-${app.id}`, '--cleanup-tag', '-y', '--repo', REPO);
    rmFile(app.icon);
    if (app.pending) rmFile(app.pending.icon);
    apps = apps.filter((x) => x.id !== app.id);
    reviews = reviews.filter((r) => r.appId !== app.id);
    return {};
  },

  async review(c, req, a) {
    const app = apps.find((x) => x.id === a.appId);
    if (!app) throw new Err('Приложение не найдено');
    if (app.owner === c.u.username) throw new Err('Нельзя оценивать своё приложение');
    const stars = Number(a.stars);
    if (!Number.isInteger(stars) || stars < 1 || stars > 5) throw new Err('Оценка от 1 до 5');
    const id = `${app.id}:${c.u.username}`;
    const old = reviews.find((r) => r.id === id);
    reviews = reviews.filter((r) => r.id !== id);
    reviews.push({
      id, appId: app.id, user: c.u.username, stars, text: str(a.text || '', 0, 1000, 'Отзыв'),
      time: Date.now(), reply: old ? old.reply : '', replyTime: old ? old.replyTime : 0,
    });
    return {};
  },

  async reply(c, req, a) {
    const r = reviews.find((x) => x.id === a.reviewId);
    if (!r) throw new Err('Отзыв не найден');
    const app = apps.find((x) => x.id === r.appId);
    if (!app || (app.owner !== c.u.username && !isAdmin(c.u))) throw new Err('Отвечать может только разработчик');
    r.reply = str(a.text || '', 0, 1000, 'Ответ');
    r.replyTime = Date.now();
    return {};
  },

  async setUser(c, req, a) {
    if (!isAdmin(c.u)) throw new Err('Нет прав');
    const t = getUser(String(a.username || ''));
    if (!t) throw new Err('Пользователь не найден');
    if (isOwner(t)) throw new Err('Нельзя менять владельца');
    const p = a.patch || {};
    const me = isOwner(c.u);
    if (isAdmin(t) && !me) throw new Err('Нельзя менять администратора');
    for (const k of ['banned', 'virus', 'friend']) if (k in p) t[k] = p[k] === true;
    if (t.banned) dropSessions(t.username, null);
    const ownerKeys = ['role', 'verified', 'glow', 'nameColor', 'glowColor', 'badges'];
    if (ownerKeys.some((k) => k in p) && !me) throw new Err('Эти настройки доступны только владельцу');
    if ('role' in p) t.role = p.role === 'admin' ? 'admin' : 'user';
    if ('verified' in p) t.verified = p.verified === true;
    if ('glow' in p) t.glow = p.glow === true;
    for (const k of ['nameColor', 'glowColor']) {
      if (k in p) {
        if (!colorOk(String(p[k]))) throw new Err('Цвет в формате #RRGGBB');
        t[k] = String(p[k]);
      }
    }
    if ('badges' in p) {
      const ids = new Set(badges.map((b) => b.id));
      t.badges = (Array.isArray(p.badges) ? p.badges : []).filter((b) => ids.has(b)).slice(0, 12);
    }
    t.updated = Date.now();
    return {};
  },

  async createBadge(c, req, a) {
    if (!isOwner(c.u)) throw new Err('Только владелец');
    const id = 'b' + crypto.randomBytes(4).toString('hex');
    const kind = a.kind;
    if (!['emoji', 'color', 'image'].includes(kind)) throw new Err('Неизвестный тип бейджа');
    let value = String(a.value || '').trim();
    if (kind === 'image') value = await saveImage(a.image, `data/badges/${id}.png`, c);
    else if (kind === 'color' && !/^#[0-9a-fA-F]{6}$/.test(value)) throw new Err('Цвет в формате #RRGGBB');
    else if (kind === 'emoji' && (value.length < 1 || value.length > 8)) throw new Err('Укажите эмодзи');
    badges.push({ id, name: str(a.name, 1, 30, 'Название'), desc: str(a.desc || '', 0, 200, 'Описание'), kind, value });
    return { id };
  },

  async deleteBadge(c, req, a) {
    if (!isOwner(c.u)) throw new Err('Только владелец');
    const b = badges.find((x) => x.id === a.id);
    if (!b) return {};
    if (b.kind === 'image') rmFile(b.value);
    badges = badges.filter((x) => x.id !== b.id);
    for (const u of Object.values(priv.users)) u.badges = (u.badges || []).filter((x) => x !== b.id);
    return {};
  },
};
const PUBLIC_OPS = new Set(['register', 'login']);

// ---------- request loop ----------
async function handle(issue) {
  let req;
  try {
    req = decryptRequest(issue.body || '');
  } catch (e) {
    await api('PATCH', `/repos/${REPO}/issues/${issue.number}`, { state: 'closed', body: '[invalid]' });
    return;
  }
  const rk = Buffer.from(String(req.rk || ''), 'base64');
  let resp;
  let used = [];
  try {
    if (rk.length !== 32) throw new Err('Некорректный запрос');
    if (Math.abs(Date.now() - Number(req.ts)) > 15 * 60e3) throw new Err('Запрос устарел. Проверьте время на устройстве.');
    const nonce = String(req.nonce || '');
    if (nonce.length < 8 || priv.nonces.includes(nonce)) throw new Err('Повторный запрос отклонён');
    priv.nonces.push(nonce);
    const fn = Object.prototype.hasOwnProperty.call(ops, req.op) ? ops[req.op] : null;
    if (!fn) throw new Err('Неизвестная операция');
    const c = PUBLIC_OPS.has(req.op) ? { used: [] } : auth(req);
    const data = await fn(c, req, req.args || {});
    used = c.used || [];
    if (data && data._used) {
      used = used.concat(data._used);
      delete data._used;
    }
    resp = { ok: true, data };
  } catch (e) {
    if (!(e instanceof Err)) console.error(e);
    resp = { ok: false, error: e instanceof Err ? e.message : 'Внутренняя ошибка сервера', code: e.code || 'err' };
  }
  writeCatalog();
  publish();
  if (rk.length === 32) {
    const body = gcmEnc(rk, Buffer.from(JSON.stringify(resp))).toString('base64');
    await api('POST', `/repos/${REPO}/issues/${issue.number}/comments`, { body });
  }
  await api('PATCH', `/repos/${REPO}/issues/${issue.number}`, { state: 'closed', body: '[processed]' });
  for (const p of used) await dropDelete(p);
}

async function main() {
  git('config', 'user.name', 'fstore-bot');
  git('config', 'user.email', 'fstore-bot@users.noreply.github.com');
  const q = await api('GET', `/repos/${REPO}/issues?state=open&labels=api&per_page=50&sort=created&direction=asc`);
  const list = (q.json || []).filter((i) => !i.pull_request && i.user && i.user.login === OWNER);
  for (const it of list) {
    try {
      await handle(it);
    } catch (e) {
      console.error('issue', it.number, String(e.message).slice(0, 200));
    }
  }
}
main().catch((e) => {
  console.error(e);
  process.exit(1);
});
