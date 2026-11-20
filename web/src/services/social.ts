import { getEventHash, verifyEvent, type Event } from "nostr-tools/pure";
import { RelayConnection, defaultRelayUrl, relayUrl } from "./relay";
import { newestProfile, readProfile } from "./identity";
import type {
  AnchorInfo,
  Board,
  CameraInboxItem,
  NostrSigner,
  NostrUserSummary,
  PhotoPin,
  PhotoSource,
  PinComment,
  PinReaction,
  PublicProfile,
} from "./contracts";

export const PHOTO_TAG = "photoanchor";
export const BOARD_TAG = "photoanchor-board";
export const COMMENT_TAG = "photoanchor-comment";

function tagValue(tags: string[][], key: string): string {
  return tags.find((t) => t[0] === key && typeof t[1] === "string")?.[1] ?? "";
}

function tagValues(tags: string[][], key: string): string[] {
  return tags
    .filter((t) => t[0] === key && typeof t[1] === "string")
    .map((t) => t[1]);
}

export function slugifyBoard(name: string): string {
  const clean = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
  return clean || "bacheca";
}

export function parsePhotoPin(event: Event): PhotoPin | undefined {
  if (
    event.kind !== 1 ||
    event.id !== getEventHash(event) ||
    !verifyEvent(event)
  ) {
    return undefined;
  }
  const hasPhotoTag = event.tags.some(
    (t) => t[0] === "t" && t[1] === PHOTO_TAG,
  );
  if (!hasPhotoTag) return undefined;
  const sha256 = tagValue(event.tags, "x").toLowerCase();
  const imageUrl = tagValue(event.tags, "url");
  if (!/^[0-9a-f]{64}$/.test(sha256) || !imageUrl) return undefined;

  const dim = tagValue(event.tags, "dim");
  const mDim = /^(\d+)x(\d+)$/.exec(dim);
  const title = tagValue(event.tags, "title") || "Scatto PhotoAnchor";
  const board = tagValue(event.tags, "board") || "Generale";
  const mime = tagValue(event.tags, "m") || "image/jpeg";
  const otsUrl = tagValue(event.tags, "ots") || undefined;
  const rawSource = tagValue(event.tags, "source");
  const source: PhotoSource =
    rawSource === "android-photoanchor" || rawSource === "web-camera"
      ? rawSource
      : "upload";
  const tags = tagValues(event.tags, "t").filter(
    (t) => t !== PHOTO_TAG && t !== COMMENT_TAG,
  );

  const cleanDesc = event.content
    .replace(
      new RegExp(`\\n?${imageUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`),
      "",
    )
    .trim();

  return {
    id: event.id,
    pubkey: event.pubkey,
    createdAt: event.created_at,
    title,
    description: cleanDesc,
    imageUrl,
    sha256,
    mime,
    width: mDim ? Number(mDim[1]) : undefined,
    height: mDim ? Number(mDim[2]) : undefined,
    board,
    tags,
    source,
    otsUrl,
    event,
  };
}

export function parseBoard(event: Event): Board | undefined {
  if (
    event.kind !== 30001 ||
    event.id !== getEventHash(event) ||
    !verifyEvent(event)
  ) {
    return undefined;
  }
  const dTag = tagValue(event.tags, "d");
  if (!dTag.startsWith("photoanchor-board:")) return undefined;
  const title =
    tagValue(event.tags, "title") ||
    dTag.replace("photoanchor-board:", "") ||
    "Bacheca";
  const pinIds = [...new Set(tagValues(event.tags, "e"))].filter((id) =>
    /^[0-9a-f]{64}$/.test(id),
  );
  return {
    id: `${event.pubkey}:${dTag}`,
    dTag,
    pubkey: event.pubkey,
    title,
    pinIds,
    updatedAt: event.created_at,
  };
}

