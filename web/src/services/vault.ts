import { generateMnemonic, validateMnemonic } from "@scure/bip39";
import { wordlist } from "@scure/bip39/wordlists/english.js";
import { gcm } from "@noble/ciphers/aes.js";
import { pbkdf2Async } from "@noble/hashes/pbkdf2.js";
import { sha256 } from "@noble/hashes/sha2.js";
import {
  finalizeEvent,
  generateSecretKey,
  getPublicKey,
  type EventTemplate,
} from "nostr-tools/pure";
import { decode, npubEncode } from "nostr-tools/nip19";
import type { Storage } from "./storage";
import type { NostrSigner } from "./contracts";

const ITERATIONS = 600_000;
const enc = new TextEncoder();
const dec = new TextDecoder();

export type Credentials = {
  mnemonic: string;
  nostrSecret: string;
  dataKey: string;
};
export type Cipher = { iv: string; ciphertext: string };
export type Backup = Cipher & {
  format: "ixh26-vault";
  version: 1;
  id: string;
  salt: string;
  kdf: "PBKDF2-SHA-256";
  iterations: number;
};

const bytes = (value: string) =>
  Uint8Array.from(atob(value), (c) => c.charCodeAt(0));
const base64 = (value: Uint8Array) => {
  let binary = "";
  for (let i = 0; i < value.length; i += 8192)
    binary += String.fromCharCode(...value.subarray(i, i + 8192));
  return btoa(binary);
};
export const hex = (value: Uint8Array) =>
  Array.from(value, (b) => b.toString(16).padStart(2, "0")).join("");
export const unhex = (value: string) => {
  if (!/^[a-f0-9]{64}$/i.test(value)) throw new Error("Chiave privata non valida.");
  return Uint8Array.from(value.match(/../g)!, (b) => parseInt(b, 16));
};

function randomUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = hex(b);
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function hasWebCrypto(): boolean {
  return typeof crypto !== "undefined" && Boolean(crypto.subtle);
}

export function newCredentials(
  mnemonic?: string,
  nostrKey?: string,
): Credentials {
  const words =
    mnemonic?.trim().toLowerCase().split(/\s+/).join(" ") ||
    generateMnemonic(wordlist, 128);
  if (!validateMnemonic(words, wordlist))
    throw new Error("Frase di recupero non valida.");
  let secret = generateSecretKey();
  if (nostrKey?.trim()) {
    const raw = nostrKey.trim();
    if (/^[a-f0-9]{64}$/i.test(raw)) {
      secret = unhex(raw);
    } else {
      try {
        const key = decode(raw);
        if (key.type !== "nsec") throw new Error();
        secret = new Uint8Array(key.data);
        getPublicKey(secret);
      } catch {
        throw new Error("Inserisci una chiave privata Nostr nsec valida.");
      }
    }
  }
  getPublicKey(secret);
  return {
    mnemonic: words,
    nostrSecret: hex(secret),
    dataKey: base64(crypto.getRandomValues(new Uint8Array(32))),
  };
}

type KeyMaterial = CryptoKey | Uint8Array;

async function derivePasswordKey(
  password: string,
  salt: Uint8Array,
): Promise<KeyMaterial> {
  if (hasWebCrypto()) {
    const material = await crypto.subtle.importKey(
      "raw",
      enc.encode(password),
      "PBKDF2",
      false,
      ["deriveKey"],
    );
    return crypto.subtle.deriveKey(
      {
        name: "PBKDF2",
        hash: "SHA-256",
        salt: salt.buffer as ArrayBuffer,
        iterations: ITERATIONS,
      },
      material,
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"],
    );
  }
  return pbkdf2Async(sha256, enc.encode(password), salt, {
    c: ITERATIONS,
    dkLen: 32,
  });
}

async function importDataKey(rawBytes: Uint8Array): Promise<KeyMaterial> {
  if (hasWebCrypto()) {
    return crypto.subtle.importKey(
      "raw",
      rawBytes.buffer as ArrayBuffer,
      "AES-GCM",
      false,
      ["encrypt", "decrypt"],
    );
  }
  return new Uint8Array(rawBytes);
}

export async function encrypt(
  key: KeyMaterial,
  value: unknown,
  context: string,
): Promise<Cipher> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const plain = enc.encode(JSON.stringify(value));
  const aad = enc.encode(context);
  if (key instanceof Uint8Array) {
    const ct = gcm(key, iv, aad).encrypt(plain);
    return { iv: base64(iv), ciphertext: base64(ct) };
  }
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv, additionalData: aad },
    key,
    plain,
  );
  return { iv: base64(iv), ciphertext: base64(new Uint8Array(ciphertext)) };
}

