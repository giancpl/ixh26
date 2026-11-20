import { decode, npubEncode, nprofileEncode } from "nostr-tools/nip19";
import { verifyEvent, getEventHash, type Event } from "nostr-tools/pure";
import { relayUrl } from "./relay";
import type { Contact, ProfileMetadata, PublicProfile } from "./contracts";

export function profileMetadata(value: ProfileMetadata): ProfileMetadata {
  const name = value.name?.trim();
  const about = value.about?.trim() || "";
  if (typeof name !== "string" || !name || name.length > 60)
    throw new Error("Scegli un nome profilo tra 1 e 60 caratteri.");
  if (typeof about !== "string" || about.length > 280)
    throw new Error("Mantieni la bio entro 280 caratteri.");
  return { name, about };
}

export function parseIdentity(input: string): {
  pubkey: string;
  relayHints: string[];
} {
  const value = input.trim().replace(/^nostr:/i, "");
  if (!value || value.length > 5000)
    throw new Error("Incolla un npub o nprofile pubblico.");
  if (/^nsec/i.test(value))
    throw new Error("Questa è una chiave privata nsec. Usa un npub pubblico.");
  if (/^[a-f0-9]{64}$/i.test(value))
    return { pubkey: value.toLowerCase(), relayHints: [] };
  try {
    const decoded = decode(value);
    if (decoded.type === "npub")
      return { pubkey: decoded.data, relayHints: [] };
    if (decoded.type === "nprofile") {
      if (!/^[a-f0-9]{64}$/.test(decoded.data.pubkey)) throw new Error();
      const relayHints = [
        ...new Set((decoded.data.relays ?? []).map(relayUrl)),
      ];
      if (relayHints.length > 3)
        throw new Error("Condividi al massimo 3 relay hint.");
      return { pubkey: decoded.data.pubkey, relayHints };
    }
  } catch {
    throw new Error("Identità pubblica non valida. Usa npub o nprofile.");
  }
  throw new Error("Usa un'identità pubblica npub o nprofile.");
}

export function shareIdentity(pubkey: string, relays: string[]) {
  return `nostr:${nprofileEncode({
    pubkey,
    relays: relays
      .slice(0, 3)
      .map(relayUrl)
      .filter((url) => new TextEncoder().encode(url).length <= 255),
  })}`;
}

export function readProfile(
  event: Event,
  pubkey: string,
): PublicProfile | undefined {
  try {
    if (
      event.kind !== 0 ||
      event.pubkey !== pubkey ||
      event.id !== getEventHash(event) ||
      !verifyEvent(event) ||
      !Number.isSafeInteger(event.created_at) ||
      event.created_at < 0 ||
      event.created_at > Math.floor(Date.now() / 1000) + 300 ||
      event.content.length > 16000
    )
      return;
    const value = JSON.parse(event.content);
    if (
      !value ||
      typeof value !== "object" ||
      Array.isArray(value) ||
      (value.name !== undefined && typeof value.name !== "string") ||
      (value.about !== undefined && typeof value.about !== "string")
    )
      return;
    const displayName =
      typeof value.name === "string"
        ? value.name
        : typeof value.display_name === "string"
          ? value.display_name
          : "";
    return {
      name: displayName.trim().slice(0, 60),
      about: (value.about || "").trim().slice(0, 280),
      id: event.id,
      createdAt: event.created_at,
      metadata: value,
    };
  } catch {
    return;
  }
}

export function newestProfile(values: PublicProfile[]) {
  return values.sort(
    (a, b) => b.createdAt - a.createdAt || a.id.localeCompare(b.id),
  )[0];
}

export function shortNpub(pubkey: string) {
  const npub = npubEncode(pubkey);
  return `${npub.slice(0, 12)}…${npub.slice(-6)}`;
}

export function contactName(contact: Contact) {
  return contact.name || contact.profile?.name || shortNpub(contact.pubkey);
}

export const initials = (name: string) =>
  name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] || "")
    .join("")
    .toUpperCase() || "?";
