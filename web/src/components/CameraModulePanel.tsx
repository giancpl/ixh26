import { Bitcoin, Hourglass, Share2 } from "./PixelIcon";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { QRCode } from "react-qr-code";
import type {
  Board,
  CameraInboxItem,
  NostrSigner,
  PhotoPin,
  PhotoSource,
} from "../services/contracts";
import type { SocialService } from "../services/social";
import { AnchorBadge } from "./PinCard";
import { Feedback, Field, formatTime, sha256File, useOperation } from "./primitives";

type CameraTab = "android" | "web" | "file";

type StagedPhoto = {
  sha256: string;
  url: string;
  mime: string;
  width?: number;
  height?: number;
  source: PhotoSource;
  anchor: CameraInboxItem["anchor"];
  fromInbox?: boolean;
};

export default function CameraModulePanel({
  signer,
  social,
  boards,
  inbox,
  onInboxRefresh,
  onPublished,
  notify,
}: {
  signer: NostrSigner;
  social: SocialService;
  boards: Board[];
  inbox: CameraInboxItem[];
  onInboxRefresh: () => Promise<void>;
  onPublished: (pin: PhotoPin) => void;
  notify: (msg: string) => void;
}) {
  const [tab, setTab] = useState<CameraTab>("android");
  const [pairing, setPairing] = useState<{
    code: string;
    serverUrl: string;
    lanUrls: string[];
  }>();
  const [staged, setStaged] = useState<StagedPhoto>();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [board, setBoard] = useState(boards[0]?.title || "PhotoAnchor");
  const [tagsInput, setTagsInput] = useState("bitcoin, street");
  const [autoAnchor, setAutoAnchor] = useState(true);

  // Web Camera state
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | undefined>(undefined);
  const [cameraActive, setCameraActive] = useState(false);
  const [facingBack, setFacingBack] = useState(true);
  const [flashBlink, setFlashBlink] = useState(false);
  const [camError, setCamError] = useState("");
  const task = useOperation();

  function stopStream() {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = undefined;
    if (videoRef.current) videoRef.current.srcObject = null;
    setCameraActive(false);
  }

  useEffect(() => {
    let active = true;
    async function initPairing() {
      try {
        const [infoRes, pairRes] = await Promise.all([
          fetch("/api/info"),
          fetch("/api/pairing", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ pubkey: signer.pubkey, npub: signer.npub }),
          }),
        ]);
        if (!active || !infoRes.ok || !pairRes.ok) return;
        const info = (await infoRes.json()) as { lan_urls?: string[] };
        const pair = (await pairRes.json()) as { code: string };
        const lanUrls = info.lan_urls ?? [];
        const preferred =
          window.location.hostname === "127.0.0.1" ||
          window.location.hostname === "localhost"
            ? lanUrls[0] || window.location.origin
            : window.location.origin;
        setPairing({ code: pair.code, serverUrl: preferred, lanUrls });
      } catch {
        /* backend offline */
      }
    }
    void initPairing();
    return () => {
      active = false;
    };
  }, [signer.pubkey, signer.npub]);

  // Poll Android camera inbox while this module is open
  useEffect(() => {
    void onInboxRefresh();
    const timer = setInterval(() => {
      void onInboxRefresh();
    }, 2500);
    return () => clearInterval(timer);
  }, [onInboxRefresh]);

  useEffect(() => {
    if (tab !== "web") {
      stopStream();
      return;
    }
    let cancelled = false;
    async function start() {
      stopStream();
      setCamError("");
      if (!navigator.mediaDevices?.getUserMedia) {
        setCamError(
          "Fotocamera browser non disponibile (richiede HTTPS o localhost). Usa l'app Android PhotoAnchor o carica un file.",
        );
        return;
      }
      try {
        const s = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: facingBack ? "environment" : "user" } },
          audio: false,
        });
        if (cancelled) {
          s.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = s;
        if (videoRef.current) videoRef.current.srcObject = s;
        setCameraActive(true);
      } catch {
        setCamError("Permesso fotocamera negato o nessuna webcam trovata.");
      }
    }
    void start();
    const onHide = () => stopStream();
    window.addEventListener("pagehide", onHide);
    return () => {
      cancelled = true;
      window.removeEventListener("pagehide", onHide);
      stopStream();
    };
  }, [tab, facingBack]);

  async function uploadBlob(blob: Blob, filename: string, source: PhotoSource) {
    const sha256 = await sha256File(blob);
    const fd = new FormData();
    fd.append("file", blob, filename);
    fd.append("sha256", sha256);
    fd.append("pubkey", signer.pubkey);
    fd.append("source", source);
    fd.append("auto_anchor", autoAnchor ? "1" : "0");
    const resp = await fetch("/api/photos", { method: "POST", body: fd });
    if (!resp.ok) throw new Error(await resp.text());
    const data = (await resp.json()) as CameraInboxItem;
    setStaged({
      sha256: data.sha256,
      url: data.url,
      mime: data.mime,
      width: data.width,
      height: data.height,
      source: data.source,
      anchor: data.anchor,
    });
    if (!title) {
      setTitle(
        source === "web-camera"
          ? "Scatto dalla Fotocamera"
          : "Foto PhotoAnchor",
      );
    }
  }

  async function shootWebCamera() {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;
    setFlashBlink(true);
    setTimeout(() => setFlashBlink(false), 180);
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    ctx.drawImage(video, 0, 0);
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, "image/jpeg", 0.92),
    );
    if (!blob) throw new Error("Acquisizione fotogramma non riuscita.");
    await task.run(() =>
      uploadBlob(blob, `photoanchor_web_${Date.now()}.jpg`, "web-camera"),
    );
  }

  async function handlePublish(e: FormEvent) {
    e.preventDefault();
    if (!staged) return;
    await task.run(async () => {
      const parsedTags = tagsInput
        .split(/[,\s]+/)
        .map((t) => t.replace(/^#/, "").trim())
        .filter(Boolean);

      const pin = await social.publishPin({
        imageUrl: staged.url,
        sha256: staged.sha256,
        mime: staged.mime,
        width: staged.width,
        height: staged.height,
        title: title.trim() || "Scatto PhotoAnchor",
        description,
        board: board.trim() || "PhotoAnchor",
        tags: parsedTags,
        source: staged.source,
        otsUrl: staged.anchor.proof_url ?? undefined,
      });

      if (staged.fromInbox) {
        await social.ackCameraInbox(staged.sha256, pin.id);
        await onInboxRefresh();
      }
      setStaged(undefined);
      setTitle("");
      setDescription("");
      notify("Foto firmata con Nostr e pubblicata nella tua bacheca!");
      onPublished(pin);
    });
  }

  const pairUri = pairing
    ? `photoanchor://pair?url=${encodeURIComponent(pairing.serverUrl)}&code=${pairing.code}`
    : "";

  return (
    <div>
      <div className="segmented" role="tablist">
        <button
          type="button"
          className={tab === "android" ? "selected" : ""}
          onClick={() => setTab("android")}
        >
          App Android PhotoAnchor ({inbox.length})
        </button>
        <button
          type="button"
          className={tab === "web" ? "selected" : ""}
          onClick={() => setTab("web")}
        >
          Fotocamera Browser
        </button>
        <button
          type="button"
          className={tab === "file" ? "selected" : ""}
          onClick={() => setTab("file")}
        >
          Carica da Galleria
        </button>
      </div>

      <Feedback message={task.error} dismiss={task.clearError} />

      <div className="camera-grid">
        {/* Left Column: Camera Source */}
        <div className="surf" style={{ padding: 20 }}>
          {tab === "android" && (
            <>
              <h3 style={{ marginBottom: 6 }}>
                Collega la tua app Android PhotoAnchor
              </h3>
              <p className="field-hint" style={{ marginBottom: 14 }}>
                Apri l'app <strong>PhotoAnchor</strong> sul telefono, tocca{" "}
                <strong>Collega Web</strong> in alto e inserisci URL e Codice qui
                sotto (oppure apri il link su telefono). Ogni scatto verrà
                calcolato in SHA-256 sul telefono, ancorato su Bitcoin e inviato
                subito qui.
              </p>

              {pairing ? (
                <div className="pairing-box">
                  <div className="qr-frame">
                    <QRCode value={pairUri} size={136} />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div className="field-hint">Codice Pairing</div>
                    <div className="pairing-code-pill mono">{pairing.code}</div>

                    <div className="field-hint" style={{ marginTop: 8 }}>
                      URL Server per il telefono (stesso Wi-Fi):
                    </div>
                    <div
                      style={{
                        display: "flex",
                        gap: 6,
                        marginTop: 4,
                        alignItems: "center",
                      }}
                    >
                      <input
                        className="mono"
                        value={pairing.serverUrl}
                        onChange={(e) =>
                          setPairing({ ...pairing, serverUrl: e.target.value })
                        }
                      />
                      <button
                        type="button"
                        onClick={() => {
                          void navigator.clipboard?.writeText(pairUri);
                          notify("Link photoanchor://pair copiato!");
                        }}
                      >
                        <Share2 size={14} />
                      </button>
                    </div>
                    {pairing.lanUrls.length > 0 && (
                      <div
                        style={{
                          display: "flex",
                          flexWrap: "wrap",
                          gap: 6,
                          marginTop: 6,
                        }}
                      >
                        {pairing.lanUrls.map((u) => (
                          <button
                            key={u}
                            type="button"
                            className="chip-btn"
                            style={{ minHeight: 28, fontSize: 11 }}
                            onClick={() => setPairing({ ...pairing, serverUrl: u })}
                          >
                            {u}
                          </button>
                        ))}
                      </div>
                    )}
                    <div style={{ marginTop: 8 }}>
                      <a href={pairUri} className="badge solid" style={{ textDecoration: "none" }}>
                        Apri in PhotoAnchor Android
                      </a>
                    </div>
                  </div>
                </div>
              ) : (
                <p className="field-hint">Generazione codice pairing…</p>
              )}

              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  marginBottom: 10,
                }}
              >
                <h4>Scatti in arrivo dalla Fotocamera ({inbox.length})</h4>
                <button
                  type="button"
                  style={{ minHeight: 34, padding: "4px 10px", fontSize: 12 }}
                  onClick={() => void onInboxRefresh()}
                >
                  Aggiorna
                </button>
              </div>

              {inbox.length === 0 ? (
                <div
                  style={{
                    padding: 18,
                    borderRadius: 12,
                    border: "1px dashed var(--bd)",
                    textAlign: "center",
                  }}
                >
                  <p className="field-hint">
                    Nessuna foto in attesa dall'app Android. Scatta una foto con
                    PhotoAnchor per vederla apparire qui in tempo reale.
                  </p>
                </div>
              ) : (
                <div className="inbox-list">
                  {inbox.map((item) => (
                    <div key={item.sha256} className="inbox-row">
                      <img src={item.url} alt="Anteprima scatto" />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div className="mono" style={{ fontSize: 11 }}>
                          {item.sha256.slice(0, 16)}…
                        </div>
                        <div className="field-hint">
                          {formatTime(item.captured_at)} ·{" "}
                          {Math.round(item.size_bytes / 1024)} KB
                        </div>
                        <div style={{ marginTop: 4 }}>
                          <AnchorBadge anchor={item.anchor} />
                        </div>
                      </div>
                      <button
                        type="button"
                        className="btn-primary"
                        onClick={() => {
                          setStaged({
                            sha256: item.sha256,
                            url: item.url,
                            mime: item.mime,
                            width: item.width,
                            height: item.height,
                            source: item.source,
                            anchor: item.anchor,
                            fromInbox: true,
                          });
                          setTitle(item.title || "Scatto PhotoAnchor");
                        }}
                      >
                        Seleziona
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </>
          )}

          {tab === "web" && (
            <div>
              <Feedback message={camError} />
              <div className="phone-viewfinder">
                <video ref={videoRef} autoPlay playsInline muted />
                <div className={`vf-flash ${flashBlink ? "on" : ""}`} />
                <div className="vf-bar">
                  <span className="badge solid">PhotoAnchor Web</span>
                  <button
                    type="button"
                    className="shutter-ring"
                    aria-label="Scatta foto"
                    disabled={!cameraActive || task.busy}
                    onClick={() => void shootWebCamera()}
                  >
                    <span className="shutter-core" />
                  </button>
                  <button
                    type="button"
                    className="btn-pill"
                    style={{
                      background: "rgba(255,255,255,0.2)",
                      color: "#fff",
                      border: 0,
                    }}
                    onClick={() => setFacingBack(!facingBack)}
                  >
                    Gira
                  </button>
                </div>
              </div>
              <p className="field-hint" style={{ marginTop: 10 }}>
                Calcola SHA-256 nel browser prima dell'invio e ancora su Bitcoin
                con OpenTimestamps.
              </p>
            </div>
          )}

          {tab === "file" && (
            <div>
              <h3 style={{ marginBottom: 8 }}>
                Importa da Galleria o Pictures/PhotoAnchor
              </h3>
              <p className="field-hint" style={{ marginBottom: 14 }}>
                Seleziona una foto JPEG, PNG o WebP salvata dal tuo modulo
                fotocamera Android o dal computer. Calcoliamo l'impronta SHA-256
                esatta senza ricomprimere i byte originali.
              </p>
              <Field label="Seleziona file immagine">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) {
                      void task.run(() => uploadBlob(f, f.name, "upload"));
                    }
                  }}
                />
              </Field>
              <label className="checkbox-row">
                <input
                  type="checkbox"
                  checked={autoAnchor}
                  onChange={(e) => setAutoAnchor(e.target.checked)}
                />
                Ancora automaticamente su Bitcoin (OpenTimestamps) al caricamento
              </label>
            </div>
          )}
        </div>

        {/* Right Column: Publish Pin & Bitcoin Anchor Preview */}
        <div className="surf" style={{ padding: 20 }}>
          <h3 style={{ marginBottom: 12 }}>Pubblica Pin su Nostr</h3>
          {!staged ? (
            <div
              style={{
                padding: 28,
                borderRadius: 14,
                background: "var(--soft)",
                border: "1px dashed var(--bd)",
                textAlign: "center",
              }}
            >
              <Bitcoin size={28} />
              <p style={{ fontWeight: 700, marginTop: 10 }}>
                Seleziona o scatta una foto a sinistra
              </p>
              <p className="field-hint" style={{ marginTop: 6 }}>
                Potrai assegnare un titolo, scegliere una bacheca stile
                Pinterest, verificare l'ancoraggio OpenTimestamps e firmare il
                post con la tua chiave Nostr.
              </p>
            </div>
          ) : (
            <form onSubmit={(e) => void handlePublish(e)}>
              <div
                style={{
                  borderRadius: 14,
                  overflow: "hidden",
                  border: "1px solid var(--bd)",
                  marginBottom: 14,
                  maxHeight: 260,
                  background: "#0d1124",
                }}
              >
                <img
                  src={staged.url}
                  alt="Foto pronta"
                  style={{
                    width: "100%",
                    maxHeight: 260,
                    objectFit: "contain",
                    display: "block",
                  }}
                />
              </div>

              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  gap: 8,
                  marginBottom: 12,
                  flexWrap: "wrap",
                }}
              >
                <span className="mono" style={{ fontSize: 11 }}>
                  SHA-256: {staged.sha256.slice(0, 18)}…
                </span>
                <AnchorBadge anchor={staged.anchor} />
              </div>

              {staged.anchor.status === "UNANCHORED" && (
                <button
                  type="button"
                  style={{ marginBottom: 12 }}
                  onClick={() =>
                    void task.run(async () => {
                      const next = await social.stampAnchor(staged.sha256);
                      setStaged({ ...staged, anchor: next });
                    })
                  }
                >
                  <Hourglass size={14} /> Esegui anchoring OpenTimestamps ora
                </button>
              )}

              <Field label="Titolo del Pin">
                <input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Es. Tramonto al Politecnico"
                  maxLength={120}
                  required
                />
              </Field>

              <Field label="Descrizione (opzionale)">
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Racconta questo scatto..."
                  rows={2}
                  maxLength={500}
                />
              </Field>

              <div className="two-cols">
                <Field label="Bacheca (Board)">
                  <input
                    value={board}
                    onChange={(e) => setBoard(e.target.value)}
                    list="publish-boards"
                    placeholder="PhotoAnchor"
                    maxLength={50}
                  />
                  <datalist id="publish-boards">
                    {boards.map((b) => (
                      <option key={b.id} value={b.title} />
                    ))}
                    <option value="PhotoAnchor" />
                    <option value="Architettura" />
                    <option value="Street" />
                    <option value="Bitcoin" />
                  </datalist>
                </Field>

                <Field label="Tag (#)">
                  <input
                    value={tagsInput}
                    onChange={(e) => setTagsInput(e.target.value)}
                    placeholder="bitcoin, torino, street"
                  />
                </Field>
              </div>

              <div style={{ display: "flex", gap: 10 }}>
                <button
                  type="button"
                  onClick={() => setStaged(undefined)}
                  disabled={task.busy}
                >
                  Annulla
                </button>
                <button
                  className="btn-primary wide"
                  disabled={task.busy}
                >
                  {task.busy
                    ? "Firma Nostr & pubblicazione…"
                    : "Firma con Nostr & Pubblica Pin"}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
