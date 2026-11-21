import { QRCode } from "react-qr-code";
import { npubEncode } from "nostr-tools/nip19";
import { initials, shareIdentity } from "../services/identity";
import { Share2 } from "./PixelIcon";
import { Dialog, Field } from "./primitives";

export default function ShareProfileModal({
  pubkey,
  name,
  relays,
  close,
  notify,
}: {
  pubkey: string;
  name: string;
  relays: string[];
  close: () => void;
  notify: (msg: string) => void;
}) {
  const npub = npubEncode(pubkey);
  let uri = `nostr:${npub}`;
  try {
    uri = shareIdentity(pubkey, relays);
  } catch {
    /* fallback to nostr:npub */
  }

  return (
    <Dialog title="Condividi profilo Nostr" close={close} narrow>
      <div style={{ textAlign: "center", marginBottom: 18 }}>
        <div
          className="avatar-circle avatar-lg"
          style={{ margin: "0 auto 12px" }}
        >
          {initials(name || "BP")}
        </div>
        <h3>{name || "Profilo PhoTO"}</h3>
        <p className="field-hint">
          Fai scansionare il QR per farti trovare su Nostr o nei messaggi privati.
        </p>
      </div>

      <div className="qr-frame" style={{ margin: "0 auto 18px" }}>
        <QRCode value={uri} size={190} />
      </div>

      <Field label="Identità pubblica (npub)">
        <textarea className="mono" readOnly rows={2} value={npub} />
      </Field>

      <div style={{ display: "flex", gap: 10 }}>
        <button
          type="button"
          className="wide"
          onClick={() => {
            void navigator.clipboard?.writeText(npub);
            notify("npub copiato negli appunti.");
          }}
        >
          Copia npub
        </button>
        <button
          type="button"
          className="btn-primary wide"
          onClick={() => {
            void navigator.clipboard?.writeText(uri);
            notify("Riferimento nostr:nprofile copiato.");
          }}
        >
          <Share2 size={14} /> Copia link Nostr
        </button>
      </div>
    </Dialog>
  );
}
