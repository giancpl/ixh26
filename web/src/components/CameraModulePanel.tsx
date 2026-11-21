import {
  ArrowRight,
  Bitcoin,
  Camera,
  Globe,
  Hourglass,
  Smartphone,
} from "./PixelIcon";
import { useEffect, useRef, useState, type FormEvent } from "react";
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

function stageFromItem(item: CameraInboxItem, fromInbox: boolean): StagedPhoto {
  return {
    sha256: item.sha256,
    url: item.url,
    mime: item.mime || "image/jpeg",
    width: item.width,
    height: item.height,
    source: item.source || "android-photoanchor",
    anchor: item.anchor,
    fromInbox,
  };
}

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
  const isAndroidApp =
    typeof window !== "undefined" && Boolean(window.AndroidBridge);
  const [staged, setStaged] = useState<StagedPhoto>();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [board, setBoard] = useState(boards[0]?.title || "PhoTO");
  const [tagsInput, setTagsInput] = useState("");

  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | undefined>(undefined);
  const autoLaunched = useRef(false);
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

  function openNativeCamera() {
    window.AndroidBridge?.capturePhoto();
  }

  // Native Android camera result.
  useEffect(() => {
    if (typeof window === "undefined") return;
    window.__onAndroidPhotoCaptured = (itemJson: string) => {
      try {
        const data = JSON.parse(itemJson) as CameraInboxItem;
        setStaged(stageFromItem(data, true));
        setTitle((prev) => prev || "");
        notify("Foto certificata! Dai un titolo e pubblica.");
        void onInboxRefresh();
      } catch {
        /* ignore malformed payload */
      }
    };
    return () => {
      window.__onAndroidPhotoCaptured = undefined;
    };
  }, [notify, onInboxRefresh]);

  // On the Android app, tapping "Scatta" opens the camera straight away.
  useEffect(() => {
    if (!isAndroidApp || autoLaunched.current || staged) return;
    autoLaunched.current = true;
    openNativeCamera();
  }, [isAndroidApp, staged]);

  useEffect(() => {
    void onInboxRefresh();
    const timer = setInterval(() => void onInboxRefresh(), 4000);
    return () => clearInterval(timer);
  }, [onInboxRefresh]);

  // Browser camera (desktop / mobile web only).
  useEffect(() => {
    if (isAndroidApp || staged) {
      stopStream();
      return;
    }
    let cancelled = false;
    async function start() {
      stopStream();
      setCamError("");
      if (!navigator.mediaDevices?.getUserMedia) {
        setCamError(
          "Fotocamera non disponibile: serve HTTPS o localhost, oppure usa l'app Android.",
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
        setCamError("Permesso fotocamera negato o nessuna fotocamera trovata.");
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
  }, [isAndroidApp, staged, facingBack]);

  async function uploadCapture(blob: Blob) {
    const sha256 = await sha256File(blob);
    const fd = new FormData();
    fd.append("file", blob, `photoanchor_${Date.now()}.jpg`);
    fd.append("sha256", sha256);
    fd.append("pubkey", signer.pubkey);
    fd.append("source", "web-camera");
    fd.append("auto_anchor", "1");
    const resp = await fetch("/api/photos", { method: "POST", body: fd });
    if (!resp.ok) throw new Error(await resp.text());
    const data = (await resp.json()) as CameraInboxItem;
    setStaged(stageFromItem(data, false));
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
    await task.run(() => uploadCapture(blob));
  }

  function resetForm() {
    setStaged(undefined);
    setTitle("");
    setDescription("");
    setTagsInput("");
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
        title: title.trim() || "Scatto PhoTO",
        description,
        board: board.trim() || "PhoTO",
        tags: parsedTags,
        source: staged.source,
        otsUrl: staged.anchor.proof_url ?? undefined,
      });

      if (staged.fromInbox) {
        await social.ackCameraInbox(staged.sha256, pin.id);
        await onInboxRefresh();
      }
      resetForm();
      notify("Pin pubblicato su Nostr!");
      onPublished(pin);
    });
  }

  /* ---------- Step 2: publish ---------- */
  if (staged) {
    return (
      <div className="camera-centered-shell">
        <Feedback message={task.error} dismiss={task.clearError} />
        <section className="surf camera-card-single">
          <div className="step-indicator">
            <span className="step done">1 · Scatto</span>
            <span className="step active">2 · Pubblica</span>
          </div>

          <form onSubmit={(e) => void handlePublish(e)}>
            <div className="staged-preview-box">
              <img src={staged.url} alt="Foto scattata" />
              <div className="staged-preview-badge">
                <AnchorBadge anchor={staged.anchor} />
              </div>
            </div>

            {staged.anchor.status === "UNANCHORED" && (
              <button
                type="button"
                className="wide"
                style={{ marginBottom: 14 }}
                disabled={task.busy}
                onClick={() =>
                  void task.run(async () => {
                    const next = await social.stampAnchor(staged.sha256);
                    setStaged({ ...staged, anchor: next });
                  })
                }
              >
                <Hourglass size={14} /> Certifica su Bitcoin
              </button>
            )}

            <Field label="Titolo">
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Dai un titolo al tuo scatto"
                maxLength={120}
                autoFocus
                required
              />
            </Field>

            <Field label="Descrizione (opzionale)">
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Racconta questo scatto…"
                rows={2}
                maxLength={500}
              />
            </Field>

            <details className="more-options">
              <summary>Bacheca e tag</summary>
              <div className="two-cols" style={{ marginTop: 12 }}>
                <Field label="Bacheca">
                  <input
                    value={board}
                    onChange={(e) => setBoard(e.target.value)}
                    list="publish-boards"
                    placeholder="PhoTO"
                    maxLength={50}
                  />
                  <datalist id="publish-boards">
                    {boards.map((b) => (
                      <option key={b.id} value={b.title} />
                    ))}
                    <option value="PhoTO" />
                    <option value="Street" />
                    <option value="Bitcoin" />
                  </datalist>
                </Field>
                <Field label="Tag">
                  <input
                    value={tagsInput}
                    onChange={(e) => setTagsInput(e.target.value)}
                    placeholder="torino, street"
                  />
                </Field>
              </div>
            </details>

            <div className="publish-actions">
              <button
                type="button"
                onClick={() => {
                  resetForm();
                  if (isAndroidApp) openNativeCamera();
                }}
                disabled={task.busy}
              >
                Riscatta
              </button>
              <button className="btn-primary wide" disabled={task.busy}>
                {task.busy ? "Pubblicazione…" : "Pubblica"}
              </button>
            </div>
          </form>
        </section>
      </div>
    );
  }

  /* ---------- Step 1: capture ---------- */
  return (
    <div className="camera-centered-shell">
      <Feedback message={task.error} dismiss={task.clearError} />

      {isAndroidApp ? (
        <section className="surf camera-card-single camera-launch">
          <button
            type="button"
            className="shutter-launch"
            aria-label="Apri fotocamera"
            onClick={openNativeCamera}
            disabled={task.busy}
          >
            <span />
          </button>
          <h2>Scatta una foto</h2>
          <p className="field-hint camera-launch-hint">
            Solo foto scattate ora: ogni scatto viene firmato e certificato su
            Bitcoin prima di essere pubblicato.
          </p>
          <ol className="camera-steps">
            <li>
              <Camera size={13} /> Scatta
            </li>
            <li>
              <Bitcoin size={13} /> Certifica
            </li>
            <li>
              <Globe size={13} /> Pubblica
            </li>
          </ol>
        </section>
      ) : (
        <section className="surf camera-card-single">
          <Feedback message={camError} />
          <div className="phone-viewfinder">
            <video ref={videoRef} autoPlay playsInline muted />
            <div className={`vf-flash ${flashBlink ? "on" : ""}`} />
            <div className="vf-bar">
              <span style={{ width: 56 }} />
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
                className="btn-pill vf-flip"
                onClick={() => setFacingBack(!facingBack)}
                aria-label="Cambia fotocamera"
              >
                <Smartphone size={16} />
              </button>
            </div>
          </div>
          <p className="field-hint" style={{ marginTop: 10, textAlign: "center" }}>
            {task.busy
              ? "Calcolo SHA-256 e certificazione su Bitcoin…"
              : "Solo foto scattate ora vengono certificate e pubblicate."}
          </p>
        </section>
      )}

      {inbox.length > 0 && (
        <section className="surf camera-card-single" style={{ marginTop: 16 }}>
          <h4 style={{ textAlign: "center", marginBottom: 12 }}>
            Scatti da pubblicare ({inbox.length})
          </h4>
          <div className="inbox-list">
            {inbox.map((item) => (
              <button
                key={item.sha256}
                type="button"
                className="inbox-row inbox-row-btn"
                onClick={() => setStaged(stageFromItem(item, true))}
              >
                <img src={item.url} alt="Anteprima scatto" />
                <div style={{ flex: 1, minWidth: 0, textAlign: "left" }}>
                  <div style={{ fontWeight: 700 }}>
                    {formatTime(item.captured_at)}
                  </div>
                  <div style={{ marginTop: 4 }}>
                    <AnchorBadge anchor={item.anchor} />
                  </div>
                </div>
                <span className="inbox-row-cta">
                  Pubblica <ArrowRight size={13} />
                </span>
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