export type SocialState = {
  pins: PhotoPin[];
  boards: Map<string, Board>;
  profiles: Map<string, PublicProfile>;
  reactions: Map<string, PinReaction[]>;
  comments: Map<string, PinComment[]>;
  anchors: Map<string, AnchorInfo>;
  follows: Map<string, { createdAt: number; targets: string[] }>;
  relays: string[];
  relayStatus: "disconnected" | "connecting" | "connected" | "offline";
};

const SOCIAL_CACHE_KEY = "bitpin-social-cache-v1";

type SerializedSocialCache = {
  pins: PhotoPin[];
  boards: Array<[string, Board]>;
  profiles: Array<[string, PublicProfile]>;
  reactions: Array<[string, PinReaction[]]>;
  comments: Array<[string, PinComment[]]>;
  anchors: Array<[string, AnchorInfo]>;
  follows: Array<[string, { createdAt: number; targets: string[] }]>;
  relays: string[];
};

export class SocialService {
  state: SocialState = {
    pins: [],
    boards: new Map(),
    profiles: new Map(),
    reactions: new Map(),
    comments: new Map(),
    anchors: new Map(),
    follows: new Map(),
    relays: [defaultRelayUrl()],
    relayStatus: "disconnected",
  };

  private listeners = new Set<() => void>();
  private sockets = new Map<string, RelayConnection>();
  private stops: Array<() => void> = [];
  private seenEvents = new Set<string>();
  private stopped = true;
  private anchorRefreshInFlight = false;
  private cacheTimer?: ReturnType<typeof setTimeout>;

  constructor(private signer: NostrSigner) {
    this.restoreCacheSync();
  }

  private restoreCacheSync() {
    try {
      if (typeof localStorage === "undefined") return;
      const raw = localStorage.getItem(SOCIAL_CACHE_KEY);
      if (!raw) return;
      const data = JSON.parse(raw) as Partial<SerializedSocialCache>;
      if (Array.isArray(data.pins)) this.state.pins = data.pins;
      if (Array.isArray(data.boards)) this.state.boards = new Map(data.boards);
      if (Array.isArray(data.profiles))
        this.state.profiles = new Map(data.profiles);
      if (Array.isArray(data.reactions))
        this.state.reactions = new Map(data.reactions);
      if (Array.isArray(data.comments))
        this.state.comments = new Map(data.comments);
      if (Array.isArray(data.anchors))
        this.state.anchors = new Map(data.anchors);
      if (Array.isArray(data.follows))
        this.state.follows = new Map(data.follows);
      if (Array.isArray(data.relays) && data.relays.length)
        this.state.relays = data.relays;
    } catch {
      /* ignore corrupt cache */
    }
  }

  private schedulePersistCache() {
    if (typeof localStorage === "undefined") return;
    if (this.cacheTimer) return;
    this.cacheTimer = setTimeout(() => {
      this.cacheTimer = undefined;
      try {
        const snapshot: SerializedSocialCache = {
          pins: this.state.pins.slice(0, 150),
          boards: [...this.state.boards.entries()],
          profiles: [...this.state.profiles.entries()],
          reactions: [...this.state.reactions.entries()],
          comments: [...this.state.comments.entries()],
          anchors: [...this.state.anchors.entries()],
          follows: [...this.state.follows.entries()],
          relays: this.state.relays,
        };
        localStorage.setItem(SOCIAL_CACHE_KEY, JSON.stringify(snapshot));
      } catch {
        /* ignore quota errors */
      }
    }, 150);
  }

  subscribe(fn: () => void) {
    this.listeners.add(fn);
    return () => {
      this.listeners.delete(fn);
    };
  }

  private emit() {
    this.schedulePersistCache();
    if (!this.stopped) this.listeners.forEach((fn) => fn());
  }

