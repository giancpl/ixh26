import type { Event, EventTemplate } from "nostr-tools/pure";

export const MAX_MESSAGE_LENGTH = 8000;

export type ProfileMetadata = { name: string; about: string };
export type PublicProfile = ProfileMetadata & {
  id: string;
  createdAt: number;
  metadata?: Record<string, unknown>;
};
export type LocalProfile = {
  draft: ProfileMetadata;
  published?: PublicProfile;
  publishStatus:
    | "unpublished"
    | "saved"
    | "publishing"
    | "published"
    | "failed";
  acceptedRelays?: number;
};

export type Contact = {
  pending?: boolean;
  pubkey: string;
  name: string;
  profile?: PublicProfile;
  relayHints?: string[];
};
export type ContactPreview = {
  pubkey: string;
  relayHints: string[];
  profile?: PublicProfile;
  existing: boolean;
  lookupStatus: "found" | "missing" | "offline";
};
export type Message = {
  id: string;
  peer: string;
  author: string;
  content: string;
  timestamp: number;
  status: "pending" | "accepted" | "failed" | "received";
  error?: string;
};
export type MessengerState = {
  contacts: Contact[];
  messages: Message[];
  relays: string[];
  status: string;
  profile: LocalProfile;
  initialized: boolean;
};

export interface MessagingPort {
  state: MessengerState;
  connect(): Promise<void>;
  disconnect(): void;
  addContact(npub: string, name: string): Promise<string>;
  previewContact(identity: string): Promise<ContactPreview>;
  saveProfile(profile: ProfileMetadata): Promise<void>;
  publishProfile(): Promise<void>;
  send(peer: string, content: string): Promise<void>;
  retry(id: string): Promise<void>;
  setRelays(relays: string[]): Promise<void>;
  subscribe(listener: () => void): () => void;
}

export type AnchorDiagnostic = {
  code: string;
  message: string;
};

export type AnchorStatus =
  | "UNANCHORED"
  | "PENDING"
  | "CONFIRMED"
  | "INVALID"
  | "UNVERIFIABLE"
  | "ERROR";

export type AnchorInfo = {
  sha256: string;
  status: AnchorStatus;
  network: string;
  proof_url?: string | null;
  block_height?: number | null;
  block_time?: number | null;
  diagnostics?: AnchorDiagnostic[];
  updated_at?: number;
};

export type PhotoSource = "android-photoanchor" | "web-camera" | "upload";

export type PhotoPin = {
  id: string;
  pubkey: string;
  createdAt: number;
  title: string;
  description: string;
  imageUrl: string;
  sha256: string;
  mime: string;
  width?: number;
  height?: number;
  board: string;
  tags: string[];
  source: PhotoSource;
  otsUrl?: string;
  event: Event;
};

export type Board = {
  id: string;
  dTag: string;
  pubkey: string;
  title: string;
  pinIds: string[];
  updatedAt: number;
};

export type PinReaction = {
  id: string;
  pinId: string;
  pubkey: string;
  createdAt: number;
  content: string;
};

export type PinComment = {
  id: string;
  pinId: string;
  pubkey: string;
  createdAt: number;
  content: string;
};

export type CameraInboxItem = {
  sha256: string;
  url: string;
  mime: string;
  size_bytes: number;
  width?: number;
  height?: number;
  source: PhotoSource;
  captured_at: number;
  created_at: number;
  title?: string;
  description?: string;
  board?: string;
  anchor: AnchorInfo;
};

export type NostrUserSummary = {
  pubkey: string;
  profile?: PublicProfile;
  pinCount: number;
  boardCount: number;
  followersCount: number;
  followingCount: number;
  latestPin?: PhotoPin;
};

export interface NostrSigner {
  mode: "vault";
  pubkey: string;
  npub: string;
  sign(template: EventTemplate): Promise<Event>;
}
