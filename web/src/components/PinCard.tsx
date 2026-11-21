import { Bitcoin, Heart, Hourglass } from "./PixelIcon";
import type { AnchorInfo, PhotoPin } from "../services/contracts";
import { initials } from "../services/identity";

export function AnchorBadge({ anchor }: { anchor?: AnchorInfo }) {
  if (!anchor || anchor.status === "UNANCHORED") {
    return <span className="badge">Non certificata</span>;
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
        <Hourglass size={12} /> In certificazione
      </span>
    );
  }
  if (anchor.status === "INVALID") {
    return <span className="badge err">Prova non valida</span>;
  }
  return <span className="badge">Da verificare</span>;
}

export default function PinCard({
  pin,
  anchor,
  authorName,
  likeCount,
  liked,
  onOpen,
  onLike,
  onFilterAuthor,
}: {
  pin: PhotoPin;
  anchor?: AnchorInfo;
  authorName: string;
  likeCount: number;
  liked: boolean;
  saved?: boolean;
  onOpen: () => void;
  onSave?: () => void;
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
        <div className="pin-media-badge">
          <AnchorBadge anchor={anchor} />
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
            title={`Vedi profilo di ${authorName}`}
          >
            <span
              className="avatar-circle"
              style={{ width: 22, height: 22, fontSize: 10 }}
            >
              {initials(authorName)}
            </span>
            <span>{authorName}</span>
          </button>
          <button
            type="button"
            className={`pin-like ${liked ? "on" : ""}`}
            aria-label={liked ? "Ti piace" : "Mi piace"}
            onClick={(e) => {
              e.stopPropagation();
              onLike();
            }}
          >
            <Heart size={13} /> {likeCount}
          </button>
        </div>
      </div>
    </article>
  );
}
