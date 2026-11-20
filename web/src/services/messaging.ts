import {
  finalizeEvent,
  getPublicKey,
  getEventHash,
  verifyEvent,
  type Event,
} from "nostr-tools/pure";
import {
  parseIdentity,
  profileMetadata,
  readProfile,
  newestProfile,
} from "./identity";
import {
  createRumor,
  createSeal,
  createWrap,
  unwrapEvent,
} from "nostr-tools/nip59";
import { RelayConnection, defaultRelayUrl, relayUrl } from "./relay";
import { unhex, type VaultService } from "./vault";
import { MAX_MESSAGE_LENGTH } from "./contracts";
import type {
  Message,
  MessengerState,
  MessagingPort,
  LocalProfile,
  ProfileMetadata,
  PublicProfile,
  ContactPreview,
} from "./contracts";

const emptyProfile = (): LocalProfile => ({
  draft: { name: "", about: "" },
  publishStatus: "unpublished",
});
type SavedProfile = LocalProfile & { pending?: Event };

type Outbox = {
  message: Message;
  recipient: Event;
  sender: Event;
  targets: string[];
  senderAccepted?: boolean;
  recipientAccepted?: boolean;
};
type SavedState = Pick<MessengerState, "contacts" | "messages" | "relays"> & {
  outbox: Outbox[];
};

export function openPrivateMessage(
  wrap: Event,
  secret: Uint8Array,
): {
  id: string;
  author: string;
  content: string;
  timestamp: number;
  peer: string;
} {
  const pubkey = getPublicKey(secret);
  if (
    wrap.kind !== 1059 ||
    wrap.id !== getEventHash(wrap) ||
    !verifyEvent(wrap) ||
    !wrap.tags.some((t) => t[0] === "p" && t[1] === pubkey)
  )
    throw new Error("Gift wrap non valido.");
  const rumor = unwrapEvent(wrap, secret);
  if (
    rumor.kind !== 14 ||
    rumor.id !== getEventHash(rumor) ||
    typeof rumor.content !== "string" ||
    rumor.content.length > MAX_MESSAGE_LENGTH ||
    !Number.isSafeInteger(rumor.created_at) ||
    rumor.created_at < 0 ||
    rumor.created_at > Math.floor(Date.now() / 1000) + 300
  )
    throw new Error("Messaggio privato non valido.");
  const recipients = rumor.tags.filter((t) => t[0] === "p").map((t) => t[1]);
  if (recipients.length !== 1 || !/^[a-f0-9]{64}$/.test(recipients[0]))
    throw new Error("Supportati solo messaggi diretti 1-a-1.");
  if (rumor.pubkey !== pubkey && recipients[0] !== pubkey)
    throw new Error("Messaggio non indirizzato a questa identità.");
  return {
    id: rumor.id,
    author: rumor.pubkey,
    content: rumor.content,
    timestamp: rumor.created_at,
    peer: rumor.pubkey === pubkey ? recipients[0] : rumor.pubkey,
  };
}

