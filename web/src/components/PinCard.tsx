import { Bitcoin, Hourglass } from "./PixelIcon";
import type { AnchorInfo, PhotoPin } from "../services/contracts";
import { initials } from "../services/identity";

export function AnchorBadge({ anchor }: { anchor?: AnchorInfo }) {
  if (!anchor || anchor.status === "UNANCHORED") {
    return <span className="badge">Locale</span>;
  }
  if (anchor.status === "CONFIRMED") {
    return (
      <span className="badge ok" title="Confermato nella timechain Bitcoin">
        <Bitcoin size={12} /> BTC #{anchor.block_height ?? "OK"}
      </span>
    );
  }
  if (anchor.status === "PENDING") {
    return (
      <span className="badge pending" title="In attesa di conferma blocco Bitcoin">
        <Hourglass size={12} /> OTS Pending
      </span>
    );
  }
  if (anchor.status === "INVALID") {
    return <span className="badge err">OTS Non Valido</span>;
  }
  return <span className="badge">Da verificare</span>;
}

export default function PinCard({
  pin,
  anchor,
  authorName,
  likeCount,
  liked,
  saved,
  onOpen,
  onSave,
  onLike,
  onFilterAuthor,
}: {
  pin: PhotoPin;
  anchor?: AnchorInfo;
  authorName: string;
  likeCount: number;
  liked: boolean;
  saved: boolean;
  onOpen: () => void;
  onSave: () => void;
  onLike: () => void;
  onFilterAuthor: () => void;
}) {
  return (
    <article className="pin-card" onClick={onOpen}>
      <div className="pin-media">
        <img
          src={pin.imageUrl}
          alt={pin.title}
          loading="lazy"
          style={
            pin.width && pin.height
              ? { aspectRatio: `${pin.width} / ${pin.height}` }
              : undefined
          }
        />
        <div className="pin-overlay">
          <div className="pin-overlay-top">
            <AnchorBadge anchor={anchor} />
            <button
              type="button"
              className="btn-primary pin-save-btn"
              onClick={(e) => {
                e.stopPropagation();
                onSave();
              }}
            >
              {saved ? "Salvato ✓" : "Salva"}
            </button>
          </div>
          <div className="pin-overlay-bottom">
            {pin.source === "android-photoanchor" ? (
              <span className="badge solid">PhotoAnchor App</span>
            ) : (
              <span />
            )}
            <button
              type="button"
              className="pin-save-btn"
              onClick={(e) => {
                e.stopPropagation();
                onLike();
              }}
            >
              {liked ? "♥" : "♡"} {likeCount}
            </button>
          </div>
        </div>
      </div>

      <div className="pin-body">
        <div className="pin-title">{pin.title}</div>
        <div className="pin-meta-row">
          <button
            type="button"
            className="pin-author"
            onClick={(e) => {
              e.stopPropagation();
              onFilterAuthor();
            }}
            title={`Vedi foto di ${authorName}`}
          >
            <span
              className="avatar-circle"
              style={{ width: 22, height: 22, fontSize: 10 }}
            >
              {initials(authorName)}
            </span>
            <span>{authorName}</span>
          </button>
          <span className="badge" style={{ fontSize: 10, padding: "2px 7px" }}>
            {pin.board}
          </span>
        </div>
      </div>
    </article>
  );
}
