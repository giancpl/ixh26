import { useState, type FormEvent } from "react";
import type { MessagingPort, NostrSigner } from "../services/contracts";
import type { SocialService } from "../services/social";
import { Globe, KeyRound, UserRound } from "./PixelIcon";
import { Feedback, Field, PasswordInput, useOperation } from "./primitives";

export default function SettingsView({
  signer,
  social,
  messenger,
  exportBackup,
  changePassword,
  notify,
}: {
  signer: NostrSigner;
  social: SocialService;
  messenger?: MessagingPort;
  exportBackup?: () => Promise<void>;
  changePassword?: (curr: string, next: string) => Promise<void>;
  notify: (msg: string) => void;
}) {
  const existingProfile =
    messenger?.state.profile.draft ||
    social.state.profiles.get(signer.pubkey) || { name: "", about: "" };

  const [name, setName] = useState(existingProfile.name);
  const [about, setAbout] = useState(existingProfile.about);
  const [relaysText, setRelaysText] = useState(social.state.relays.join("\n"));
  const [currPass, setCurrPass] = useState("");
  const [nextPass, setNextPass] = useState("");
  const task = useOperation();

  async function saveAndPublishProfile(e: FormEvent) {
    e.preventDefault();
    await task.run(async () => {
      if (messenger) {
        await messenger.saveProfile({ name, about });
        await messenger.publishProfile();
      } else {
        await social.publishProfile(name, about);
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

  async function updatePass(e: FormEvent) {
    e.preventDefault();
    if (!changePassword) return;
    await task.run(async () => {
      await changePassword(currPass, nextPass);
      setCurrPass("");
      setNextPass("");
      notify("Password locale aggiornata.");
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
      </section>

      {signer.mode === "vault" && exportBackup && changePassword && (
        <section className="surf settings-card">
          <h3 style={{ marginBottom: 12 }}>
            <KeyRound size={18} /> Chiavi, Backup &amp; Password Vault
          </h3>
          <p className="field-hint" style={{ marginBottom: 12 }}>
            Esporta un backup JSON cifrato delle tue chiavi Nostr per ripristinarle
            su altri browser o dispositivi.
          </p>
          <button
            type="button"
            className="btn-primary"
            style={{ marginBottom: 18 }}
            onClick={() =>
              void task.run(async () => {
                await exportBackup();
                notify("Backup cifrato scaricato.");
              })
            }
          >
            Esporta backup cifrato (.json)
          </button>

          <form onSubmit={(e) => void updatePass(e)}>
            <PasswordInput
              label="Password attuale (vuota se non impostata)"
              value={currPass}
              onChange={setCurrPass}
              autoComplete="current-password"
            />
            <PasswordInput
              label="Nuova password locale"
              value={nextPass}
              onChange={setNextPass}
              autoComplete="new-password"
            />
            <button disabled={task.busy}>Aggiorna password</button>
          </form>
        </section>
      )}
    </div>
  );
}
