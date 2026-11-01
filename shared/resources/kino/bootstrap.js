import * as rank from 'kino-rank';

// This prelude exposes JSON/byte operations only. Plugins never receive a JVM object.
const parse = JSON.parse;
const native = (operation, value) => {
  const answer = parse(__kino_native(operation, JSON.stringify(value ?? null)));
  if (answer.error) { const e = new Error(answer.message || answer.error); e.code = answer.error; throw e; }
  return answer.value;
};
const setup = native('setup');
const unavailable = name => { const e = new Error(`${name} aún no está disponible en Nuvio-Kino`); e.code = 'unavailable'; throw e; };

class URLSearchParams {
  constructor(input = '') {
    this.entries_ = [];
    if (typeof input === 'string') {
      for (const part of input.replace(/^\?/, '').split('&')) {
        if (!part) continue;
        const i = part.indexOf('=');
        const decode = s => { try { return decodeURIComponent(s.replace(/\+/g, ' ')); } catch { return s; } };
        this.entries_.push([decode(i < 0 ? part : part.slice(0, i)), decode(i < 0 ? '' : part.slice(i + 1))]);
      }
    } else if (Array.isArray(input)) this.entries_ = input.map(([k,v]) => [String(k),String(v)]);
    else if (input && typeof input === 'object') this.entries_ = Object.entries(input).map(([k,v]) => [k,String(v)]);
  }
  append(k,v) { this.entries_.push([String(k),String(v)]); }
  set(k,v) { this.delete(k); this.append(k,v); }
  delete(k) { this.entries_ = this.entries_.filter(x => x[0] !== String(k)); }
  get(k) { return this.entries_.find(x => x[0] === String(k))?.[1] ?? null; }
  getAll(k) { return this.entries_.filter(x => x[0] === String(k)).map(x => x[1]); }
  has(k) { return this.get(k) !== null; }
  toString() { const e=s=>encodeURIComponent(s).replace(/%20/g,'+').replace(/[!'()~]/g,c=>'%'+c.charCodeAt(0).toString(16).toUpperCase()); return this.entries_.map(([k,v])=>e(k)+'='+e(v)).join('&'); }
  entries() { return this.entries_[Symbol.iterator](); }
  keys() { return this.entries_.map(x=>x[0])[Symbol.iterator](); }
  values() { return this.entries_.map(x=>x[1])[Symbol.iterator](); }
  forEach(fn, self) { this.entries_.forEach(([k,v])=>fn.call(self,v,k,this)); }
  [Symbol.iterator]() { return this.entries(); }
}
class URL {
  constructor(value, base) { Object.assign(this, native('url', { value: String(value), base })); this.searchParams = new URLSearchParams(this.search); }
  toString() { return this.href; }
  toJSON() { return this.href; }
}
class TextEncoder {
  get encoding() { return 'utf-8'; }
  encode(text = '') { return new Uint8Array(native('encode', String(text))); }
}
class TextDecoder {
  constructor(encoding = 'utf-8') { if (!['utf-8','utf8'].includes(encoding.toLowerCase())) throw new RangeError('Solo UTF-8'); }
  get encoding() { return 'utf-8'; }
  decode(bytes = []) { return native('decode', Array.from(bytes)); }
}
Object.assign(globalThis, { URL, URLSearchParams, TextEncoder, TextDecoder,
  atob: value => native('atob', String(value)), btoa: value => native('btoa', String(value)),
});
const error = (code, message = code, extra = {}) => Object.assign(new Error(message), { code }, extra);
const log = (...args) => { /* User credentials and plugin payloads are never sent to telemetry. */ };
log.report = () => {};
const config = Object.freeze({
  get: key => setup.config[key], all: () => ({...setup.config}),
});
const storage = Object.freeze({
  get: key => native('storage.get', String(key)),
  set: (key, value, options) => native('storage.set', { key: String(key), value: String(value), ttlMs: options?.ttlMs }),
  remove: key => native('storage.remove', String(key)),
  keys: () => native('storage.keys'), clear: () => native('storage.clear'),
});
const crypto = Object.freeze({
  hash: (algorithm, data, options) => native('crypto.hash', {algorithm,data,options}),
  hmac: (algorithm, key, data, options) => native('crypto.hmac', {algorithm,key,data,options}),
  random: length => native('crypto.random', length),
  randomBytes: length => native('crypto.random', length),
  uuid: () => native('crypto.uuid'),
  convert: (data, from, to) => native('crypto.convert', {data,from,to}),
  gunzip: (data, options) => native('crypto.gunzip', {data,options}),
});
const callAsync = async (op, value) => {
  const answer = parse(await __kino_async(op, JSON.stringify(value ?? null)));
  if (answer.error) throw error(answer.error, answer.message);
  return answer.value;
};
const fetch = async (url, options = {}) => {
  const result = await callAsync('fetch', {url:String(url),options});
  return Object.freeze({ ok: result.ok, status: result.status, url: result.url, headers: Object.freeze(result.headers),
    truncated: result.truncated, text: () => result.text, json: () => parse(result.text), base64: () => result.base64 });
};
const emptyCatalog = Object.freeze({
  search: q => native('catalog.search',q), get: id => native('catalog.get',id),
  upsert: rows => native('catalog.upsert',rows), remove: ids => native('catalog.remove',ids),
  count: () => native('catalog.count'), clear: () => native('catalog.clear'),
});
globalThis.console = Object.freeze({log,info:log,warn:log,error:log});
globalThis.kino = Object.freeze({
  apiVersion: 17, appVersion: '0.1.0-nuvio-kino', lang: 'es-CL', device: setup.device === 'tv' ? 'tv' : 'phone',
  seed: setup.seed, fetchAnyHost: setup.fetchAnyHost, config, storage, crypto, rank: Object.freeze({...rank}), error, log, fetch,
  sleep: ms => callAsync('sleep', Math.min(30000,Math.max(0,Number(ms)||0))),
  cookies: Object.freeze({clear: () => native('cookies.clear')}), catalog: emptyCatalog,
  meta: Object.freeze({byIds: async () => null, byTitle: async () => null}),
  tmdb: Object.freeze({request: async () => unavailable('TMDB')}),
  browser: Object.freeze({capture: async () => unavailable('Navegador'), captureAll: async () => unavailable('Navegador')}),
  sync: Object.freeze({remoteChanged: () => unavailable('Sincronización Kino')}),
  library: Object.freeze({query: async () => unavailable('Biblioteca Kino'), get: async () => unavailable('Biblioteca Kino')}),
  ai: Object.freeze({text: async () => unavailable('IA Kino')}),
  room: Object.freeze({open: async () => unavailable('Salas Kino')}),
  qr: Object.freeze({scan: async () => unavailable('Lector QR')}),
  ws: Object.freeze({open: async () => unavailable('Sockets Kino')}),
  identity: Object.freeze({get: async () => unavailable('Identidad Kino')}),
  notifications: Object.freeze({post: async () => unavailable('Notificaciones Kino')}),
});
