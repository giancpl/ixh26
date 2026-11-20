import { describe, expect, it } from "vitest";
import { finalizeEvent, generateSecretKey, getPublicKey } from "nostr-tools/pure";
import { nsecEncode } from "nostr-tools/nip19";
import { VaultService, newCredentials, encrypt, decrypt } from "../src/services/vault";
import { parseBoard, parsePhotoPin, PHOTO_TAG, slugifyBoard } from "../src/services/social";
import type { Storage } from "../src/services/storage";

class MemoryStorage implements Storage {
  private map = new Map<string, unknown>();
  async get<T>(key: string) {
    return this.map.get(key) as T | undefined;
  }
  async put<T>(key: string, value: T) {
    this.map.set(key, structuredClone(value));
  }
  serialized() {
    return JSON.stringify([...this.map]);
  }
}

describe("Nostr auth & VaultService", () => {
  it("imports nsec, unlocks vault, signs Nostr events, and restores backup", async () => {
    const sk = generateSecretKey();
    const expectedPubkey = getPublicKey(sk);
    const nsec = nsecEncode(sk);

    const store = new MemoryStorage();
    const vault = new VaultService(store);
    const creds = newCredentials(undefined, nsec);
    await vault.create(creds, "pass123");
    await vault.unlock("pass123");

    expect(vault.publicKey).toBe(expectedPubkey);
    const signer = vault.asSigner();
    const ev = await signer.sign({
      kind: 1,
      created_at: 1700000000,
      tags: [["t", PHOTO_TAG]],
      content: "hello",
    });
    expect(ev.pubkey).toBe(expectedPubkey);
    expect(ev.sig).toMatch(/^[0-9a-f]{128}$/);

    const backup = await vault.exportBackup();
    vault.lock();

    const vault2 = new VaultService(new MemoryStorage());
    await vault2.importBackup(backup, "pass123");
    await vault2.unlock("pass123");
    expect(vault2.publicKey).toBe(expectedPubkey);
  }, 30_000);

  it("preserves an encrypted identity after failed unlock and persists no raw credentials", async () => {
    const store = new MemoryStorage();
    const credentials = newCredentials();
    const vault = new VaultService(store);
    await vault.create(credentials, "correct password");
    const original = await vault.exists();
    await vault.unlock("correct password");
    const publicKey = vault.publicKey;
    for (const secret of [credentials.mnemonic, credentials.nostrSecret, credentials.dataKey]) {
      expect(store.serialized()).not.toContain(secret);
    }
    vault.lock();

    const refreshed = new VaultService(store);
    await expect(refreshed.unlock("wrong password")).rejects.toThrow();
    expect(await refreshed.exists()).toEqual(original);
    expect(refreshed.unlocked).toBe(false);
    await refreshed.unlock("correct password");
    expect(refreshed.publicKey).toBe(publicKey);
    refreshed.lock();
  }, 30_000);

  it("interoperates between WebCrypto AES-GCM and @noble/ciphers fallback", async () => {
    const rawKey = crypto.getRandomValues(new Uint8Array(32));
    const webKey = await crypto.subtle.importKey("raw", rawKey, "AES-GCM", false, ["encrypt", "decrypt"]);
    const cipherFromNoble = await encrypt(rawKey, { ok: 42 }, "ctx");
    expect(await decrypt<{ ok: number }>(webKey, cipherFromNoble, "ctx")).toEqual({ ok: 42 });
    const cipherFromWeb = await encrypt(webKey, { ok: 99 }, "ctx");
    expect(await decrypt<{ ok: number }>(rawKey, cipherFromWeb, "ctx")).toEqual({ ok: 99 });
  });
});

describe("Nostr Pinterest pin & board parsing", () => {
  const sk = generateSecretKey();
  const sha = "a".repeat(64);

  it("parses valid PhotoAnchor pin event and rejects forged event", () => {
    const ev = finalizeEvent(
      {
        kind: 1,
        created_at: 1700000000,
        tags: [
          ["t", PHOTO_TAG],
          ["t", "torino"],
          ["title", "Mole Antonelliana"],
          ["board", "Architettura"],
          ["x", sha],
          ["url", `/api/media/${sha}`],
          ["m", "image/jpeg"],
          ["dim", "1200x1600"],
          ["source", "android-photoanchor"],
        ],
        content: `Scatto originale\n/api/media/${sha}`,
      },
      sk,
    );

    const pin = parsePhotoPin(ev);
    expect(pin).toBeDefined();
    expect(pin!.title).toBe("Mole Antonelliana");
    expect(pin!.description).toBe("Scatto originale");
    expect(pin!.board).toBe("Architettura");
    expect(pin!.width).toBe(1200);
    expect(pin!.height).toBe(1600);
    expect(pin!.source).toBe("android-photoanchor");
    expect(pin!.tags).toEqual(["torino"]);

    const forged = { ...ev, content: "Manomesso" };
    expect(parsePhotoPin(forged)).toBeUndefined();
  });

  it("parses board event and slugifies board titles", () => {
    expect(slugifyBoard("Torino & Bitcoin!")).toBe("torino-bitcoin");
    const pinId = "b".repeat(64);
    const ev = finalizeEvent(
      {
        kind: 30001,
        created_at: 1700000000,
        tags: [
          ["d", "photoanchor-board:architettura"],
          ["title", "Architettura"],
          ["t", "photoanchor-board"],
          ["e", pinId],
        ],
        content: "",
      },
      sk,
    );
    const board = parseBoard(ev);
    expect(board).toBeDefined();
    expect(board!.title).toBe("Architettura");
    expect(board!.pinIds).toEqual([pinId]);
  });
});
