import { useState } from "react";
import type { NostrUserSummary } from "../services/contracts";
import { initials, shortNpub } from "../services/identity";
import { MessageSquare, Star, UserRound } from "./PixelIcon";

export default function UsersView({
  myPubkey,
  users,
  isFollowing,
  onToggleFollow,
  onOpenProfile,
  onMessageUser,
}: {
  myPubkey: string;
  users: NostrUserSummary[];
  isFollowing: (pubkey: string) => boolean;
  onToggleFollow: (pubkey: string) => Promise<void>;
  onOpenProfile: (pubkey: string) => void;
  onMessageUser: (pubkey: string) => void;
}) {
  const [filter, setFilter] = useState<"all" | "following">("all");
  const [query, setQuery] = useState("");
  const [busyPk, setBusyPk] = useState<string | null>(null);

  const visible = users.filter((u) => {
    if (filter === "following" && !isFollowing(u.pubkey)) return false;
    if (!query.trim()) return true;
    const q = query.trim().toLowerCase();
    return (
      (u.profile?.name || "").toLowerCase().includes(q) ||
      (u.profile?.about || "").toLowerCase().includes(q) ||
      u.pubkey.includes(q) ||
      shortNpub(u.pubkey).toLowerCase().includes(q)
    );
  });

  return (
    <div>
      <div className="section-header-bar">
        <div>
          <h2>Community</h2>
          <p className="field-hint">
            Scopri e segui gli altri fotografi su BitPin.
          </p>
        </div>

        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <button
            type="button"
            className={`chip-btn ${filter === "all" ? "active" : ""}`}
            onClick={() => setFilter("all")}
          >
            Tutti ({users.length})
          </button>
          <button
            type="button"
            className={`chip-btn ${filter === "following" ? "active" : ""}`}
            onClick={() => setFilter("following")}
          >
            Seguiti
          </button>
        </div>
      </div>

      <div style={{ maxWidth: 460, margin: "0 auto 18px" }}>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Cerca utente per nome o npub..."
        />
      </div>

      {visible.length === 0 ? (
        <div className="surf" style={{ padding: 36, textAlign: "center" }}>
          <UserRound size={28} />
          <h3 style={{ marginTop: 10 }}>Nessun utente trovato</h3>
          <p className="field-hint" style={{ marginTop: 6 }}>
            Appena altri utenti pubblicano il profilo o scattano foto sui relay
            Nostr, appariranno qui.
          </p>
        </div>
      ) : (
        <div className="boards-grid">
          {visible.map((u) => {
            const name = u.profile?.name || shortNpub(u.pubkey);
            const isMe = u.pubkey === myPubkey;
            const following = isFollowing(u.pubkey);
            return (
              <article
                key={u.pubkey}
                className="surf"
                style={{
                  padding: 18,
                  display: "flex",
                  flexDirection: "column",
                  gap: 12,
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div className="avatar-circle" style={{ width: 46, height: 46, fontSize: 16 }}>
                    {initials(name)}
                  </div>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div
                      style={{
                        fontWeight: 700,
                        fontSize: 15,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {name} {isMe && <span className="badge">Tu</span>}
                    </div>
                    <div className="mono" style={{ fontSize: 10.5, color: "var(--muted-ink)" }}>
                      {shortNpub(u.pubkey)}
                    </div>
                  </div>
                </div>

                {u.latestPin && (
                  <div
                    onClick={() => onOpenProfile(u.pubkey)}
                    style={{
                      height: 120,
                      borderRadius: 12,
                      overflow: "hidden",
                      cursor: "pointer",
                      background: "var(--soft)",
                    }}
                  >
                    <img
                      src={u.latestPin.imageUrl}
                      alt={u.latestPin.title}
                      style={{ width: "100%", height: "100%", objectFit: "cover" }}
                    />
                  </div>
                )}

                {u.profile?.about && (
                  <p className="field-hint" style={{ margin: 0 }}>
                    {u.profile.about}
                  </p>
                )}

                <div style={{ display: "flex", gap: 14, fontSize: 12, color: "var(--muted-ink)" }}>
                  <span>
                    <strong style={{ color: "var(--fg)" }}>{u.pinCount}</strong> foto
                  </span>
                  <span>
                    <strong style={{ color: "var(--fg)" }}>{u.boardCount}</strong> bacheche
                  </span>
                  <span>
                    <strong style={{ color: "var(--fg)" }}>{u.followersCount}</strong> follower
                  </span>
                </div>

                <div style={{ display: "flex", gap: 8, marginTop: "auto" }}>
                  <button
                    type="button"
                    className="btn-primary wide"
                    onClick={() => onOpenProfile(u.pubkey)}
                  >
                    Vedi profilo
                  </button>
                  {!isMe && (
                    <>
                      <button
                        type="button"
                        disabled={busyPk === u.pubkey}
                        onClick={() => {
                          setBusyPk(u.pubkey);
                          void onToggleFollow(u.pubkey).finally(() => setBusyPk(null));
                        }}
                      >
                        {following ? (
                          <>
                            <Star size={13} /> Seguito
                          </>
                        ) : (
                          "Segui"
                        )}
                      </button>
                      <button
                        type="button"
                        title="Messaggio privato NIP-17"
                        onClick={() => onMessageUser(u.pubkey)}
                      >
                        <MessageSquare size={15} />
                      </button>
                    </>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
