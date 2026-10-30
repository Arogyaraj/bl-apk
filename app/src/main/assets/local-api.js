/* Local data layer for the Android app (also used by any browser opened with ?local=1).
   Data lives in IndexedDB on the device. The PIN is stored as a salted PBKDF2 hash.
   It answers the same /api paths as the server, so the app code is shared. */
(() => {
  "use strict";
  const DB_NAME = "bucketlist-local";
  const CATS = ["Travel","Adventure","Learning","Health","Career","Creative","Food","Experiences","Other"];
  const PRIOS = ["high", "medium", "low"];
  const ITER = 120000;
  const MAX_FAILS = 5;
  const WINDOW = 15 * 60 * 1000;
  const MAX_PHOTO = 400000;
  const EDITABLE = ["title","category","priority","date","budget","location","url","tags","notes","memory",
    "photo","emoji","steps","pinned","done","rating"];

  let dbPromise = null;
  let verified = null;   // PIN verified in this page session, so it is not re-hashed on every request

  const fail = (code, message) => Object.assign(new Error(message), { code });
  const reqP = (r) => new Promise((res, rej) => { r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); });

  function open() {
    if (!dbPromise) dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        const db = req.result;
        db.createObjectStore("kv");
        db.createObjectStore("dreams", { keyPath: "id" });
        db.createObjectStore("activity", { autoIncrement: true });
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    return dbPromise;
  }

  /* Single read or write on one store. */
  const read = (name, op) => open().then((db) => reqP(op(db.transaction(name, "readonly").objectStore(name))));
  const write = (name, op) => open().then((db) => new Promise((resolve, reject) => {
    const tx = db.transaction(name, "readwrite");
    let r;
    try { r = op(tx.objectStore(name)); } catch (e) { reject(e); return; }
    tx.oncomplete = () => resolve(r && r.result);
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error("Storage error"));
  }));
  /* Several writes that must succeed or fail together. */
  const batch = (names, fn) => open().then((db) => new Promise((resolve, reject) => {
    const tx = db.transaction(names, "readwrite");
    const stores = {};
    names.forEach((n) => (stores[n] = tx.objectStore(n)));
    try { fn(stores); } catch (e) { reject(e); try { tx.abort(); } catch {} return; }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error || new Error("Storage error"));
  }));

  const kvGet = (k) => read("kv", (s) => s.get(k));
  const kvSet = (k, v) => write("kv", (s) => s.put(v, k));

  /* ---------- PIN ---------- */
  const enc = new TextEncoder();
  const toB64 = (buf) => { let s = ""; for (const x of new Uint8Array(buf)) s += String.fromCharCode(x); return btoa(s); };
  const fromB64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
  const validPin = (p) => /^\d{4,12}$/.test(p);

  async function derive(pin, salt, iterations) {
    const key = await crypto.subtle.importKey("raw", enc.encode(pin), "PBKDF2", false, ["deriveBits"]);
    const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", salt, iterations, hash: "SHA-256" }, key, 256);
    return toB64(bits);
  }

  async function storePin(p) {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    await kvSet("pin", { salt: toB64(salt), hash: await derive(p, salt, ITER), iter: ITER });
  }

  async function isConfigured() { return !!(await kvGet("pin")); }

  async function setPin(p) {
    if (!validPin(p)) throw fail(400, "Use 4 to 12 digits");
    if (await isConfigured()) throw fail(409, "A PIN is already set");
    await storePin(p);
    await kvSet("pinFails", { count: 0, last: 0 });
    verified = p;
  }

  async function checkPin(p) {
    if (p && verified === p) return;
    const cfg = await kvGet("pin");
    if (!cfg) throw fail(400, "Create a PIN first");
    let fails = (await kvGet("pinFails")) || { count: 0, last: 0 };
    const now = Date.now();
    if (now - fails.last >= WINDOW) fails = { count: 0, last: 0 };
    if (fails.count >= MAX_FAILS) throw fail(429, "Too many attempts. Wait 15 minutes.");
    const ok = typeof p === "string" && (await derive(p, fromB64(cfg.salt), cfg.iter || ITER)) === cfg.hash;
    if (!ok) {
      await kvSet("pinFails", { count: fails.count + 1, last: now });
      throw fail(401, "Wrong PIN");
    }
    await kvSet("pinFails", { count: 0, last: 0 });
    verified = p;
  }

  async function changePin(oldPin, newPin) {
    await checkPin(oldPin);
    if (!validPin(newPin)) throw fail(400, "Use 4 to 12 digits");
    await storePin(newPin);
    verified = newPin;
  }

  async function wipe() {
    await batch(["kv", "dreams", "activity"], (s) => { s.kv.clear(); s.dreams.clear(); s.activity.clear(); });
    verified = null;
  }

  /* ---------- Dream rules (same as lib/db.js on the server) ---------- */
  const str = (v, max) => String(v ?? "").trim().slice(0, max);
  const uid = () => (crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2));
  const localIso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  const monthKey = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;

  function cleanUrl(v) {
    const s = str(v, 300);
    if (!s) return "";
    try {
      const u = new URL(/^https?:\/\//i.test(s) ? s : "https://" + s);
      return ["http:", "https:"].includes(u.protocol) ? u.href : "";
    } catch { return ""; }
  }
  const cleanPhoto = (v) => (typeof v === "string" && v.startsWith("data:image/") && v.length < MAX_PHOTO ? v : "");

  function sanitize(raw = {}) {
    const steps = (Array.isArray(raw.steps) ? raw.steps : []).slice(0, 40)
      .map((s) => ({ id: str(s && s.id, 40) || uid(), text: str(s && s.text, 200), done: !!(s && s.done) }))
      .filter((s) => s.text);
    const tags = (Array.isArray(raw.tags) ? raw.tags : []).map((t) => str(t, 30).replace(/^#/, "")).filter(Boolean).slice(0, 10);
    const done = !!raw.done;
    const rating = Math.round(Number(raw.rating));
    return {
      id: str(raw.id, 40) || uid(),
      title: str(raw.title, 120) || "Untitled",
      category: CATS.includes(raw.category) ? raw.category : "Other",
      priority: PRIOS.includes(raw.priority) ? raw.priority : "medium",
      date: /^\d{4}-\d{2}-\d{2}$/.test(raw.date || "") ? raw.date : null,
      budget: str(raw.budget, 40),
      location: str(raw.location, 120),
      url: cleanUrl(raw.url),
      tags,
      notes: str(raw.notes, 2000),
      memory: str(raw.memory, 4000),
      photo: cleanPhoto(raw.photo),
      emoji: Array.from(String(raw.emoji ?? "").trim()).slice(0, 4).join(""),
      steps,
      pinned: !!raw.pinned,
      done,
      rating: done && rating >= 1 && rating <= 5 ? rating : null,
      added: Number(raw.added) > 0 ? Number(raw.added) : 0,
      completedAt: done ? (Number(raw.completedAt) > 0 ? Number(raw.completedAt) : Date.now()) : 0
    };
  }

  const view = (d) => ({
    ...d,
    date: d.date || "",
    rating: d.rating ?? null,
    added: Math.round(d.added || 0),
    completedAt: Math.round(d.completedAt || 0)
  });

  const getDream = (id) => read("dreams", (s) => s.get(id));
  const allDreams = () => read("dreams", (s) => s.getAll());
  const allActivity = () => read("activity", (s) => s.getAll());
  const logActivity = (dreamId, title, kind) =>
    write("activity", (s) => s.add({ dreamId, title: str(title, 120), kind, at: Date.now() }));

  async function getProfile() {
    return { name: "Your Name", tagline: "", photo: "", goal: 0, ...((await kvGet("profile")) || {}) };
  }

  function profileFrom(pr = {}) {
    return {
      name: str(pr.name, 60) || "Your Name",
      tagline: str(pr.tagline, 120),
      photo: cleanPhoto(pr.photo),
      goal: Math.min(500, Math.max(0, Math.round(Number(pr.goal) || 0)))
    };
  }

  async function loadAll() {
    const [profile, dreams, activity] = await Promise.all([getProfile(), allDreams(), allActivity()]);
    dreams.sort((a, b) => (b.pinned - a.pinned) || (b.added - a.added));
    return {
      profile,
      dreams: dreams.map(view),
      activity: activity.slice(-80).reverse().map((a) => ({ dreamId: a.dreamId, title: a.title, kind: a.kind, at: a.at }))
    };
  }

  async function createDream(body) {
    let d;
    if (body.duplicateOf) {
      const src = await getDream(str(body.duplicateOf, 40));
      if (!src) throw fail(404, "Original not found");
      d = sanitize({
        ...src, id: undefined, title: src.title + " (copy)", done: false, completedAt: 0,
        pinned: false, memory: "", rating: null, added: 0,
        steps: src.steps.map((s) => ({ ...s, id: undefined, done: false }))
      });
    } else {
      d = sanitize(body);
    }
    d.added = d.added || Date.now();
    if (!(await getDream(d.id))) {
      await write("dreams", (s) => s.put(d));
      await logActivity(d.id, d.title, "created");
    }
    return view(await getDream(d.id));
  }

  async function patchDream(old, body) {
    const pick = {};
    for (const k of EDITABLE) if (k in body) pick[k] = body[k];
    const merged = sanitize({ ...old, ...pick, id: old.id });
    merged.added = old.added;
    const before = sanitize(old);
    const changed = EDITABLE.some((k) => JSON.stringify(before[k]) !== JSON.stringify(merged[k]));
    await write("dreams", (s) => s.put(merged));
    if (changed) {
      const kind = merged.done && !old.done ? "completed"
        : !merged.done && old.done ? "reopened"
        : merged.memory && merged.memory !== old.memory ? "memory"
        : "updated";
      await logActivity(old.id, merged.title, kind);
    }
    return view(merged);
  }

  async function removeDream(old) {
    await write("dreams", (s) => s.delete(old.id));
    await logActivity(old.id, old.title, "deleted");
    return { ok: true };
  }

  async function importBackup(data) {
    if (!Array.isArray(data.items)) throw fail(400, "Not a valid backup");
    const items = data.items.slice(0, 1000).map((i) => {
      const d = sanitize(i);
      d.added = d.added || Date.now();
      return d;
    });
    const profile = data.profile ? profileFrom(data.profile) : null;
    await batch(["dreams", "activity", "kv"], (s) => {
      s.dreams.clear();
      s.activity.clear();
      items.forEach((d) => s.dreams.put(d));
      if (profile) s.kv.put(profile, "profile");
    });
    return { ok: true, count: items.length };
  }

  async function stats() {
    const dreams = await allDreams();
    const done = dreams.filter((d) => d.done);
    const now = new Date();
    const today = localIso(now);
    const days = done.filter((d) => d.completedAt && d.added).map((d) => (d.completedAt - d.added) / 864e5);
    const ratings = dreams.filter((d) => d.rating).map((d) => d.rating);
    const start = new Date(now.getFullYear(), now.getMonth() - 11, 1).getTime();
    const monthly = {};
    for (const d of done) {
      if (d.completedAt >= start) {
        const k = monthKey(new Date(d.completedAt));
        monthly[k] = (monthly[k] || 0) + 1;
      }
    }
    const group = (field) => {
      const m = {};
      for (const d of dreams) {
        const k = d[field];
        m[k] = m[k] || { total: 0, done: 0 };
        m[k].total++;
        if (d.done) m[k].done++;
      }
      return m;
    };
    const cats = group("category");
    const prios = group("priority");
    return {
      total: dreams.length,
      done: done.length,
      doneYear: done.filter((d) => d.completedAt && new Date(d.completedAt).getFullYear() === now.getFullYear()).length,
      overdue: dreams.filter((d) => !d.done && d.date && d.date < today).length,
      avgDays: days.length ? Math.round(days.reduce((a, b) => a + b, 0) / days.length) : null,
      avgRating: ratings.length ? ratings.reduce((a, b) => a + b, 0) / ratings.length : null,
      monthly: Object.keys(monthly).sort().map((month) => ({ month, n: monthly[month] })),
      byCategory: Object.keys(cats).map((c) => ({ category: c, ...cats[c] })).sort((a, b) => b.total - a.total),
      byPriority: Object.keys(prios).map((p) => ({ priority: p, ...prios[p] }))
    };
  }

  /* Same routes and status codes as the server API. */
  async function call(method, path, body, pinValue) {
    const url = new URL(path, "http://device.local");
    const p = url.pathname;
    const id = String(url.searchParams.get("id") || "").slice(0, 40);
    body = body || {};
    await checkPin(pinValue);

    if (p === "/api/ping") return { ok: true };

    if (p === "/api/data") {
      if (method === "GET") return loadAll();
      if (method === "PUT") {
        await kvSet("profile", profileFrom(body));
        return getProfile();
      }
      if (method === "DELETE") {
        await batch(["dreams", "activity", "kv"], (s) => {
          s.dreams.clear();
          s.activity.clear();
          s.kv.delete("profile");
        });
        return { ok: true };
      }
    }

    if (p === "/api/dreams") {
      if (method === "POST") return createDream(body);
      if (!id) throw fail(400, "Missing id");
      const old = await getDream(id);
      if (!old) throw fail(404, "Not found");
      if (method === "PATCH") return patchDream(old, body);
      if (method === "DELETE") return removeDream(old);
    }

    if (p === "/api/import" && method === "POST") return importBackup(body);
    if (p === "/api/stats" && method === "GET") return stats();
    throw fail(405, "Not supported");
  }

  window.LocalAPI = {
    isLocal: true,
    call,
    isConfigured,
    setPin,
    changePin,
    wipe
  };
})();