export async function decrypt<T>(
  key: KeyMaterial,
  value: Cipher,
  context: string,
): Promise<T> {
  const iv = bytes(value.iv);
  const ct = bytes(value.ciphertext);
  const aad = enc.encode(context);
  if (key instanceof Uint8Array) {
    const plain = gcm(key, iv, aad).decrypt(ct);
    return JSON.parse(dec.decode(plain));
  }
  const result = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv, additionalData: aad },
    key,
    ct,
  );
  return JSON.parse(dec.decode(result));
}

export class SessionLease {
  private releaseLock?: () => void;
  async acquire(id: string) {
    if (typeof navigator === "undefined" || !navigator.locks) return;
    await new Promise<void>((resolve, reject) => {
      void navigator.locks
        .request(`bitpin:${id}`, { ifAvailable: true }, async (lock) => {
          if (!lock) {
            reject(
              new Error(
                "Questo vault è già aperto in un'altra scheda. Bloccalo lì prima.",
              ),
            );
            return;
          }
          await new Promise<void>((release) => {
            this.releaseLock = release;
            resolve();
          });
        })
        .catch(reject);
    });
  }
  release() {
    this.releaseLock?.();
    this.releaseLock = undefined;
  }
}

export class VaultService {
  private credentials?: Credentials;
  private key?: KeyMaterial;
  private id?: string;
  private revision?: string;
  private generation = 0;
  private unlocking = false;
  private passwordChange?: Promise<void>;
  private writes = new Map<string, Promise<void>>();
  private pendingWrites = new Set<Promise<void>>();
  private lease = new SessionLease();
  constructor(private storage: Storage) {}
  exists() {
    return this.storage.get<Backup>("vault");
  }
  get unlocked() {
    return Boolean(this.credentials);
  }
  get secrets() {
    if (!this.credentials) throw new Error("Sblocca prima il profilo.");
    return this.credentials;
  }
  get publicKey() {
    return getPublicKey(unhex(this.secrets.nostrSecret));
  }
  get npub() {
    return npubEncode(this.publicKey);
  }
  get backupRevision() {
    if (!this.revision) throw new Error("Sblocca prima il profilo.");
    return this.revision;
  }
  asSigner(): NostrSigner {
    const pubkey = this.publicKey;
    const npub = this.npub;
    return {
      mode: "vault",
      pubkey,
      npub,
      sign: async (template: EventTemplate) => {
        const secret = unhex(this.secrets.nostrSecret);
        try {
          return finalizeEvent(template, secret);
        } finally {
          secret.fill(0);
        }
      },
    };
  }
  async create(credentials: Credentials, password: string) {
    if (await this.exists())
      throw new Error("Esiste già un profilo Nostr salvato in questo browser.");
    const id = randomUuid();
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const cipher = await encrypt(
      await derivePasswordKey(password, salt),
      credentials,
      `ixh26-vault:1:${id}`,
    );
    const backup: Backup = {
      ...cipher,
      format: "ixh26-vault",
      version: 1,
      id,
      salt: base64(salt),
      kdf: "PBKDF2-SHA-256",
      iterations: ITERATIONS,
    };
    await this.storeNew(backup);
  }
  async replaceWithImport(credentials: Credentials, password: string) {
    const id = randomUuid();
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const cipher = await encrypt(
      await derivePasswordKey(password, salt),
      credentials,
      `ixh26-vault:1:${id}`,
    );
    const backup: Backup = {
      ...cipher,
      format: "ixh26-vault",
      version: 1,
      id,
      salt: base64(salt),
      kdf: "PBKDF2-SHA-256",
      iterations: ITERATIONS,
    };
    await this.storage.put("vault", backup);
  }
  private async storeNew(backup: Backup) {
    const creation = new SessionLease();
    await creation.acquire("vault-creation");
    try {
      if (await this.exists())
        throw new Error("Esiste già un profilo in questo browser.");
      await this.storage.put("vault", backup);
    } finally {
      creation.release();
    }
  }
  async unlock(password: string) {
    if (this.unlocking) throw new Error("Sblocco già in corso.");
    this.unlocking = true;
    const generation = this.generation;
    try {
      await this.settled();
      if (this.credentials) throw new Error("Profilo già sbloccato.");
      if (generation !== this.generation) throw new Error("Sessione chiusa.");
      const backup = await this.exists();
      if (!backup) throw new Error("Crea o importa un'identità Nostr prima.");
      await this.lease.acquire(backup.id);
      try {
        const credentials = await this.decodeBackup(backup, password);
        const key = await importDataKey(bytes(credentials.dataKey));
        if (generation !== this.generation) throw new Error("Sessione chiusa.");
        this.credentials = credentials;
        this.key = key;
        this.id = backup.id;
        this.revision = backup.salt;
      } catch {
        this.lease.release();
        throw new Error("Password errata o vault danneggiato.");
      }
    } finally {
      this.unlocking = false;
    }
  }
  lock() {
    this.generation++;
    this.credentials = undefined;
    this.key = undefined;
    this.id = undefined;
    this.revision = undefined;
    this.releaseWhenIdle();
  }
  private releaseWhenIdle() {
    if (!this.credentials && !this.passwordChange && !this.pendingWrites.size)
      this.lease.release();
  }
  async settled() {
    while (this.passwordChange || this.pendingWrites.size)
      await Promise.allSettled([
        ...this.pendingWrites,
        ...(this.passwordChange ? [this.passwordChange] : []),
      ]);
  }
  changePassword(currentPassword: string, nextPassword: string): Promise<void> {
    if (this.passwordChange)
      return Promise.reject(new Error("Cambio password già in corso."));
    const credentials = this.secrets;
    const id = this.id!;
    const generation = this.generation;
    const operation = (async () => {
      const previous = await this.exists();
      if (!previous || previous.id !== id)
        throw new Error("Il vault locale è cambiato.");
      try {
        await this.decodeBackup(previous, currentPassword);
      } catch {
        throw new Error("Password attuale non corretta.");
      }
      const salt = crypto.getRandomValues(new Uint8Array(16));
      const cipher = await encrypt(
        await derivePasswordKey(nextPassword, salt),
        credentials,
        `ixh26-vault:1:${id}`,
      );
      if (generation !== this.generation || this.credentials !== credentials)
        throw new Error("Sessione bloccata prima del salvataggio.");
      await this.storage.put("vault", {
        ...previous,
        ...cipher,
        salt: base64(salt),
      });
      if (generation === this.generation) this.revision = base64(salt);
    })();
    this.passwordChange = operation.finally(() => {
      this.passwordChange = undefined;
      this.releaseWhenIdle();
    });
    return this.passwordChange;
  }
  private async decodeBackup(backup: Backup, password: string) {
    if (
      backup?.format !== "ixh26-vault" ||
      backup.version !== 1 ||
      backup.kdf !== "PBKDF2-SHA-256" ||
      backup.iterations !== ITERATIONS ||
      !/^[a-f0-9-]{36}$/i.test(backup.id)
    )
      throw new Error("Backup non supportato.");
    if (
      bytes(backup.salt).length !== 16 ||
      bytes(backup.iv).length !== 12 ||
      backup.ciphertext.length > 10000
    )
      throw new Error("Backup non valido.");
    const result = await decrypt<Credentials>(
      await derivePasswordKey(password, bytes(backup.salt)),
      backup,
      `ixh26-vault:1:${backup.id}`,
    );
    if (
      !validateMnemonic(result.mnemonic, wordlist) ||
      bytes(result.dataKey).length !== 32
    )
      throw new Error("Credenziali non valide.");
    getPublicKey(unhex(result.nostrSecret));
    return result;
  }
  async importBackup(text: string, password: string) {
    if (text.length > 20000) throw new Error("File backup troppo grande.");
    if (await this.exists())
      throw new Error("Esiste già un profilo in questo browser.");
    let backup: Backup;
    try {
      backup = JSON.parse(text) as Backup;
    } catch {
      throw new Error("File backup JSON non valido.");
    }
    await this.decodeBackup(backup, password);
    await this.storeNew(backup);
  }
  async exportBackup() {
    return JSON.stringify(await this.exists(), null, 2);
  }
  async save<T>(name: string, value: T) {
    if (!this.key || !this.id) throw new Error("Vault bloccato.");
    const record = `${this.id}:${name}`;
    const generation = this.generation;
    const cipher = encrypt(this.key, value, record);
    void cipher.catch(() => {});
    const previous = this.writes.get(record) ?? Promise.resolve();
    const writing = previous
      .catch(() => {})
      .then(async () => {
        const encrypted = await cipher;
        if (generation !== this.generation)
          throw new Error("Sessione chiusa prima del salvataggio.");
        await this.storage.put(record, encrypted);
      })
      .finally(() => {
        if (this.writes.get(record) === writing) this.writes.delete(record);
        this.pendingWrites.delete(writing);
        this.releaseWhenIdle();
      });
    this.writes.set(record, writing);
    this.pendingWrites.add(writing);
    return writing;
  }
  async load<T>(name: string): Promise<T | undefined> {
    if (!this.key || !this.id) throw new Error("Vault bloccato.");
    const key = this.key;
    const generation = this.generation;
    const record = `${this.id}:${name}`;
    const cipher = await this.storage.get<Cipher>(record);
    if (generation !== this.generation) throw new Error("Sessione chiusa.");
    const value = cipher ? await decrypt<T>(key, cipher, record) : undefined;
    if (generation !== this.generation) throw new Error("Sessione chiusa.");
    return value;
  }
}