  async connect(relays?: string[]) {
    this.disconnect();
    this.stopped = false;
    this.state.relays = (relays?.length ? relays : [defaultRelayUrl()]).map(
      relayUrl,
    );
    this.state.relayStatus = "connecting";
    this.emit();

    const results = await Promise.allSettled(
      this.state.relays.map(async (url) => {
        const conn = new RelayConnection(
          url,
          (tpl) => this.signer.sign(tpl),
          () => this.emit(),
        );
        this.sockets.set(url, conn);

        const stopPins = await conn.subscribe(
          { kinds: [1], "#t": [PHOTO_TAG], limit: 250 },
          (ev) => this.ingest(ev),
          () => {
            void this.refreshAllAnchors();
            void this.fetchMissingAuthors();
          },
        );
        const stopBoards = await conn.subscribe(
          { kinds: [30001], "#t": [BOARD_TAG], limit: 250 },
          (ev) => this.ingest(ev),
        );
        const stopComments = await conn.subscribe(
          { kinds: [1], "#t": [COMMENT_TAG], limit: 250 },
          (ev) => this.ingest(ev),
        );
        const stopLikes = await conn.subscribe(
          { kinds: [7], "#t": [PHOTO_TAG], limit: 250 },
          (ev) => this.ingest(ev),
        );
        const stopProfiles = await conn.subscribe(
          { kinds: [0], limit: 250 },
          (ev) => this.ingest(ev),
        );
        const stopFollows = await conn.subscribe(
          { kinds: [3], limit: 250 },
          (ev) => this.ingest(ev),
        );
        this.stops.push(
          stopPins,
          stopBoards,
          stopComments,
          stopLikes,
          stopProfiles,
          stopFollows,
        );
      }),
    );

    if (this.stopped) return;
    this.state.relayStatus = results.some((r) => r.status === "fulfilled")
      ? "connected"
      : "offline";
    this.emit();
  }

  private ingest(event: Event) {
    if (this.stopped || this.seenEvents.has(event.id)) return;
    if (event.id !== getEventHash(event) || !verifyEvent(event)) return;
    this.seenEvents.add(event.id);

    if (event.kind === 0) {
      const prof = readProfile(event, event.pubkey);
      if (!prof) return;
      const prev = this.state.profiles.get(event.pubkey);
      const best = newestProfile(prev ? [prev, prof] : [prof]);
      if (best) this.state.profiles.set(event.pubkey, best);
      this.emit();
      return;
    }

    if (event.kind === 3) {
      const prev = this.state.follows.get(event.pubkey);
      if (!prev || event.created_at >= prev.createdAt) {
        const targets = [
          ...new Set(
            event.tags
              .filter(
                (t) => t[0] === "p" && typeof t[1] === "string" && /^[0-9a-f]{64}$/.test(t[1]),
              )
              .map((t) => t[1]),
          ),
        ];
        this.state.follows.set(event.pubkey, {
          createdAt: event.created_at,
          targets,
        });
        this.emit();
      }
      return;
    }

    if (event.kind === 30001) {
      const board = parseBoard(event);
      if (!board) return;
      const prev = this.state.boards.get(board.id);
      if (!prev || board.updatedAt >= prev.updatedAt) {
        this.state.boards.set(board.id, board);
        this.emit();
      }
      return;
    }

    if (event.kind === 7) {
      const pinId = tagValue(event.tags, "e");
      if (!/^[0-9a-f]{64}$/.test(pinId)) return;
      const list = this.state.reactions.get(pinId) ?? [];
      if (!list.some((r) => r.id === event.id)) {
        list.push({
          id: event.id,
          pinId,
          pubkey: event.pubkey,
          createdAt: event.created_at,
          content: event.content || "+",
        });
        this.state.reactions.set(pinId, list);
        this.emit();
      }
      return;
    }

    if (event.kind === 1) {
      const pin = parsePhotoPin(event);
      if (pin) {
        if (!this.state.pins.some((p) => p.id === pin.id)) {
          this.state.pins = [pin, ...this.state.pins].sort(
            (a, b) => b.createdAt - a.createdAt || a.id.localeCompare(b.id),
          );
          this.emit();
          void this.refreshAnchor(pin.sha256);
        }
        return;
      }

      const isComment = event.tags.some(
        (t) => t[0] === "t" && t[1] === COMMENT_TAG,
      );
      if (isComment) {
        const pinId = tagValue(event.tags, "e");
        if (!/^[0-9a-f]{64}$/.test(pinId)) return;
        const list = this.state.comments.get(pinId) ?? [];
        if (!list.some((c) => c.id === event.id)) {
          list.push({
            id: event.id,
            pinId,
            pubkey: event.pubkey,
            createdAt: event.created_at,
            content: event.content,
          });
          list.sort((a, b) => a.createdAt - b.createdAt);
          this.state.comments.set(pinId, list);
          this.emit();
        }
      }
    }
  }

