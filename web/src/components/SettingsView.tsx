import { useState, type FormEvent } from "react";
import type { MessagingPort, NostrSigner } from "../services/contracts";
import type { SocialService } from "../services/social";
import { Globe, KeyRound, UserRound } from "./PixelIcon";
import { Feedback, Field, useOperation } from "./primitives";

export default function SettingsView({
  signer,
  social,
  messenger,
  exportBackup,
  onLogout,
  notify,
}: {
  signer: NostrSigner;
  social: SocialService;
  messenger?: MessagingPort;
  exportBackup?: () => Promise<void>;
  onLogout?: () => void;
  notify: (msg: string) => void;
}) {
  const existingProfile =
    messenger?.state.profile.draft?.name
      ? messenger.state.profile.draft
      : social.state.profiles.get(signer.pubkey) ||
        messenger?.state.profile.draft || { name: "", about: "" };

  const [name, setName] = useState(existingProfile.name);
  const [about, setAbout] = useState(existingProfile.about);
  const [relaysText, setRelaysText] = useState(social.state.relays.join("\n"));
  const androidBridge =
    typeof window !== "undefined" ? window.AndroidBridge : undefined;
  const [backendUrl, setBackendUrl] = useState(
    () => androidBridge?.getServerUrl?.() ?? "",
  );
  const task = useOperation();

  async function saveAndPublishProfile(e: FormEvent) {
    e.preventDefault();
    await task.run(async () => {
      await social.publishProfile(name, about);
      if (messenger) {
        await messenger.saveProfile({ name, about });
      }
      notify("Profilo Nostr (kind-0) pubblicato sui relay!");
    });
  }

  async function saveRelays(e: FormEvent) {
    e.preventDefault();
    const list = relaysText
      .split(/\s+/)
      .map((s) => s.trim())
      .filter(Boolean);
    await task.run(async () => {
      await social.connect(list);
      if (messenger) await messenger.setRelays(list);
      notify("Lista relay Nostr aggiornata.");
    });
  }

  async function saveAndroidBackend(e: FormEvent) {
    e.preventDefault();
    if (!androidBridge) return;
    await task.run(async () => {
      const clean = backendUrl.trim().replace(/\/+$/, "");
      androidBridge.setServerUrl(clean);
      setBackendUrl(clean);
      await social.connect(social.state.relays);
      notify(
        clean
          ? `Server backend impostato: ${clean}`
          : "Modalità locale attiva.",
      );
    });
  }

  return (
    <div className="settings-grid">
      <Feedback message={task.error} dismiss={task.clearError} />

      <section className="surf settings-card">
        <h3 style={{ marginBottom: 12 }}>
          <UserRound size={18} /> Profilo pubblico Nostr (kind-0)
        </h3>
        <form onSubmit={(e) => void saveAndPublishProfile(e)}>
          <Field label="Nome visualizzato">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              maxLength={60}
              required
            />
          </Field>
          <Field label="Bio">
            <textarea
              value={about}
              onChange={(e) => setAbout(e.target.value)}
              rows={2}
              maxLength={280}
            />
          </Field>
          <Field label="La tua chiave pubblica (npub)">
            <textarea className="mono" readOnly rows={2} value={signer.npub} />
          </Field>
          <button className="btn-primary" disabled={task.busy}>
            Pubblica profilo su Nostr
          </button>
        </form>
      </section>

      <section className="surf settings-card">
        <h3 style={{ marginBottom: 12 }}>
          <Globe size={18} /> Relay Nostr &amp; Connessione
        </h3>
        <form onSubmit={(e) => void saveRelays(e)}>
          <Field
            label="URL Relay (uno per riga)"
            hint="Di default punta al relay Nostr integrato (/nostr). Puoi aggiungere ws://127.0.0.1:7777/ o relay pubblici wss://."
          >
            <textarea
              className="mono"
              value={relaysText}
              onChange={(e) => setRelaysText(e.target.value)}
              rows={4}
              required
            />
          </Field>
          <button className="btn-primary" disabled={task.busy}>
            Connetti ai relay
          </button>
        </form>

        {androidBridge && (
          <form
            onSubmit={(e) => void saveAndroidBackend(e)}
            style={{ marginTop: 18, paddingTop: 16, borderTop: "1px solid var(--bd)" }}
          >
            <Field
              label="Server Backend BitPin (LAN / VPN)"
              hint="Indirizzo del server BitPin (es. http://192.168.1.x:8787 o http://10.0.2.2:8787). Lascia vuoto per usare il relay locale integrato nell'app."
            >
              <input
                className="mono"
                value={backendUrl}
                onChange={(e) => setBackendUrl(e.target.value)}
                placeholder="http://192.168.1.x:8787"
              />
            </Field>
            <button className="btn-primary" disabled={task.busy}>
              Salva server backend
            </button>
          </form>
        )}
      </section>

      {signer.mode === "vault" && exportBackup && (
        <section className="surf settings-card">
          <h3 style={{ marginBottom: 12 }}>
            <KeyRound size={18} /> Chiavi &amp; Backup Identità
          </h3>
          <p className="field-hint" style={{ marginBottom: 12 }}>
            La tua sessione Nostr resta sempre attiva su questo dispositivo senza
            richiedere password all'avvio. Esporta un backup JSON delle tue chiavi
            Nostr per conservarle o ripristinarle su altri dispositivi.
          </p>
          <button
            type="button"
            className="btn-primary wide"
            onClick={() =>
              void task.run(async () => {
                await exportBackup();
                notify("Backup identità scaricato.");
              })
            }
          >
            Esporta backup (.json)
          </button>
          {onLogout && (
            <button
              type="button"
              className="wide"
              style={{ marginTop: 10 }}
              onClick={onLogout}
            >
              Cambia profilo / Esci
            </button>
          )}
        </section>
      )}
    </div>
  );
}
