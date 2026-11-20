import { finalizeEvent, type Event, verifyEvent } from "nostr-tools/pure";
import { matchFilter, type Filter } from "nostr-tools/filter";

export function defaultRelayUrl(): string {
  if (typeof window === "undefined") return "ws://127.0.0.1:8787/nostr";
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${proto}//${window.location.host}/nostr`;
}

function isPrivateHost(host: string): boolean {
  if (["localhost", "127.0.0.1", "[::1]"].includes(host)) return true;
  if (/^10\.\d+\.\d+\.\d+$/.test(host)) return true;
  if (/^192\.168\.\d+\.\d+$/.test(host)) return true;
  if (/^172\.(1[6-9]|2\d|3[01])\.\d+\.\d+$/.test(host)) return true;
  if (/^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d+\.\d+$/.test(host)) return true; // Tailscale CGNAT
  return false;
}

export function relayUrl(input: string) {
  const url = new URL(input.trim());
  const isLocal =
    isPrivateHost(url.hostname) ||
    (typeof window !== "undefined" && url.host === window.location.host);
  if (url.protocol !== "wss:" && !(url.protocol === "ws:" && isLocal)) {
    throw new Error("Usa un relay wss:// oppure ws:// locale/VPN.");
  }
  if (url.username || url.password || url.hash || url.search) {
    throw new Error("L'URL del relay non può contenere credenziali o query.");
  }
  return url.href;
}

export class RelayConnection {
  private socket: WebSocket;
  private ready: Promise<void>;
  private pending = new Map<
    string,
    {
      resolve: (result: void) => void;
      reject: (error: Error) => void;
      timer: ReturnType<typeof setTimeout>;
      promise: Promise<void>;
    }
  >();
  private subscriptions = new Map<
    string,
    {
      filter: Filter;
      event: (event: Event) => void;
      done?: () => void;
      failed?: (error: Error) => void;
    }
  >();
  private releaseReady?: () => void;
  private failReady?: (error: Error) => void;
  private authId?: string;
  private closed = false;
  private signAuth?: (template: {
    kind: number;
    created_at: number;
    tags: string[][];
    content: string;
  }) => Promise<Event>;