  private async fetchMissingAuthors() {
    const authors = [
      ...new Set(this.state.pins.map((p) => p.pubkey)),
    ].filter((pk) => !this.state.profiles.has(pk));
    if (!authors.length) return;
    for (const conn of this.sockets.values()) {
      if (!conn.connected) continue;
      try {
        const events = await conn.query({ kinds: [0], authors, limit: 100 });
        events.forEach((e) => this.ingest(e));
      } catch {
        /* ignore offline relay */
      }
    }
  }

  private async publishToRelays(event: Event) {
    const active = [...this.sockets.values()];
    if (!active.length) throw new Error("Nessun relay Nostr configurato.");
    const results = await Promise.allSettled(
      active.map((conn) => conn.publish(event)),
    );
    if (!results.some((r) => r.status === "fulfilled")) {
      throw new Error("Nessun relay ha accettato l'evento Nostr.");
    }
    this.ingest(event);
  }

  isFollowing(targetPubkey: string): boolean {
    const mine = this.state.follows.get(this.signer.pubkey);
    return mine ? mine.targets.includes(targetPubkey) : false;
  }

  myFollowing(): string[] {
    return this.state.follows.get(this.signer.pubkey)?.targets ?? [];
  }

  followersOf(pubkey: string): string[] {
    const res: string[] = [];
    for (const [follower, data] of this.state.follows.entries()) {
      if (data.targets.includes(pubkey)) res.push(follower);
    }
    return res;
  }

  async toggleFollow(targetPubkey: string) {
    if (targetPubkey === this.signer.pubkey) return;
    const prev = this.state.follows.get(this.signer.pubkey);
    const current = prev?.targets ?? [];
    const next = current.includes(targetPubkey)
      ? current.filter((pk) => pk !== targetPubkey)
      : [...current, targetPubkey];

    const event = await this.signer.sign({
      kind: 3,
      created_at: Math.max(
        Math.floor(Date.now() / 1000),
        (prev?.createdAt ?? 0) + 1,
      ),
      tags: next.map((pk) => ["p", pk]),
      content: "",
    });
    await this.publishToRelays(event);
  }

  discoveredUsers(): NostrUserSummary[] {
    const keys = new Set<string>([this.signer.pubkey]);
    for (const pk of this.state.profiles.keys()) keys.add(pk);
    for (const pin of this.state.pins) keys.add(pin.pubkey);
    for (const board of this.state.boards.values()) keys.add(board.pubkey);

    const list: NostrUserSummary[] = [];
    for (const pubkey of keys) {
      const userPins = this.state.pins.filter((p) => p.pubkey === pubkey);
      const userBoards = [...this.state.boards.values()].filter(
        (b) => b.pubkey === pubkey,
      );
      list.push({
        pubkey,
        profile: this.state.profiles.get(pubkey),
        pinCount: userPins.length,
        boardCount: userBoards.length,
        followersCount: this.followersOf(pubkey).length,
        followingCount: this.state.follows.get(pubkey)?.targets.length ?? 0,
        latestPin: userPins[0],
      });
    }
    return list.sort((a, b) => {
      if (a.pubkey === this.signer.pubkey) return -1;
      if (b.pubkey === this.signer.pubkey) return 1;
      return b.pinCount - a.pinCount || b.followersCount - a.followersCount;
    });
  }

