import { useCallback, useEffect, useRef, useState } from "react";
import { BrowserStorage } from "./services/storage";
import { VaultService } from "./services/vault";
import { MessagingService } from "./services/messaging";
import { SocialService } from "./services/social";
import { defaultRelayUrl } from "./services/relay";
import { initials } from "./services/identity";
import type { CameraInboxItem, NostrSigner } from "./services/contracts";
import AuthView from "./components/AuthView";
import PinCard from "./components/PinCard";
import PinModal from "./components/PinModal";
import CameraModulePanel from "./components/CameraModulePanel";
import ProfileView from "./components/ProfileView";
import UsersView from "./components/UsersView";
import MessagesView from "./components/MessagesView";
import SettingsView from "./components/SettingsView";
import ShareProfileModal from "./components/ShareProfileModal";
import {
  Bitcoin,
  Globe,
  LockKeyhole,
  MessageSquare,
  UserRound,
} from "./components/PixelIcon";
import { Feedback, ThemeToggle } from "./components/primitives";

type Area = "Explore" | "Users" | "Profile" | "Camera" | "Messages" | "Settings";

type ActiveSession = {
  signer: NostrSigner;
  social: SocialService;
  messenger: MessagingService;
};

export default function App() {
  const [vault] = useState(() => new VaultService(new BrowserStorage()));
  const [exists, setExists] = useState<boolean>();
  const [session, setSession] = useState<ActiveSession>();
  const [area, setArea] = useState<Area>("Explore");
  const [search, setSearch] = useState("");
  const [filterMode, setFilterMode] = useState<
    "all" | "following" | "camera" | "btc" | "pending"
  >("all");
  const [viewingPubkey, setViewingPubkey] = useState<string>("");
  const [selectedPinId, setSelectedPinId] = useState<string | null>(null);
  const [sharing, setSharing] = useState(false);
  const [inbox, setInbox] = useState<CameraInboxItem[]>([]);
  const [chatPeerHint, setChatPeerHint] = useState<{ id: number; peer: string }>();
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [, tick] = useState(0);
  const currentRef = useRef<ActiveSession | undefined>(undefined);
  const seqRef = useRef(0);

  useEffect(() => {
    void vault
      .exists()
      .then((b) => setExists(Boolean(b)))
      .catch(() =>
        setError("Impossibile accedere a IndexedDB in questo browser."),
      );
    return () => {
      currentRef.current?.social.disconnect();
      currentRef.current?.messenger.disconnect();
      vault.lock();
    };
  }, [vault]);

  useEffect(() => {
    if (!notice) return;
    const t = setTimeout(() => setNotice(""), 4500);
    return () => clearTimeout(t);
  }, [notice]);

  const refreshInbox = useCallback(async () => {
    const active = currentRef.current;
    if (!active) return;
    try {
      const items = await active.social.fetchCameraInbox();
      if (currentRef.current === active) setInbox(items);
    } catch {
      /* ignore polling error */
    }
  }, []);

  function startSession(initialProfile?: { name: string; about: string }) {
    currentRef.current?.social.disconnect();
    currentRef.current?.messenger.disconnect();

    const signer = vault.asSigner();
    const social = new SocialService(signer);
    const messenger = new MessagingService(vault);
    const next: ActiveSession = { signer, social, messenger };
    currentRef.current = next;

    social.subscribe(() => {
      if (currentRef.current === next) tick((n) => n + 1);
    });
    messenger.subscribe(() => {
      if (currentRef.current === next) tick((n) => n + 1);
    });

    setSession(next);
    setViewingPubkey(signer.pubkey);
    setArea("Explore");
    setError("");

    const relays = [defaultRelayUrl()];
    void social.connect(relays).then(async () => {
      if (initialProfile?.name && currentRef.current === next) {
        try {
          await social.publishProfile(initialProfile.name, initialProfile.about);
        } catch {
          /* profile can be published later */
        }
      }
      await refreshInbox();
    });

    void messenger.connect().catch(() => {});
  }

  function lockSession() {
    const prev = currentRef.current;
    currentRef.current = undefined;
    prev?.social.disconnect();
    prev?.messenger.disconnect();
    vault.lock();
    setSession(undefined);
    setInbox([]);
    setSelectedPinId(null);
    setSharing(false);
  }

  async function exportBackup() {
    const raw = await vault.exportBackup();
    const url = URL.createObjectURL(new Blob([raw], { type: "application/json" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "bitpin-nostr-backup.json";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  if (!session) {
    if (exists === undefined) {
      return (
        <div style={{ padding: 64, textAlign: "center" }}>
          Caricamento identità Nostr…
        </div>
      );
    }
    return (
      <AuthView
        vault={vault}
        exists={exists}
        onStored={() => setExists(true)}
        onVaultOpened={(initialProfile) => {
          setExists(true);
          startSession(initialProfile);
        }}
      />
    );
  }

  const { signer, social, messenger } = session;
  const { pins, anchors, reactions, comments, boards, profiles } = social.state;
  const myBoards = social.myBoards();
  const allSavedIds = new Set(myBoards.flatMap((b) => b.pinIds));
  const followingSet = new Set(social.myFollowing());

  const authorNameOf = (pk: string) => {
    const pub = profiles.get(pk);
    if (pub?.name) return pub.name;
    if (pk === signer.pubkey && messenger.state.profile.draft.name) {
      return messenger.state.profile.draft.name;
    }
    return social.authorLabel(pk);
  };

  const myName = authorNameOf(signer.pubkey);

  const filteredPins = pins.filter((pin) => {
    const anchor = anchors.get(pin.sha256);
    if (filterMode === "following" && !followingSet.has(pin.pubkey))
      return false;
    if (filterMode === "camera" && pin.source !== "android-photoanchor")
      return false;
    if (filterMode === "btc" && anchor?.status !== "CONFIRMED") return false;
    if (filterMode === "pending" && anchor?.status !== "PENDING") return false;
    if (!search.trim()) return true;
    const q = search.trim().toLowerCase();
    return (
      pin.title.toLowerCase().includes(q) ||
      pin.description.toLowerCase().includes(q) ||
      pin.board.toLowerCase().includes(q) ||
      pin.sha256.includes(q) ||
      pin.tags.some((t) => t.toLowerCase().includes(q)) ||
      authorNameOf(pin.pubkey).toLowerCase().includes(q)
    );
  });

  const selectedPin = selectedPinId
    ? pins.find((p) => p.id === selectedPinId)
    : undefined;
  const targetPubkey = viewingPubkey || signer.pubkey;

  return (
    <div className="app-shell">
      <div className="app-body">
        <aside className="sidebar">
          <a
            className="brand-link"
            href="#explore"
            onClick={(e) => {
              e.preventDefault();
              setArea("Explore");
            }}
          >
            <img className="lt" src="/lab-assets/cow-icon-blue.svg" alt="" />
            <img className="dk" src="/lab-assets/cow-icon-white.svg" alt="" />
            <span>
              BitPin
              <span className="brand-sub">PhotoAnchor · IXH26</span>
            </span>
          </a>

          <nav className="sidebar-nav" aria-label="Navigazione principale">
            <button
              type="button"
              className={`nav-btn ${area === "Explore" ? "active" : ""}`}
              onClick={() => setArea("Explore")}
            >
              <Globe size={18} />
              <span>Esplora</span>
            </button>
            <button
              type="button"
              className={`nav-btn ${area === "Users" ? "active" : ""}`}
              onClick={() => setArea("Users")}
            >
              <UserRound size={18} />
              <span>Utenti</span>
            </button>
            <button
              type="button"
              className={`nav-btn ${area === "Profile" ? "active" : ""}`}
              onClick={() => {
                setViewingPubkey(signer.pubkey);
                setArea("Profile");
              }}
            >
              <UserRound size={18} />
              <span>Profilo</span>
            </button>
            <button
              type="button"
              className={`nav-btn ${area === "Camera" ? "active" : ""}`}
              onClick={() => setArea("Camera")}
            >
              <Bitcoin size={18} />
              <span>Fotocamera</span>
              {inbox.length > 0 && (
                <span className="nav-count">{inbox.length}</span>
              )}
            </button>
            <button
              type="button"
              className={`nav-btn ${area === "Messages" ? "active" : ""}`}
              onClick={() => setArea("Messages")}
            >
              <MessageSquare size={18} />
              <span>Messaggi</span>
            </button>
            <button
              type="button"
              className={`nav-btn ${area === "Settings" ? "active" : ""}`}
              onClick={() => setArea("Settings")}
            >
              <LockKeyhole size={18} />
              <span>Impostazioni</span>
            </button>
          </nav>

          <div className="sidebar-footer">
            <div>
              Relay Nostr:{" "}
              <strong
                style={{
                  color:
                    social.state.relayStatus === "connected"
                      ? "var(--ok)"
                      : "var(--muted-ink)",
                }}
              >
                {social.state.relayStatus}
              </strong>
            </div>
            <button type="button" onClick={lockSession} className="wide">
              <LockKeyhole size={14} /> Blocca / Esci
            </button>
          </div>
        </aside>

        <div className="main-area">
          <header className="topbar">
            <a
              className="brand-link mobile-brand"
              href="#explore"
              onClick={(e) => {
                e.preventDefault();
                setArea("Explore");
              }}
            >
              <img className="lt" src="/lab-assets/cow-icon-blue.svg" alt="" />
              <img className="dk" src="/lab-assets/cow-icon-white.svg" alt="" />
              <span>BitPin</span>
            </a>

            <div className="search-wrap">
              <input
                type="search"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  if (area !== "Explore") setArea("Explore");
                }}
                placeholder="Cerca foto, utenti, #tag, bacheche..."
                aria-label="Cerca nel feed"
              />
            </div>

            <div className="topbar-actions">
              <button
                type="button"
                className="btn-primary btn-pill"
                onClick={() => setArea("Camera")}
              >
                + Scatta / Carica
              </button>
              <ThemeToggle />
              <button
                type="button"
                className="avatar-chip"
                onClick={() => {
                  setViewingPubkey(signer.pubkey);
                  setArea("Profile");
                }}
                title="Il mio profilo Nostr"
              >
                <span className="avatar-circle">{initials(myName)}</span>
                <span
                  style={{
                    maxWidth: 110,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {myName}
                </span>
              </button>
            </div>
          </header>

          <main className="content-wrap">
            <Feedback message={error} dismiss={() => setError("")} />
            {notice && (
              <div className="toast-float">
                <Feedback
                  message={notice}
                  kind="success"
                  dismiss={() => setNotice("")}
                />
              </div>
            )}

            {area === "Explore" && (
              <>
                <div className="filter-bar">
                  {(
                    [
                      ["all", `Tutti (${pins.length})`],
                      ["following", `Seguiti (${followingSet.size})`],
                      ["camera", "Da PhotoAnchor Android"],
                      ["btc", "Confermati Bitcoin"],
                      ["pending", "In attesa OTS"],
                    ] as const
                  ).map(([key, label]) => (
                    <button
                      key={key}
                      type="button"
                      className={`chip-btn ${filterMode === key ? "active" : ""}`}
                      onClick={() => setFilterMode(key)}
                    >
                      {label}
                    </button>
                  ))}
                  <button
                    type="button"
                    className="chip-btn"
                    style={{ marginLeft: "auto" }}
                    onClick={() => void social.refreshAllAnchors()}
                  >
                    Aggiorna stato Bitcoin
                  </button>
                </div>

                {filteredPins.length === 0 ? (
                  <div className="surf" style={{ padding: 48, textAlign: "center" }}>
                    <img
                      className="lt"
                      src="/lab-assets/cow-icon-blue.svg"
                      alt=""
                      style={{ width: 64, height: 64, marginBottom: 14 }}
                    />
                    <img
                      className="dk"
                      src="/lab-assets/cow-icon-white.svg"
                      alt=""
                      style={{ width: 64, height: 64, marginBottom: 14 }}
                    />
                    <h2>
                      {filterMode === "following"
                        ? "Nessuna foto dagli utenti seguiti"
                        : "Il feed è pronto per i tuoi scatti"}
                    </h2>
                    <p
                      className="field-hint"
                      style={{ maxWidth: 460, margin: "8px auto 18px" }}
                    >
                      {filterMode === "following"
                        ? "Vai nella scheda Utenti per scoprire e seguire altri fotografi su Nostr."
                        : "Scatta una foto con l'app Android PhotoAnchor oppure dalla Fotocamera Browser: verrà pubblicata su Nostr e ancorata su Bitcoin."}
                    </p>
                    <div style={{ display: "flex", gap: 10, justifyContent: "center" }}>
                      <button
                        type="button"
                        className="btn-primary"
                        onClick={() =>
                          setArea(filterMode === "following" ? "Users" : "Camera")
                        }
                      >
                        {filterMode === "following"
                          ? "Scopri Utenti"
                          : "Apri Modulo Fotocamera"}
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="masonry">
                    {filteredPins.map((pin) => {
                      const likes = reactions.get(pin.id) ?? [];
                      return (
                        <PinCard
                          key={pin.id}
                          pin={pin}
                          anchor={anchors.get(pin.sha256)}
                          authorName={authorNameOf(pin.pubkey)}
                          likeCount={likes.length}
                          liked={likes.some((r) => r.pubkey === signer.pubkey)}
                          saved={allSavedIds.has(pin.id)}
                          onOpen={() => setSelectedPinId(pin.id)}
                          onSave={() => {
                            void social
                              .saveToBoard(pin.id, myBoards[0]?.title || "Preferiti")
                              .then(() =>
                                setNotice(
                                  `Salvata in "${myBoards[0]?.title || "Preferiti"}".`,
                                ),
                              )
                              .catch((err) => setError(String(err)));
                          }}
                          onLike={() => {
                            void social
                              .toggleLike(pin)
                              .catch((err) => setError(String(err)));
                          }}
                          onFilterAuthor={() => {
                            setViewingPubkey(pin.pubkey);
                            setArea("Profile");
                          }}
                        />
                      );
                    })}
                  </div>
                )}
              </>
            )}

            {area === "Users" && (
              <UsersView
                myPubkey={signer.pubkey}
                users={social.discoveredUsers()}
                isFollowing={(pk) => social.isFollowing(pk)}
                onToggleFollow={(pk) => social.toggleFollow(pk)}
                onOpenProfile={(pk) => {
                  setViewingPubkey(pk);
                  setArea("Profile");
                }}
                onMessageUser={async (pk) => {
                  try {
                    await messenger.addContact(pk, authorNameOf(pk));
                    setChatPeerHint({ id: ++seqRef.current, peer: pk });
                    setArea("Messages");
                  } catch (err) {
                    setError(err instanceof Error ? err.message : String(err));
                  }
                }}
              />
            )}

            {area === "Profile" && (
              <ProfileView
                signer={signer}
                viewingPubkey={targetPubkey}
                profile={profiles.get(targetPubkey)}
                pins={pins}
                boards={[...boards.values()]}
                anchors={anchors}
                inbox={inbox}
                followersCount={social.followersOf(targetPubkey).length}
                followingCount={
                  social.state.follows.get(targetPubkey)?.targets.length ?? 0
                }
                isFollowing={social.isFollowing(targetPubkey)}
                likeCountOf={(id) => (reactions.get(id) ?? []).length}
                isLiked={(id) =>
                  (reactions.get(id) ?? []).some((r) => r.pubkey === signer.pubkey)
                }
                isSaved={(id) => allSavedIds.has(id)}
                authorNameOf={authorNameOf}
                onOpenPin={(pin) => setSelectedPinId(pin.id)}
                onSavePin={(pin) => {
                  void social
                    .saveToBoard(pin.id, myBoards[0]?.title || "Preferiti")
                    .then(() => setNotice("Salvata in bacheca."));
                }}
                onLikePin={(pin) => void social.toggleLike(pin)}
                onToggleFollow={() => social.toggleFollow(targetPubkey)}
                onMessageUser={async (pk) => {
                  try {
                    await messenger.addContact(pk, authorNameOf(pk));
                    setChatPeerHint({ id: ++seqRef.current, peer: pk });
                    setArea("Messages");
                  } catch (err) {
                    setError(err instanceof Error ? err.message : String(err));
                  }
                }}
                onOpenShare={() => setSharing(true)}
                onOpenCamera={() => setArea("Camera")}
                onOpenSettings={() => setArea("Settings")}
                onCreateBoard={async (name) => {
                  await social.createBoard(name);
                }}
                onFilterAuthor={(pk) => {
                  setViewingPubkey(pk);
                  setArea("Profile");
                }}
                onBackToMe={() => setViewingPubkey(signer.pubkey)}
                notify={setNotice}
              />
            )}

            {area === "Camera" && (
              <CameraModulePanel
                signer={signer}
                social={social}
                boards={myBoards}
                inbox={inbox}
                onInboxRefresh={refreshInbox}
                onPublished={() => {
                  setViewingPubkey(signer.pubkey);
                  setArea("Profile");
                }}
                notify={setNotice}
              />
            )}

            {area === "Messages" && (
              <MessagesView
                messenger={messenger}
                initialPeerHint={chatPeerHint}
                onShareProfile={() => setSharing(true)}
              />
            )}

            {area === "Settings" && (
              <SettingsView
                signer={signer}
                social={social}
                messenger={messenger}
                exportBackup={exportBackup}
                changePassword={(curr, next) =>
                  vault.changePassword(curr, next)
                }
                notify={setNotice}
              />
            )}
          </main>

          <footer className="site-footer">
            <img className="lt" src="/lab-assets/cow-bull-blue.svg" alt="" />
            <img className="dk" src="/lab-assets/cow-bull-white.svg" alt="" />
            <span>Fatto con</span>
            <i className="ic i-heart" />
            <span>da BitPolito · PhotoAnchor &amp; Nostr · IXH26</span>
          </footer>
        </div>
      </div>

      {selectedPin && (
        <PinModal
          pin={selectedPin}
          anchor={anchors.get(selectedPin.sha256)}
          authorName={authorNameOf(selectedPin.pubkey)}
          boards={myBoards}
          comments={comments.get(selectedPin.id) ?? []}
          likeCount={(reactions.get(selectedPin.id) ?? []).length}
          liked={(reactions.get(selectedPin.id) ?? []).some(
            (r) => r.pubkey === signer.pubkey,
          )}
          onClose={() => setSelectedPinId(null)}
          onLike={() => social.toggleLike(selectedPin)}
          onSaveToBoard={(boardTitle) =>
            social.saveToBoard(selectedPin.id, boardTitle)
          }
          onAddComment={(txt) => social.addComment(selectedPin, txt)}
          onStampAnchor={async () => {
            await social.stampAnchor(selectedPin.sha256);
          }}
          onUpgradeAnchor={async () => {
            await social.upgradeAnchor(selectedPin.sha256);
          }}
          onFilterAuthor={(pk) => {
            setViewingPubkey(pk);
            setArea("Profile");
          }}
          notify={setNotice}
        />
      )}

      {sharing && (
        <ShareProfileModal
          pubkey={signer.pubkey}
          name={myName}
          relays={social.state.relays}
          close={() => setSharing(false)}
          notify={setNotice}
        />
      )}
    </div>
  );
}
