import { useState, type FormEvent } from "react";
import { npubEncode } from "nostr-tools/nip19";
import type {
  AnchorInfo,
  Board,
  CameraInboxItem,
  NostrSigner,
  PhotoPin,
  PublicProfile,
} from "../services/contracts";
import { initials, shortNpub } from "../services/identity";
import { Bitcoin, MessageSquare, Share2, UserRound } from "./PixelIcon";
import PinCard from "./PinCard";
import { Feedback, formatTime, useOperation } from "./primitives";

type ProfileTab = "pins" | "boards" | "inbox";

export default function ProfileView({
  signer,
  viewingPubkey,
  profile,
  pins,
  boards,
  anchors,
  inbox,
  followersCount,
  followingCount,
  isFollowing,
  likeCountOf,
  isLiked,
  isSaved,
  authorNameOf,
  onOpenPin,
  onSavePin,
  onLikePin,
  onToggleFollow,
  onMessageUser,
  onOpenShare,
  onOpenCamera,
  onOpenSettings,
  onCreateBoard,
  onFilterAuthor,
  onBackToMe,
  notify,
}: {
  signer: NostrSigner;
  viewingPubkey: string;
  profile?: PublicProfile;
  pins: PhotoPin[];
  boards: Board[];
  anchors: Map<string, AnchorInfo>;
  inbox: CameraInboxItem[];
  followersCount: number;
  followingCount: number;
  isFollowing: boolean;
  likeCountOf: (id: string) => number;
  isLiked: (id: string) => boolean;
  isSaved: (id: string) => boolean;
  authorNameOf: (pubkey: string) => string;
  onOpenPin: (pin: PhotoPin) => void;
  onSavePin: (pin: PhotoPin) => void;
  onLikePin: (pin: PhotoPin) => void;
  onToggleFollow: () => Promise<void>;
  onMessageUser: (pubkey: string) => void;
  onOpenShare: () => void;
  onOpenCamera: () => void;
  onOpenSettings: () => void;
  onCreateBoard: (title: string) => Promise<void>;
  onFilterAuthor: (pubkey: string) => void;
  onBackToMe: () => void;
  notify: (msg: string) => void;
}) {
  const isOwn = viewingPubkey === signer.pubkey;
  const [subTab, setSubTab] = useState<ProfileTab>("pins");
  const [selectedBoardId, setSelectedBoardId] = useState<string | null>(null);
  const [newBoardName, setNewBoardName] = useState("");
  const task = useOperation();

  const userPins = pins.filter((p) => p.pubkey === viewingPubkey);
  const userBoards = boards.filter((b) => b.pubkey === viewingPubkey);
  const pinMap = new Map(pins.map((p) => [p.id, p]));
  const selectedBoard = userBoards.find((b) => b.id === selectedBoardId);
  const confirmedCount = userPins.filter(
    (p) => anchors.get(p.sha256)?.status === "CONFIRMED",
  ).length;
  const displayName = profile?.name || authorNameOf(viewingPubkey);

  async function handleCreateBoard(e: FormEvent) {
    e.preventDefault();
    if (!newBoardName.trim()) return;
    await task.run(async () => {
      await onCreateBoard(newBoardName.trim());
      setNewBoardName("");
      notify("Bacheca creata su Nostr!");
    });
  }

  return (
    <div>
      {!isOwn && (
        <div style={{ marginBottom: 14 }}>
          <button type="button" onClick={onBackToMe}>
            ← Torna al mio profilo
          </button>
        </div>
      )}

      <section className="surf profile-hero">
        <div className="avatar-circle avatar-lg">{initials(displayName)}</div>
        <div className="profile-hero-info">
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <h1>{displayName}</h1>
            <span className="badge">
              <UserRound size={12} /> {isOwn ? "Il tuo profilo Nostr" : "Utente Nostr"}
            </span>
          </div>
          {profile?.about && (
            <p style={{ marginTop: 6, color: "var(--muted-ink)" }}>{profile.about}</p>
          )}
          <div style={{ marginTop: 10, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <button
              type="button"
              className="chip-btn mono"
              onClick={() => {
                void navigator.clipboard?.writeText(npubEncode(viewingPubkey));
                notify("npub copiato.");
              }}
            >
              {shortNpub(viewingPubkey)}
            </button>
            {isOwn && (
              <>
                <button type="button" className="chip-btn" onClick={onOpenShare}>
                  <Share2 size={13} /> Condividi QR
                </button>
                <button type="button" className="chip-btn" onClick={onOpenSettings}>
                  Modifica profilo
                </button>
              </>
            )}
          </div>
          <div className="profile-stats">
            <span>
              <strong>{userPins.length}</strong> Foto
            </span>
            <span>
              <strong>{userBoards.length}</strong> Bacheche
            </span>
            <span>
              <strong>{followersCount}</strong> Follower
            </span>
            <span>
              <strong>{followingCount}</strong> Seguiti
            </span>
            <span>
              <strong>{confirmedCount}</strong> Confermati BTC
            </span>
          </div>
        </div>

        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          {isOwn ? (
            <button type="button" className="btn-primary" onClick={onOpenCamera}>
              + Scatta / Carica foto
            </button>
          ) : (
            <>
              <button
                type="button"
                className={isFollowing ? "" : "btn-primary"}
                disabled={task.busy}
                onClick={() => void task.run(onToggleFollow)}
              >
                {isFollowing ? "Segui già ✓" : "+ Segui su Nostr"}
              </button>
              <button type="button" onClick={() => onMessageUser(viewingPubkey)}>
                <MessageSquare size={15} /> Messaggio
              </button>
            </>
          )}
        </div>
      </section>

      <div className="segmented" style={{ maxWidth: 520 }}>
        <button
          type="button"
          className={subTab === "pins" ? "selected" : ""}
          onClick={() => {
            setSubTab("pins");
            setSelectedBoardId(null);
          }}
        >
          {isOwn ? "Le mie foto" : "Foto"} ({userPins.length})
        </button>
        <button
          type="button"
          className={subTab === "boards" ? "selected" : ""}
          onClick={() => setSubTab("boards")}
        >
          Bacheche ({userBoards.length})
        </button>
        {isOwn && (
          <button
            type="button"
            className={subTab === "inbox" ? "selected" : ""}
            onClick={() => setSubTab("inbox")}
          >
            Dalla Fotocamera ({inbox.length})
          </button>
        )}
      </div>

      <Feedback message={task.error} dismiss={task.clearError} />

      {subTab === "pins" && (
        <>
          {userPins.length === 0 ? (
            <div className="surf" style={{ padding: 36, textAlign: "center" }}>
              <h3>Nessuna foto pubblicata ancora</h3>
              <p className="field-hint" style={{ margin: "8px 0 16px" }}>
                {isOwn
                  ? "Scatta una foto con l'app Android PhotoAnchor o dalla Fotocamera per pubblicare il tuo primo Pin ancorato su Bitcoin."
                  : "Questo utente non ha ancora pubblicato foto su questi relay Nostr."}
              </p>
              {isOwn && (
                <button type="button" className="btn-primary" onClick={onOpenCamera}>
                  Apri Modulo Fotocamera
                </button>
              )}
            </div>
          ) : (
            <div className="masonry">
              {userPins.map((pin) => (
                <PinCard
                  key={pin.id}
                  pin={pin}
                  anchor={anchors.get(pin.sha256)}
                  authorName={authorNameOf(pin.pubkey)}
                  likeCount={likeCountOf(pin.id)}
                  liked={isLiked(pin.id)}
                  saved={isSaved(pin.id)}
                  onOpen={() => onOpenPin(pin)}
                  onSave={() => onSavePin(pin)}
                  onLike={() => onLikePin(pin)}
                  onFilterAuthor={() => onFilterAuthor(pin.pubkey)}
                />
              ))}
            </div>
          )}
        </>
      )}

      {subTab === "boards" && !selectedBoard && (
        <div>
          {isOwn && (
            <form
              onSubmit={(e) => void handleCreateBoard(e)}
              style={{
                display: "flex",
                gap: 10,
                maxWidth: 460,
                marginBottom: 20,
              }}
            >
              <input
                value={newBoardName}
                onChange={(e) => setNewBoardName(e.target.value)}
                placeholder="Nuova bacheca (es. Torino, Street, Bitcoin)..."
                maxLength={50}
              />
              <button className="btn-primary" disabled={task.busy || !newBoardName.trim()}>
                Crea bacheca
              </button>
            </form>
          )}

          {userBoards.length === 0 ? (
            <div className="surf" style={{ padding: 32, textAlign: "center" }}>
              <h3>Nessuna bacheca ancora</h3>
              <p className="field-hint" style={{ marginTop: 6 }}>
                Salva qualsiasi Pin o creane una sopra per raccogliere le foto per tema.
              </p>
            </div>
          ) : (
            <div className="boards-grid">
              {userBoards.map((board) => {
                const boardPins = board.pinIds
                  .map((id) => pinMap.get(id))
                  .filter((p): p is PhotoPin => Boolean(p));
                const thumbs = boardPins.slice(0, 3);
                return (
                  <button
                    key={board.id}
                    type="button"
                    className="surf board-card"
                    onClick={() => setSelectedBoardId(board.id)}
                  >
                    <div className="board-collage">
                      <div className="slot-main">
                        {thumbs[0] ? (
                          <img src={thumbs[0].imageUrl} alt={board.title} />
                        ) : (
                          <div className="slot-empty" style={{ height: "100%" }}>
                            Vuota
                          </div>
                        )}
                      </div>
                      <div>
                        {thumbs[1] ? (
                          <img src={thumbs[1].imageUrl} alt="" />
                        ) : (
                          <div className="slot-empty" style={{ height: "100%" }} />
                        )}
                      </div>
                      <div>
                        {thumbs[2] ? (
                          <img src={thumbs[2].imageUrl} alt="" />
                        ) : (
                          <div className="slot-empty" style={{ height: "100%" }} />
                        )}
                      </div>
                    </div>
                    <div className="board-info">
                      <h3>{board.title}</h3>
                      <small>{boardPins.length} Pin salvati</small>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      )}

      {subTab === "boards" && selectedBoard && (
        <div>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: 18,
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <button type="button" onClick={() => setSelectedBoardId(null)}>
                ← Tutte le bacheche
              </button>
              <h2>{selectedBoard.title}</h2>
            </div>
          </div>
          <div className="masonry">
            {selectedBoard.pinIds
              .map((id) => pinMap.get(id))
              .filter((p): p is PhotoPin => Boolean(p))
              .map((pin) => (
                <PinCard
                  key={pin.id}
                  pin={pin}
                  anchor={anchors.get(pin.sha256)}
                  authorName={authorNameOf(pin.pubkey)}
                  likeCount={likeCountOf(pin.id)}
                  liked={isLiked(pin.id)}
                  saved={isSaved(pin.id)}
                  onOpen={() => onOpenPin(pin)}
                  onSave={() => onSavePin(pin)}
                  onLike={() => onLikePin(pin)}
                  onFilterAuthor={() => onFilterAuthor(pin.pubkey)}
                />
              ))}
          </div>
        </div>
      )}

      {subTab === "inbox" && isOwn && (
        <div className="surf" style={{ padding: 22 }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: 14,
            }}
          >
            <div>
              <h3>Foto arrivate dal modulo fotocamera Android</h3>
              <p className="field-hint">
                Queste foto sono già state salvate e sottoposte ad anchoring su Bitcoin.
                Apri il Modulo Fotocamera per aggiungere titolo/bacheca e pubblicarle su Nostr.
              </p>
            </div>
            <button type="button" className="btn-primary" onClick={onOpenCamera}>
              Pubblica dalla coda ({inbox.length})
            </button>
          </div>
          {inbox.length === 0 ? (
            <p className="field-hint">
              Nessuna foto in coda. Collega la tua app Android PhotoAnchor dalla sezione Fotocamera!
            </p>
          ) : (
            <div className="inbox-list">
              {inbox.map((item) => (
                <div key={item.sha256} className="inbox-row">
                  <img src={item.url} alt="" />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="mono">{item.sha256}</div>
                    <div className="field-hint">
                      <Bitcoin size={12} /> Stato OTS: {item.anchor.status} ·{" "}
                      {formatTime(item.captured_at)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