  async publishProfile(name: string, about: string) {
    const cleanName = name.trim().slice(0, 60);
    if (!cleanName) throw new Error("Inserisci un nome profilo.");
    const cleanAbout = about.trim().slice(0, 280);
    const prev = this.state.profiles.get(this.signer.pubkey);
    const event = await this.signer.sign({
      kind: 0,
      created_at: Math.max(
        Math.floor(Date.now() / 1000),
        (prev?.createdAt ?? 0) + 1,
      ),
      tags: [],
      content: JSON.stringify({ name: cleanName, about: cleanAbout }),
    });
    await this.publishToRelays(event);
  }

  async publishPin(input: {
    imageUrl: string;
    sha256: string;
    mime: string;
    width?: number;
    height?: number;
    title: string;
    description: string;
    board: string;
    tags: string[];
    source: PhotoSource;
    otsUrl?: string;
  }): Promise<PhotoPin> {
    const title = input.title.trim() || "Scatto PhotoAnchor";
    const board = input.board.trim() || "Generale";
    const cleanTags = [
      ...new Set(
        input.tags
          .map((t) => t.trim().toLowerCase().replace(/^#/, ""))
          .filter(Boolean),
      ),
    ];
    const dim =
      input.width && input.height ? `${input.width}x${input.height}` : "";
    const imetaParts = [
      `url ${input.imageUrl}`,
      `m ${input.mime}`,
      `x ${input.sha256}`,
      ...(dim ? [`dim ${dim}`] : []),
    ];

    const tags: string[][] = [
      ["t", PHOTO_TAG],
      ...cleanTags.map((t) => ["t", t]),
      ["title", title],
      ["board", board],
      ["x", input.sha256],
      ["url", input.imageUrl],
      ["m", input.mime],
      ["source", input.source],
      ["imeta", ...imetaParts],
    ];
    if (dim) tags.push(["dim", dim]);
    if (input.otsUrl) tags.push(["ots", input.otsUrl]);

    const desc = input.description.trim();
    const content = desc ? `${desc}\n${input.imageUrl}` : input.imageUrl;

    const event = await this.signer.sign({
      kind: 1,
      created_at: Math.floor(Date.now() / 1000),
      tags,
      content,
    });
    await this.publishToRelays(event);
    await this.saveToBoard(event.id, board);
    return parsePhotoPin(event)!;
  }

  async createBoard(boardTitle: string) {
    const cleanTitle = boardTitle.trim();
    if (!cleanTitle) throw new Error("Inserisci un nome per la bacheca.");
    const dTag = `photoanchor-board:${slugifyBoard(cleanTitle)}`;
    const existing = this.state.boards.get(`${this.signer.pubkey}:${dTag}`);
    const pinIds = existing ? existing.pinIds : [];
    const event = await this.signer.sign({
      kind: 30001,
      created_at: Math.max(
        Math.floor(Date.now() / 1000),
        (existing?.updatedAt ?? 0) + 1,
      ),
      tags: [
        ["d", dTag],
        ["title", cleanTitle],
        ["t", BOARD_TAG],
        ...pinIds.map((id) => ["e", id]),
      ],
      content: "",
    });
    await this.publishToRelays(event);
  }

  async saveToBoard(pinId: string, boardTitle: string) {
    const cleanTitle = boardTitle.trim() || "Preferiti";
    const dTag = `photoanchor-board:${slugifyBoard(cleanTitle)}`;
    const existing = this.state.boards.get(`${this.signer.pubkey}:${dTag}`);
    const pinIds = existing
      ? [...new Set([pinId, ...existing.pinIds])]
      : [pinId];
    const event = await this.signer.sign({
      kind: 30001,
      created_at: Math.max(
        Math.floor(Date.now() / 1000),
        (existing?.updatedAt ?? 0) + 1,
      ),
      tags: [
        ["d", dTag],
        ["title", cleanTitle],
        ["t", BOARD_TAG],
        ...pinIds.map((id) => ["e", id]),
      ],
      content: "",
    });
    await this.publishToRelays(event);
  }

  async toggleLike(pin: PhotoPin) {
    const list = this.state.reactions.get(pin.id) ?? [];
    if (list.some((r) => r.pubkey === this.signer.pubkey)) return;
    const event = await this.signer.sign({
      kind: 7,
      created_at: Math.floor(Date.now() / 1000),
      tags: [
        ["e", pin.id],
        ["p", pin.pubkey],
        ["t", PHOTO_TAG],
      ],
      content: "+",
    });
    await this.publishToRelays(event);
  }

  async addComment(pin: PhotoPin, text: string) {
    const clean = text.trim();
    if (!clean || clean.length > 1000)
      throw new Error("Scrivi un commento tra 1 e 1000 caratteri.");
    const event = await this.signer.sign({
      kind: 1,
      created_at: Math.floor(Date.now() / 1000),
      tags: [
        ["e", pin.id, "", "reply"],
        ["p", pin.pubkey],
        ["t", COMMENT_TAG],
      ],
      content: clean,
    });
    await this.publishToRelays(event);
  }

  async refreshAnchor(sha256: string): Promise<AnchorInfo | undefined> {
    try {
      const resp = await fetch(`/api/anchor/${sha256}`);
      if (!resp.ok) return undefined;
      const data = (await resp.json()) as AnchorInfo;
      this.state.anchors.set(sha256, data);
      this.emit();
      return data;
    } catch {
      return undefined;
    }
  }

  async refreshAllAnchors() {
    if (this.anchorRefreshInFlight) return;
    this.anchorRefreshInFlight = true;
    try {
      const unique = [...new Set(this.state.pins.map((p) => p.sha256))];
      await Promise.all(unique.map((sha) => this.refreshAnchor(sha)));
    } finally {
      this.anchorRefreshInFlight = false;
    }
  }

  async stampAnchor(sha256: string): Promise<AnchorInfo> {
    const resp = await fetch(`/api/anchor/${sha256}`, { method: "POST" });
    if (!resp.ok) throw new Error(await resp.text());
    const data = (await resp.json()) as AnchorInfo;
    this.state.anchors.set(sha256, data);
    this.emit();
    return data;
  }

  async upgradeAnchor(sha256: string): Promise<AnchorInfo> {
    const resp = await fetch(`/api/anchor/${sha256}/upgrade`, {
      method: "POST",
    });
    if (!resp.ok) throw new Error(await resp.text());
    const data = (await resp.json()) as AnchorInfo;
    this.state.anchors.set(sha256, data);
    this.emit();
    return data;
  }

  async fetchCameraInbox(): Promise<CameraInboxItem[]> {
    const resp = await fetch(
      `/api/camera/inbox?pubkey=${encodeURIComponent(this.signer.pubkey)}`,
    );
    if (!resp.ok) return [];
    const data = (await resp.json()) as { items?: CameraInboxItem[] };
    const items = data.items ?? [];
    for (const item of items) {
      if (item.anchor) this.state.anchors.set(item.sha256, item.anchor);
    }
    return items;
  }

  async ackCameraInbox(sha256: string, eventId?: string) {
    await fetch(`/api/camera/inbox/${sha256}/ack`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ event_id: eventId }),
    });
  }

  myBoards(): Board[] {
    return [...this.state.boards.values()]
      .filter((b) => b.pubkey === this.signer.pubkey)
      .sort((a, b) => b.updatedAt - a.updatedAt);
  }

  authorLabel(pubkey: string): string {
    const prof = this.state.profiles.get(pubkey);
    if (prof?.name) return prof.name;
    return `${pubkey.slice(0, 8)}…`;
  }

  disconnect() {
    this.stopped = true;
    for (const stop of this.stops) {
      try {
        stop();
      } catch {
        /* ignore */
      }
    }
    this.stops = [];
    for (const conn of this.sockets.values()) conn.close();
    this.sockets.clear();
    this.seenEvents.clear();
  }
}