  constructor(
    readonly url: string,
    secretOrSigner:
      | Uint8Array
      | ((template: {
          kind: number;
          created_at: number;
          tags: string[][];
          content: string;
        }) => Promise<Event>),
    private status: () => void = () => {},
  ) {
    if (secretOrSigner instanceof Uint8Array) {
      const copy = new Uint8Array(secretOrSigner);
      this.signAuth = async (tpl) => finalizeEvent(tpl, copy);
    } else {
      this.signAuth = secretOrSigner;
    }
    this.socket = new WebSocket(url);
    this.ready = new Promise<void>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.terminate(new Error("Connessione al relay scaduta."));
        this.socket.close();
      }, 7000);
      this.releaseReady = () => {
        clearTimeout(timer);
        resolve();
      };
      this.failReady = (error) => {
        clearTimeout(timer);
        reject(error);
      };
      this.socket.onopen = () => {
        // NIP-42 relays challenge immediately; non-auth relays resolve quickly.
        setTimeout(() => {
          if (!this.authId && this.connected) this.releaseReady?.();
        }, 120);
        this.status();
      };
      this.socket.onerror = () => {
        if (this.closed) return;
        this.terminate(new Error("Impossibile connettersi al relay."));
        this.socket.close();
      };
    });
    void this.ready.catch(() => {});
    this.socket.onmessage = (e) => {
      void this.handle(e.data);
    };
    this.socket.onclose = () => {
      this.terminate(new Error("Relay disconnesso."));
    };
  }
  get active() {
    return (
      !this.closed &&
      (this.socket.readyState === WebSocket.CONNECTING ||
        this.socket.readyState === WebSocket.OPEN)
    );
  }
  get connected() {
    return !this.closed && this.socket.readyState === WebSocket.OPEN;
  }
  private async handle(raw: string) {
    if (this.closed || typeof raw !== "string" || raw.length > 131072) return;
    try {
      const [type, id, data, detail] = JSON.parse(raw);
      if (type === "AUTH" && typeof id === "string" && this.signAuth) {
        const auth = await this.signAuth({
          kind: 22242,
          created_at: Math.floor(Date.now() / 1000),
          tags: [
            ["relay", this.url],
            ["challenge", id],
          ],
          content: "",
        });
        this.authId = auth.id;
        this.socket.send(JSON.stringify(["AUTH", auth]));
      } else if (type === "OK") {
        if (id === this.authId && data === true) this.releaseReady?.();
        if (id === this.authId && data !== true) {
          this.terminate(new Error("Autenticazione relay rifiutata."));
          this.socket.close();
          return;
        }
        const item = this.pending.get(id);
        if (item) {
          clearTimeout(item.timer);
          this.pending.delete(id);
          if (data === true) item.resolve();
          else
            item.reject(
              new Error(
                typeof detail === "string"
                  ? detail
                  : "Il relay ha rifiutato l'evento.",
              ),
            );
        }
      } else if (type === "EVENT" && data && verifyEvent(data)) {
        const subscription = this.subscriptions.get(id);
        if (subscription && matchFilter(subscription.filter, data))
          subscription.event(data);
      } else if (type === "EOSE") this.subscriptions.get(id)?.done?.();
      else if (type === "CLOSED") {
        this.subscriptions
          .get(id)
          ?.failed?.(new Error("Il relay ha chiuso la sottoscrizione."));
        this.subscriptions.delete(id);
      }
    } catch {
      /* Ignora frame non validi. */
    }
  }
  async publish(event: Event) {
    await this.ready;
    if (!this.connected) throw new Error("Relay disconnesso.");
    const previous = this.pending.get(event.id);
    if (previous) return previous.promise;
    let resolve!: () => void;
    let reject!: (error: Error) => void;
    const promise = new Promise<void>((accept, refuse) => {
      resolve = accept;
      reject = refuse;
    });
    const timer = setTimeout(() => {
      this.pending.delete(event.id);
      reject(new Error("Timeout conferma dal relay. Riprova."));
    }, 8000);
    this.pending.set(event.id, { resolve, reject, timer, promise });
    try {
      this.socket.send(JSON.stringify(["EVENT", event]));
    } catch {
      clearTimeout(timer);
      this.pending.delete(event.id);
      reject(new Error("Invio al relay non riuscito."));
    }
    return promise;
  }
  async subscribe(
    filter: Filter,
    event: (event: Event) => void,
    done?: () => void,
    failed?: (error: Error) => void,
  ) {
    await this.ready;
    if (!this.connected) throw new Error("Relay disconnesso.");
    const id = crypto.randomUUID();
    this.subscriptions.set(id, { filter, event, done, failed });
    try {
      this.socket.send(JSON.stringify(["REQ", id, filter]));
    } catch {
      this.subscriptions.delete(id);
      throw new Error("Sottoscrizione al relay non riuscita.");
    }
    return () => {
      this.subscriptions.delete(id);
      if (this.connected) this.socket.send(JSON.stringify(["CLOSE", id]));
    };
  }
  async query(filter: Filter) {
    const events: Event[] = [];
    const ids = new Set<string>();
    const limit = Math.max(1, Math.min(filter.limit ?? 256, 256));
    return new Promise<Event[]>((resolve, reject) => {
      let stop: (() => void) | undefined;
      let finished = false;
      const finish = (error?: Error) => {
        if (finished) return;
        finished = true;
        clearTimeout(timer);
        stop?.();
        if (error) reject(error);
        else resolve(events);
      };
      const timer = setTimeout(
        () => finish(new Error("Query relay scaduta.")),
        7000,
      );
      void this.subscribe(
        filter,
        (event) => {
          if (finished || ids.has(event.id)) return;
          ids.add(event.id);
          events.push(event);
          if (events.length >= limit) finish();
        },
        () => finish(),
        finish,
      )
        .then((close) => {
          stop = close;
          if (finished) close();
        })
        .catch((error) => {
          finish(error);
        });
    });
  }
  private terminate(error: Error) {
    if (this.closed) return;
    this.closed = true;
    this.failReady?.(error);
    this.signAuth = undefined;
    for (const subscription of this.subscriptions.values())
      subscription.failed?.(error);
    this.subscriptions.clear();
    for (const item of this.pending.values()) {
      clearTimeout(item.timer);
      item.reject(error);
    }
    this.pending.clear();
    this.status();
  }
  close() {
    this.terminate(new Error("Sessione chiusa."));
    this.socket.close();
  }
}
