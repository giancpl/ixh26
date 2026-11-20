import { useEffect, useState, type FormEvent } from "react";
import type { MessagingPort } from "../services/contracts";
import { contactName, initials, shortNpub } from "../services/identity";
import { LockKeyhole, MessageSquare, Share2 } from "./PixelIcon";
import { Feedback, Field, formatTime, useOperation } from "./primitives";

export default function MessagesView({
  messenger,
  initialPeerHint,
  onShareProfile,
}: {
  messenger?: MessagingPort;
  initialPeerHint?: { id: number; peer: string };
  onShareProfile: () => void;
}) {
  const [peer, setPeer] = useState("");
  const [newIdentity, setNewIdentity] = useState("");
  const [newAlias, setNewAlias] = useState("");
  const [text, setText] = useState("");
  const [showAdd, setShowAdd] = useState(false);
  const task = useOperation();

  useEffect(() => {
    if (initialPeerHint?.peer) {
      setPeer(initialPeerHint.peer);
      setShowAdd(false);
    }
  }, [initialPeerHint?.id, initialPeerHint?.peer]);

  if (!messenger) {
    return (
      <div className="surf" style={{ padding: 32, textAlign: "center" }}>
        <LockKeyhole size={28} />
        <h3 style={{ marginTop: 10 }}>Messaggi privati NIP-17</h3>
        <p className="field-hint" style={{ marginTop: 6 }}>
          I messaggi privati cifrati end-to-end (NIP-17/44/59) richiedono il login
          tramite Vault locale o chiave nsec (non sono disponibili con sola estensione NIP-07).
        </p>
      </div>
    );
  }

  const chat = messenger.state;
  const messages = chat.messages.filter((m) => m.peer === peer);
  const selectedContact = chat.contacts.find((c) => c.pubkey === peer);

  async function addContact(e: FormEvent) {
    e.preventDefault();
    await task.run(async () => {
      const pk = await messenger!.addContact(newIdentity, newAlias);
      setPeer(pk);
      setNewIdentity("");
      setNewAlias("");
      setShowAdd(false);
    });
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!peer || !text.trim()) return;
    const msg = text;
    await task.run(async () => {
      await messenger!.send(peer, msg);
      setText("");
    });
  }

  return (
    <div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 14,
          flexWrap: "wrap",
          gap: 10,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <LockKeyhole size={18} />
          <span className="field-hint">
            Chat cifrate end-to-end Nostr (NIP-17 / NIP-44 / NIP-59) · Stato:{" "}
            <strong>{chat.status}</strong>
          </span>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button type="button" onClick={onShareProfile}>
            <Share2 size={14} /> Condividi mio QR
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={() => setShowAdd(!showAdd)}
          >
            + Nuovo contatto
          </button>
        </div>
      </div>

      <Feedback message={task.error} dismiss={task.clearError} />

      {showAdd && (
        <form
          onSubmit={(e) => void addContact(e)}
          className="surf"
          style={{ padding: 18, marginBottom: 16 }}
        >
          <h3 style={{ marginBottom: 10 }}>Aggiungi contatto Nostr</h3>
          <div className="two-cols">
            <Field label="npub, nprofile o pubkey hex">
              <input
                value={newIdentity}
                onChange={(e) => setNewIdentity(e.target.value)}
                placeholder="npub1..."
                required
              />
            </Field>
            <Field label="Nickname locale (opzionale)">
              <input
                value={newAlias}
                onChange={(e) => setNewAlias(e.target.value)}
                placeholder="Es. Giulia"
              />
            </Field>
          </div>
          <button className="btn-primary" disabled={task.busy}>
            Salva e apri chat
          </button>
        </form>
      )}

      <div className="surf chat-layout">
        <div className="chat-contacts">
          {chat.contacts.length === 0 ? (
            <p className="field-hint" style={{ padding: 12 }}>
              Nessun contatto ancora. Aggiungi un npub per iniziare una chat privata.
            </p>
          ) : (
            chat.contacts.map((c) => (
              <button
                key={c.pubkey}
                type="button"
                className={`nav-btn ${peer === c.pubkey ? "active" : ""}`}
                onClick={() => setPeer(c.pubkey)}
              >
                <span className="avatar-circle">{initials(contactName(c))}</span>
                <div style={{ textAlign: "left", minWidth: 0 }}>
                  <div style={{ fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis" }}>
                    {contactName(c)}
                  </div>
                  <div className="mono" style={{ fontSize: 10 }}>
                    {shortNpub(c.pubkey)}
                  </div>
                </div>
              </button>
            ))
          )}
        </div>

        <div className="chat-thread">
          {!peer ? (
            <div
              style={{
                flex: 1,
                display: "grid",
                placeItems: "center",
                padding: 32,
                textAlign: "center",
              }}
            >
              <div>
                <MessageSquare size={28} />
                <h3 style={{ marginTop: 10 }}>Seleziona una conversazione</h3>
                <p className="field-hint">
                  Scambia foto, hash SHA-256 e messaggi privati con i tuoi contatti Nostr.
                </p>
              </div>
            </div>
          ) : (
            <>
              <div
                style={{
                  padding: "12px 18px",
                  borderBottom: "1px solid var(--bd)",
                  fontWeight: 700,
                }}
              >
                {selectedContact ? contactName(selectedContact) : shortNpub(peer)}
              </div>
              <div className="chat-messages">
                {messages.map((m) => (
                  <div
                    key={m.id}
                    className={`bubble ${m.author !== peer ? "own" : ""}`}
                  >
                    <div style={{ whiteSpace: "pre-wrap" }}>{m.content}</div>
                    <div style={{ fontSize: 10, opacity: 0.75, marginTop: 4 }}>
                      {formatTime(m.timestamp)} · {m.status}
                    </div>
                  </div>
                ))}
              </div>
              <form
                onSubmit={(e) => void send(e)}
                style={{
                  display: "flex",
                  gap: 8,
                  padding: 12,
                  borderTop: "1px solid var(--bd)",
                }}
              >
                <input
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  placeholder="Scrivi un messaggio cifrato…"
                  maxLength={8000}
                />
                <button className="btn-primary" disabled={task.busy || !text.trim()}>
                  Invia
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