export class MessagingService implements MessagingPort {
  state: MessengerState = {
    contacts: [],
    messages: [],
    relays: [defaultRelayUrl()],
    status: "disconnected",
    profile: emptyProfile(),
    initialized: false,
  };
  private listeners = new Set<() => void>();
  private sockets = new Map<string, RelayConnection>();
  private inboxes = new Set<string>();
  private outbox: Outbox[] = [];
  private secret?: Uint8Array;
  private pubkey = "";
  private stopped = true;
  private generation = 0;
  private poll?: ReturnType<typeof setInterval>;
  private persistence: Promise<void> = Promise.resolve();
  private busy = new Set<string>();
  private profileBusy = false;
  private pendingProfile?: Event;
  private previews = new Map<string, ContactPreview>();
  constructor(private vault: VaultService) {
    this.restoreCacheSync();
  }
  private cacheKey() {
    try {
      return this.vault.unlocked
        ? `bitpin-msg-cache:${this.vault.publicKey}`
        : "";
    } catch {
      return "";
    }
  }
  private restoreCacheSync() {
    try {
      if (typeof localStorage === "undefined") return;
      const key = this.cacheKey();
      if (!key) return;
      const raw = localStorage.getItem(key);
      if (!raw) return;
      const data = JSON.parse(raw) as Partial<MessengerState>;
      if (Array.isArray(data.contacts)) this.state.contacts = data.contacts;
      if (Array.isArray(data.messages)) this.state.messages = data.messages;
      if (Array.isArray(data.relays) && data.relays.length)
        this.state.relays = data.relays;
      if (data.profile?.draft) this.state.profile = data.profile;
      this.state.initialized = true;
    } catch {
      /* ignore */
    }
  }
  private persistCacheSync() {
    try {
      if (typeof localStorage === "undefined") return;
      const key = this.cacheKey();
      if (!key) return;
      localStorage.setItem(
        key,
        JSON.stringify({
          contacts: this.state.contacts,
          messages: this.state.messages.slice(-200),
          relays: this.state.relays,
          profile: this.state.profile,
        }),
      );
    } catch {
      /* ignore */
    }
  }
  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }
  private emit() {
    this.persistCacheSync();
    if (!this.stopped) this.listeners.forEach((fn) => fn());
  }
  private save() {
    if (this.stopped) return Promise.resolve();
    const generation = this.generation;
    const snapshot: SavedState = structuredClone({
      contacts: this.state.contacts,
      messages: this.state.messages,
      relays: this.state.relays,
      outbox: this.outbox,
    });
    this.persistence = this.persistence
      .catch(() => {})
      .then(() => {
        this.assertSession(generation);
        return this.vault.save("messaging", snapshot);
      });
    return this.persistence;
  }
  private connection(url: string) {
    if (this.stopped || !this.secret)
      throw new Error("Sessione messaggistica bloccata.");
    let socket = this.sockets.get(url);
    if (!socket || !socket.active) {
      socket?.close();
      socket = new RelayConnection(url, new Uint8Array(this.secret));
      this.sockets.set(url, socket);
    }
    return socket;
  }
  async connect() {
    this.stopped = false;
    this.generation++;
    const generation = this.generation;
    this.secret = unhex(this.vault.secrets.nostrSecret);
    this.pubkey = getPublicKey(this.secret);
    const [saved, profile] = await Promise.all([
      this.vault.load<SavedState>("messaging"),
      this.vault.load<SavedProfile>("profile"),
    ]);
    if (this.stopped || generation !== this.generation) return;
    if (saved) {
      this.state = {
        contacts: saved.contacts,
        messages: saved.messages,
        relays: saved.relays.length ? saved.relays : [defaultRelayUrl()],
        status: "connecting",
        profile: emptyProfile(),
        initialized: false,
      };
      this.outbox = saved.outbox;
    }
    if (profile) {
      this.pendingProfile = profile.pending;
      this.state.profile = {
        ...profile,
        publishStatus:
          profile.publishStatus === "publishing"
            ? "failed"
            : profile.publishStatus,
      };
    }
    this.state.initialized = true;
    this.emit();
    await this.reconnect();
    for (const pendingItem of this.outbox) {
      if (!pendingItem.recipientAccepted || !pendingItem.senderAccepted) {
        void this.deliver(pendingItem);
      }
    }
    void this.restorePublishedProfile().catch(() => {});
    if (!this.stopped)
      this.poll = setInterval(() => {
        void this.reconnect();
      }, 6000);
  }
  private async reconnect() {
    if (this.stopped || !this.secret) return;
    const generation = this.generation;
    const results = await Promise.allSettled(
      this.state.relays.map(async (url) => {
        const previous = this.sockets.get(url);
        if (previous?.connected && this.inboxes.has(url)) return;
        this.inboxes.delete(url);
        const socket = this.connection(url);
        const event = finalizeEvent(
          {
            kind: 10050,
            created_at: Math.floor(Date.now() / 1000),
            content: "",
            tags: this.state.relays.map((r) => ["relay", r]),
          },
          this.secret!,
        );
        await socket.publish(event);
        if (this.stopped || generation !== this.generation) return;
        await socket.subscribe(
          { kinds: [1059], "#p": [this.pubkey] },
          (wrapped) => {
            void this.receive(wrapped);
          },
        );
        this.inboxes.add(url);
      }),
    );
    if (this.stopped || generation !== this.generation) return;
    this.state.status = results.some((r) => r.status === "fulfilled")
      ? "connected"
      : "offline";
    this.emit();
  }
  private async receive(wrapped: Event) {
    if (this.stopped || !this.secret) return;
    try {
      const event = openPrivateMessage(wrapped, this.secret);
      if (this.state.messages.some((m) => m.id === event.id)) return;
      this.state.messages.push({
        ...event,
        status: event.author === this.pubkey ? "accepted" : "received",
      });
      if (!this.state.contacts.some((c) => c.pubkey === event.peer))
        this.state.contacts.push({
          pubkey: event.peer,
          name: "",
          pending: true,
        });
      this.emit();
      await this.save();
    } catch {
      /* Ignora messaggi non destinati a noi. */
    }
  }
  private assertSession(generation = this.generation) {
    if (this.stopped || !this.secret || generation !== this.generation)
      throw new Error("Sessione bloccata o cambiata.");
  }
  private async lookupProfile(pubkey: string, hints: string[] = []) {
    const results = await Promise.allSettled(
      [...new Set([...this.state.relays, ...hints])].map((url) =>
        this.connection(url).query({ kinds: [0], authors: [pubkey], limit: 1 }),
      ),
    );
    const profiles = results.flatMap((result) =>
      result.status === "fulfilled"
        ? result.value
            .map((event) => readProfile(event, pubkey))
            .filter((p): p is PublicProfile => Boolean(p))
        : [],
    );
    return {
      profile: newestProfile(profiles),
      online: results.some((result) => result.status === "fulfilled"),
    };
  }
  private async restorePublishedProfile() {
    const generation = this.generation;
    const result = await this.lookupProfile(this.pubkey);
    this.assertSession(generation);
    const found = newestProfile(
      [result.profile, this.state.profile.published].filter(
        (p): p is PublicProfile => Boolean(p),
      ),
    );
    if (
      !found ||
      (this.state.profile.published?.id === found.id &&
        this.state.profile.published.metadata) ||
      this.profileBusy
    )
      return;
    const current = this.state.profile;
    const next: LocalProfile = {
      ...current,
      published: found,
      draft: current.draft.name
        ? current.draft
        : { name: found.name, about: found.about },
      publishStatus:
        current.draft.name &&
        (current.draft.name !== found.name ||
          current.draft.about !== found.about)
          ? "saved"
          : "published",
    };
    this.profileBusy = true;
    try {
      await this.vault.save("profile", {
        ...next,
        pending: this.pendingProfile,
      });
      this.assertSession(generation);
      this.state.profile = next;
      this.emit();
    } finally {
      this.profileBusy = false;
    }
  }
  async saveProfile(value: ProfileMetadata) {
    this.assertSession();
    if (this.profileBusy) throw new Error("Attendi il termine dell'operazione.");
    this.profileBusy = true;
    const generation = this.generation;
    try {
      const draft = profileMetadata(value);
      const published = this.state.profile.published;
      const next: LocalProfile = {
        draft,
        published,
        publishStatus:
          published?.name === draft.name && published.about === draft.about
            ? "published"
            : "saved",
      };
      await this.vault.save("profile", next);
      this.assertSession(generation);
      this.pendingProfile = undefined;
      this.state.profile = next;
      this.emit();
    } finally {
      this.profileBusy = false;
    }
  }
  async publishProfile() {
    this.assertSession();
    if (this.profileBusy) throw new Error("Attendi il termine dell'operazione.");
    const draft = profileMetadata(this.state.profile.draft);
    this.profileBusy = true;
    const generation = this.generation;
    try {
      this.state.profile = {
        ...this.state.profile,
        publishStatus: "publishing",
      };
      this.emit();
      if (!this.pendingProfile) {
        const discovered = await this.lookupProfile(this.pubkey);
        this.assertSession(generation);
        const previous = newestProfile(
          [discovered.profile, this.state.profile.published].filter(
            (profile): profile is PublicProfile => Boolean(profile),
          ),
        );
        const metadata: Record<string, unknown> = {
          ...previous?.metadata,
          ...draft,
        };
        if (typeof metadata.display_name === "string")
          metadata.display_name = draft.name;
        this.pendingProfile = finalizeEvent(
          {
            kind: 0,
            tags: [],
            content: JSON.stringify(metadata),
            created_at: Math.max(
              Math.floor(Date.now() / 1000),
              (previous?.createdAt ?? 0) + 1,
            ),
          },
          this.secret!,
        );
      }
      const event = this.pendingProfile;
      await this.vault.save("profile", {
        ...this.state.profile,
        pending: event,
      });
      this.assertSession(generation);
      const results = await Promise.allSettled(
        this.state.relays.map((url) => this.connection(url).publish(event)),
      );
      this.assertSession(generation);
      const acceptedRelays = results.filter(
        (result) => result.status === "fulfilled",
      ).length;
      if (!acceptedRelays)
        throw new Error(
          "Nessun relay ha accettato il profilo. Le modifiche locali sono salve.",
        );
      this.state.profile = {
        draft,
        published: readProfile(event, this.pubkey)!,
        publishStatus: "published",
        acceptedRelays,
      };
      await this.vault.save("profile", {
        ...this.state.profile,
        pending: event,
      });
      this.emit();
    } catch (error) {
      if (!this.stopped && generation === this.generation) {
        this.state.profile = { ...this.state.profile, publishStatus: "failed" };
        await this.vault.save("profile", {
          ...this.state.profile,
          pending: this.pendingProfile,
        });
        this.emit();
      }
      throw error;
    } finally {
      this.profileBusy = false;
    }
  }
  async previewContact(identity: string): Promise<ContactPreview> {
    this.assertSession();
    const generation = this.generation;
    const { pubkey, relayHints } = parseIdentity(identity);
    if (pubkey === this.pubkey)
      throw new Error("Questa è la tua identità.");
    const cached = this.state.contacts.find(
      (contact) => contact.pubkey === pubkey,
    );
    const result = await this.lookupProfile(pubkey, relayHints);
    this.assertSession(generation);
    const profile = newestProfile(
      [cached?.profile, result.profile].filter((p): p is PublicProfile =>
        Boolean(p),
      ),
    );
    const preview: ContactPreview = {
      pubkey,
      relayHints,
      profile,
      existing: Boolean(cached && !cached.pending),
      lookupStatus: result.online ? (profile ? "found" : "missing") : "offline",
    };
    this.previews.set(pubkey, preview);
    return preview;
  }
  async addContact(identity: string, name: string) {
    this.assertSession();
    const generation = this.generation;
    if (!this.state.initialized)
      throw new Error("Caricamento contatti in corso.");
    const { pubkey, relayHints } = parseIdentity(identity);
    if (pubkey === this.pubkey)
      throw new Error("Aggiungi un altro utente, non te stesso.");
    const previous = this.state.contacts;
    const cached = previous.find((contact) => contact.pubkey === pubkey);
    const profile = newestProfile(
      [this.previews.get(pubkey)?.profile, cached?.profile].filter(
        (p): p is PublicProfile => Boolean(p),
      ),
    );
    const contact = cached
      ? {
          ...cached,
          name: cached.pending ? name.trim().slice(0, 60) : cached.name,
          pending: false,
          profile,
          relayHints: relayHints.length ? relayHints : cached.relayHints,
        }
      : { pubkey, name: name.trim().slice(0, 60), relayHints, profile };
    this.state.contacts = cached
      ? previous.map((item) => (item.pubkey === pubkey ? contact : item))
      : [...previous, contact];
    try {
      await this.save();
      this.assertSession(generation);
    } catch (error) {
      if (!this.stopped && generation === this.generation)
        this.state.contacts = previous;
      throw error;
    }
    this.emit();
    return pubkey;
  }
  async setRelays(relays: string[]) {
    this.assertSession();
    const normalized = [
      ...new Set(relays.filter((r) => r.trim()).map(relayUrl)),
    ];
    if (normalized.length < 1 || normalized.length > 5)
      throw new Error("Configura tra 1 e 5 relay Nostr.");
    this.generation++;
    for (const socket of this.sockets.values()) socket.close();
    this.sockets.clear();
    this.inboxes.clear();
    this.state.relays = normalized;
    await this.save();
    await this.reconnect();
  }
  private async discover(peer: string) {
    const hints =
      this.state.contacts.find((contact) => contact.pubkey === peer)
        ?.relayHints ?? [];
    const results = await Promise.allSettled(
      [...new Set([...this.state.relays, ...hints])].map((url) =>
        this.connection(url).query({ kinds: [10050], authors: [peer] }),
      ),
    );
    const events = results
      .flatMap((r) => (r.status === "fulfilled" ? r.value : []))
      .filter(
        (event) =>
          event.kind === 10050 &&
          event.pubkey === peer &&
          event.id === getEventHash(event) &&
          verifyEvent(event),
      )
      .sort((a, b) => b.created_at - a.created_at || a.id.localeCompare(b.id));
    if (!events.length)
      throw new Error(
        "Lista inbox relay non trovata per il destinatario.",
      );
    const urls = events[0].tags
      .filter((t) => t[0] === "relay")
      .map((t) => relayUrl(t[1]));
    if (!urls.length || urls.length > 10)
      throw new Error("Il destinatario non ha una lista relay valida.");
    return [...new Set(urls)];
  }
  async send(peer: string, content: string) {
    if (!this.secret || this.stopped)
      throw new Error("Sblocca prima la sessione.");
    if (!this.state.contacts.some((c) => c.pubkey === peer && !c.pending))
      throw new Error("Conferma il contatto prima di rispondere.");
    if (!content.trim() || content.length > MAX_MESSAGE_LENGTH)
      throw new Error("Il messaggio deve contenere 1–8000 caratteri.");
    const generation = this.generation;
    const rumor = createRumor(
      {
        kind: 14,
        tags: [["p", peer]],
        content,
        created_at: Math.floor(Date.now() / 1000),
      },
      this.secret,
    );
    const duplicate = this.outbox.find((item) => item.message.id === rumor.id);
    if (duplicate) return this.deliver(duplicate);
    if (this.state.messages.some((message) => message.id === rumor.id)) return;
    const message: Message = {
      id: rumor.id,
      peer,
      author: this.pubkey,
      content,
      timestamp: rumor.created_at,
      status: "pending",
    };
    const item: Outbox = {
      message,
      targets: [],
      recipient: createWrap(createSeal(rumor, this.secret, peer), peer),
      sender: createWrap(
        createSeal(rumor, this.secret, this.pubkey),
        this.pubkey,
      ),
    };
    this.state.messages.push(message);
    this.outbox.push(item);
    this.emit();
    try {
      await this.save();
    } catch (error) {
      message.status = "failed";
      message.error = "Impossibile salvare il messaggio localmente.";
      this.emit();
      throw error;
    }
    this.assertSession(generation);
    await this.deliver(item);
  }
  async retry(id: string) {
    const item = this.outbox.find((o) => o.message.id === id);
    if (!item) throw new Error("Messaggio non presente nell'outbox.");
    await this.deliver(item);
  }
  private async deliver(item: Outbox) {
    if (this.busy.has(item.message.id)) return;
    this.busy.add(item.message.id);
    const generation = this.generation;
    const message = this.state.messages.find((m) => m.id === item.message.id)!;
    message.status = "pending";
    message.error = undefined;
    this.emit();
    try {
      if (!item.targets.length)
        item.targets = await this.discover(message.peer);
      if (this.stopped || generation !== this.generation) return;
      await this.save();
      this.assertSession(generation);
      const publish = async (event: Event, relays: string[]) => {
        const results = await Promise.allSettled(
          relays.map((url) => this.connection(url).publish(event)),
        );
        if (!results.some((r) => r.status === "fulfilled"))
          throw new Error("Nessun relay ha confermato il messaggio.");
      };
      if (!item.recipientAccepted) {
        await publish(item.recipient, item.targets);
        item.recipientAccepted = true;
        await this.save();
        this.assertSession(generation);
      }
      if (!item.senderAccepted) {
        await publish(item.sender, this.state.relays);
        item.senderAccepted = true;
      }
      message.status = "accepted";
    } catch (error) {
      message.status = "failed";
      message.error =
        error instanceof Error ? error.message : "Pubblicazione fallita.";
    } finally {
      this.busy.delete(item.message.id);
      if (!this.stopped && generation === this.generation) {
        await this.save();
        this.emit();
      }
    }
  }
  disconnect() {
    this.stopped = true;
    this.generation++;
    clearInterval(this.poll);
    for (const socket of this.sockets.values()) socket.close();
    this.sockets.clear();
    this.inboxes.clear();
    this.secret?.fill(0);
    this.secret = undefined;
    this.state.status = "disconnected";
    this.previews.clear();
  }
}
