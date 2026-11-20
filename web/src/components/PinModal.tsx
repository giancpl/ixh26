import { Bitcoin, Hourglass, Share2 } from "./PixelIcon";
import { useState, type FormEvent } from "react";
import { npubEncode } from "nostr-tools/nip19";
import type { AnchorInfo, Board, PhotoPin, PinComment } from "../services/contracts";
import { initials, shortNpub } from "../services/identity";
import { AnchorBadge } from "./PinCard";
import {
  Dialog,
  Feedback,
  formatTime,
  sha256File,
  useOperation,
} from "./primitives";

export default function PinModal({
  pin,
  anchor,
  authorName,
  boards,
  comments,
  likeCount,
  liked,
  onClose,
  onLike,
  onSaveToBoard,
  onAddComment,
  onStampAnchor,
  onUpgradeAnchor,
  onFilterAuthor,
  notify,
}: {
  pin: PhotoPin;
  anchor?: AnchorInfo;
  authorName: string;
  boards: Board[];
  comments: PinComment[];
  likeCount: number;
  liked: boolean;
  onClose: () => void;
  onLike: () => Promise<void>;
  onSaveToBoard: (boardTitle: string) => Promise<void>;
  onAddComment: (text: string) => Promise<void>;
  onStampAnchor: () => Promise<void>;
  onUpgradeAnchor: () => Promise<void>;
  onFilterAuthor: (pubkey: string) => void;
  notify: (msg: string) => void;
}) {
  const [boardName, setBoardName] = useState(
    boards[0]?.title || pin.board || "Preferiti",
  );
  const [commentText, setCommentText] = useState("");
  const [verifyMsg, setVerifyMsg] = useState<{ ok: boolean; text: string }>();
  const task = useOperation();

  async function checkLocalFile(file: File | undefined) {
    if (!file) return;
    const digest = await sha256File(file);
    if (digest === pin.sha256) {
      setVerifyMsg({
        ok: true,
        text: `Integrità verificata: SHA-256 identico (${digest.slice(0, 16)}…).`,
      });
    } else {
      setVerifyMsg({
        ok: false,
        text: `File diverso! Atteso ${pin.sha256.slice(0, 12)}…, trovato ${digest.slice(0, 12)}….`,
      });
    }
  }

  return (
    <Dialog title={pin.title} close={onClose}>
      <Feedback message={task.error} dismiss={task.clearError} />
      <div className="pin-detail-grid">
        <div>
          <div className="pin-detail-photo">
            <img src={pin.imageUrl} alt={pin.title} />
          </div>
          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              gap: 8,
              marginTop: 12,
              alignItems: "center",
            }}
          >
            <span className="badge">
              {pin.source === "android-photoanchor"
                ? "Scattata con PhotoAnchor Android"
                : pin.source === "web-camera"
                  ? "Fotocamera Web"
                  : "Caricata"}
            </span>
            {pin.width && pin.height && (
              <span className="badge">
                {pin.width} × {pin.height}px
              </span>
            )}
            <a
              href={pin.imageUrl}
              download={`${pin.sha256.slice(0, 12)}.jpg`}
              className="badge"
              style={{ textDecoration: "none" }}
            >
              Scarica foto originale
            </a>
          </div>
        </div>

        <div>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 10,
              flexWrap: "wrap",
              marginBottom: 14,
            }}
          >
            <button
              type="button"
              className="btn-ghost"
              style={{ paddingLeft: 4 }}
              onClick={() => {
                onClose();
                onFilterAuthor(pin.pubkey);
              }}
            >
              <span className="avatar-circle">{initials(authorName)}</span>
              <div style={{ textAlign: "left" }}>
                <div style={{ fontWeight: 700, color: "var(--ink)" }}>
                  {authorName}
                </div>
                <div className="mono" style={{ fontSize: 10.5 }}>
                  {shortNpub(pin.pubkey)} · {formatTime(pin.createdAt)}
                </div>
              </div>
            </button>

            <div style={{ display: "flex", gap: 8 }}>
              <button
                type="button"
                onClick={() => void task.run(onLike)}
                disabled={task.busy}
              >
                {liked ? "♥" : "♡"} {likeCount}
              </button>
              <button
                type="button"
                onClick={() => {
                  void navigator.clipboard?.writeText(pin.sha256);
                  notify("SHA-256 copiato.");
                }}
              >
                <Share2 size={14} /> Hash
              </button>
            </div>
          </div>

          {pin.description && (
            <p style={{ marginBottom: 12, whiteSpace: "pre-wrap" }}>
              {pin.description}
            </p>
          )}

          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              gap: 6,
              marginBottom: 14,
            }}
          >
            <span className="badge solid">Bacheca: {pin.board}</span>
            {pin.tags.map((t) => (
              <span key={t} className="badge">
                #{t}
              </span>
            ))}
          </div>

          <form
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              void task.run(async () => {
                await onSaveToBoard(boardName);
                notify(`Salvata nella bacheca "${boardName}".`);
              });
            }}
            style={{
              display: "flex",
              gap: 8,
              alignItems: "center",
              marginBottom: 14,
            }}
          >
            <input
              value={boardName}
              onChange={(e) => setBoardName(e.target.value)}
              list="board-suggestions"
              placeholder="Nome bacheca..."
              aria-label="Bacheca di destinazione"
            />
            <datalist id="board-suggestions">
              {boards.map((b) => (
                <option key={b.id} value={b.title} />
              ))}
              <option value="Preferiti" />
              <option value="PhotoAnchor" />
            </datalist>
            <button
              className="btn-primary"
              disabled={task.busy}
              style={{ flexShrink: 0 }}
            >
              Salva in bacheca
            </button>
          </form>

          {/* Bitcoin OpenTimestamps Anchor Verification Card */}
          <div className="anchor-card">
            <h4>
              <span>
                <Bitcoin size={16} /> Ancoraggio Bitcoin (OpenTimestamps)
              </span>
              <AnchorBadge anchor={anchor} />
            </h4>
            <div className="mono hash-box">SHA-256: {pin.sha256}</div>

            {anchor?.block_height && (
              <p style={{ fontSize: 12, marginBottom: 8, color: "var(--ok)" }}>
                Blocco Bitcoin #{anchor.block_height}
                {anchor.block_time
                  ? ` · ${formatTime(anchor.block_time)}`
                  : ""}
              </p>
            )}

            {anchor?.diagnostics?.map((d, idx) => (
              <p
                key={idx}
                className="field-hint"
                style={{ marginBottom: 8 }}
              >
                {d.message}
              </p>
            ))}

            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {(!anchor ||
                anchor.status === "UNANCHORED" ||
                anchor.status === "ERROR") && (
                <button
                  type="button"
                  className="btn-primary"
                  disabled={task.busy}
                  onClick={() =>
                    void task.run(async () => {
                      await onStampAnchor();
                      notify("Richiesta di ancoraggio OpenTimestamps inviata.");
                    })
                  }
                >
                  <Hourglass size={14} /> Ancora ora su Bitcoin
                </button>
              )}

              {anchor?.proof_url && (
                <>
                  <button
                    type="button"
                    disabled={task.busy}
                    onClick={() =>
                      void task.run(async () => {
                        await onUpgradeAnchor();
                        notify("Verifica OpenTimestamps aggiornata.");
                      })
                    }
                  >
                    Aggiorna &amp; Verifica
                  </button>
                  <a
                    href={anchor.proof_url}
                    download={`${pin.sha256.slice(0, 16)}.ots`}
                    style={{ textDecoration: "none" }}
                  >
                    <button type="button">Scarica prova .ots</button>
                  </a>
                </>
              )}
            </div>

            <div style={{ marginTop: 12, paddingTop: 10, borderTop: "1px solid var(--bd)" }}>
              <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 6 }}>
                Verifica un file dal tuo dispositivo contro questo hash:
              </label>
              <input
                type="file"
                accept="image/*"
                onChange={(e) => void checkLocalFile(e.target.files?.[0])}
              />
              {verifyMsg && (
                <p
                  style={{
                    marginTop: 6,
                    fontSize: 12,
                    fontWeight: 700,
                    color: verifyMsg.ok ? "var(--ok)" : "var(--rd)",
                  }}
                >
                  {verifyMsg.text}
                </p>
              )}
            </div>
          </div>

          <details style={{ marginBottom: 14 }}>
            <summary
              style={{
                cursor: "pointer",
                fontSize: 12.5,
                fontWeight: 600,
                color: "var(--muted-ink)",
              }}
            >
              Firma crittografica Nostr (NIP-01)
            </summary>
            <div className="hash-box mono" style={{ marginTop: 8 }}>
              <div>event.id: {pin.id}</div>
              <div>npub: {npubEncode(pin.pubkey)}</div>
              <div>sig: {pin.event.sig}</div>
            </div>
          </details>

          {/* Comments */}
          <h4 style={{ fontSize: 14, marginBottom: 6 }}>
            Commenti Nostr ({comments.length})
          </h4>
          <div className="comment-list">
            {comments.length === 0 && (
              <p className="field-hint">
                Nessun commento ancora. Scrivi il primo messaggio pubblico su questa foto.
              </p>
            )}
            {comments.map((c) => (
              <div key={c.id} className="comment-item">
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    fontSize: 11,
                    color: "var(--muted-ink)",
                    marginBottom: 3,
                  }}
                >
                  <strong>{shortNpub(c.pubkey)}</strong>
                  <span>{formatTime(c.createdAt)}</span>
                </div>
                <div>{c.content}</div>
              </div>
            ))}
          </div>

          <form
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              if (!commentText.trim()) return;
              void task.run(async () => {
                await onAddComment(commentText);
                setCommentText("");
              });
            }}
            style={{ display: "flex", gap: 8 }}
          >
            <input
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="Aggiungi un commento pubblico..."
              maxLength={1000}
            />
            <button className="btn-primary" disabled={task.busy || !commentText.trim()}>
              Invia
            </button>
          </form>
        </div>
      </div>
    </Dialog>
  );
}
