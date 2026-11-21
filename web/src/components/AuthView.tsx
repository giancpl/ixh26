import {
  Bitcoin,
  KeyRound,
  LockKeyhole,
  UserRound,
  ArrowLeft,
} from "./PixelIcon";
import { useState, type FormEvent } from "react";
import {
  newCredentials,
  type Credentials,
  type VaultService,
} from "../services/vault";
import { profileMetadata } from "../services/identity";
import {
  Feedback,
  Field,
  PasswordInput,
  ThemeToggle,
  useOperation,
} from "./primitives";

type Mode = "unlock" | "create" | "nsec" | "backup";

export default function AuthView({
  vault,
  exists,
  onVaultOpened,
  onStored,
}: {
  vault: VaultService;
  exists: boolean;
  onVaultOpened: (initialProfile?: { name: string; about: string }) => void;
  onStored: () => void;
}) {
  const [mode, setMode] = useState<Mode>(exists ? "unlock" : "create");
  const [step, setStep] = useState(0);
  const [name, setName] = useState("");
  const [about, setAbout] = useState("");
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [nsec, setNsec] = useState("");
  const [backupText, setBackupText] = useState("");
  const [draft, setDraft] = useState<Credentials>();
  const [savedWords, setSavedWords] = useState(false);
  const task = useOperation();

  function switchMode(next: Mode) {
    setMode(next);
    setStep(0);
    setPassword("");
    setConfirmation("");
    setDraft(undefined);
    setSavedWords(false);
    task.clearError();
  }

  function submit(e: FormEvent) {
    e.preventDefault();
    void task.run(async () => {
      if (mode === "unlock") {
        await vault.unlock(password);
        onVaultOpened();
        return;
      }

      if (mode === "backup") {
        if (!backupText) throw new Error("Seleziona un file backup JSON valido.");
        await vault.importBackup(backupText, password);
        try {
          await vault.unlock(password);
          onVaultOpened();
        } catch (err) {
          onStored();
          throw err;
        }
        return;
      }

      if (mode === "nsec") {
        const meta = profileMetadata({ name: name || "Fotografo Nostr", about });
        if (!nsec.trim()) throw new Error("Inserisci la tua chiave privata nsec.");
        if (password !== confirmation)
          throw new Error("Le password locali non coincidono.");
        const creds = newCredentials(undefined, nsec.trim());
        if (await vault.exists()) {
          await vault.replaceWithImport(creds, password);
        } else {
          await vault.create(creds, password);
        }
        await vault.unlock(password);
        await vault.save("profile", { draft: meta, publishStatus: "saved" });
        onVaultOpened(meta);
        return;
      }

      if (step === 0) {
        profileMetadata({ name, about });
        setStep(1);
        return;
      }
      if (step === 1) {
        if (password !== confirmation)
          throw new Error("Le password locali non coincidono.");
        setDraft(draft ?? newCredentials(undefined, nsec.trim() || undefined));
        setStep(2);
        return;
      }
      if (!draft) throw new Error("Torna ai dettagli profilo.");
      if (!savedWords)
        throw new Error("Conferma di aver salvato la frase di recupero.");
      await vault.create(draft, password);
      await vault.unlock(password);
      const meta = profileMetadata({ name, about });
      await vault.save("profile", { draft: meta, publishStatus: "saved" });
      onVaultOpened(meta);
    });
  }

  return (
    <div className="auth-shell">
      <header className="auth-header">
        <a className="brand-link" href="#top">
          <img className="lt" src="/lab-assets/cow-icon-blue.svg" alt="" />
          <img className="dk" src="/lab-assets/cow-icon-white.svg" alt="" />
          <span>
            PhoTO
            <span className="brand-sub">by BitPolito · Nostr &amp; Bitcoin</span>
          </span>
        </a>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span className="badge">
            <Bitcoin size={14} /> OpenTimestamps
          </span>
          <ThemeToggle />
        </div>
      </header>

      <main className="auth-main">
        <section className="auth-hero">
          <img
            className="auth-hero-cow lt"
            src="/lab-assets/cow-icon-blue.svg"
            alt="BitPolito"
          />
          <img
            className="auth-hero-cow dk"
            src="/lab-assets/cow-icon-white.svg"
            alt="BitPolito"
          />
          <h1>
            Scatta, <span>condividi su Nostr</span> e ancora su Bitcoin.
          </h1>
          <p className="auth-lead">
            Social network fotografico decentralizzato con modulo fotocamera
            integrato, bacheche stile Pinterest, profili utenti e prove
            OpenTimestamps su Bitcoin. Tutto nativo su Nostr, senza nessuna
            estensione esterna.
          </p>
          <div className="feature-pills">
            <span className="badge solid">
              <KeyRound size={14} /> Chiavi Nostr integrate
            </span>
            <span className="badge">
              <UserRound size={14} /> Profili, Bacheche &amp; Follow
            </span>
            <span className="badge pending">
              <Bitcoin size={14} /> Prove .ots reali
            </span>
          </div>
        </section>

        <section className="surf auth-card">
          <div className="segmented" role="tablist" aria-label="Accesso Nostr">
            {exists && (
              <button
                type="button"
                className={mode === "unlock" ? "selected" : ""}
                onClick={() => switchMode("unlock")}
              >
                Sblocca
              </button>
            )}
            {!exists && (
              <button
                type="button"
                className={mode === "create" ? "selected" : ""}
                onClick={() => switchMode("create")}
              >
                Crea account
              </button>
            )}
            <button
              type="button"
              className={mode === "nsec" ? "selected" : ""}
              onClick={() => switchMode("nsec")}
            >
              Accedi con nsec
            </button>
            {!exists && (
              <button
                type="button"
                className={mode === "backup" ? "selected" : ""}
                onClick={() => switchMode("backup")}
              >
                Ripristina
              </button>
            )}
          </div>

          <h2>
            {mode === "unlock"
              ? "Bentornato su PhoTO"
              : mode === "nsec"
                ? "Accedi con chiave Nostr"
                : mode === "backup"
                  ? "Ripristina backup cifrato"
                  : step === 2
                    ? "Salva le parole di recupero"
                    : step === 1
                      ? "Password locale (opzionale)"
                      : "Crea il tuo profilo Nostr"}
          </h2>
          <p className="sub">
            {mode === "unlock"
              ? "Inserisci la password locale (o lascia vuoto se non impostata)."
              : mode === "nsec"
                ? "Usa la tua chiave privata nsec: resta cifrata localmente nel sito."
                : mode === "backup"
                  ? "Carica un file backup JSON esportato in precedenza."
                  : step === 2
                    ? "Conserva queste 12 parole al sicuro. Dalle Impostazioni potrai anche esportare la chiave."
                    : step === 1
                      ? "Proteggi il profilo su questo dispositivo, oppure lascia entrambi i campi vuoti."
                      : "Nessun server centrale o estensione: generiamo qui le tue chiavi Nostr."}
          </p>

          <Feedback message={task.error} dismiss={task.clearError} />

          <form onSubmit={submit}>
            {mode === "unlock" && (
              <PasswordInput
                label="Password locale"
                value={password}
                onChange={setPassword}
                autoComplete="current-password"
                hint="Lascia vuoto se il profilo è stato creato senza password."
              />
            )}

            {mode === "nsec" && (
              <>
                <Field label="Nome visualizzato">
                  <input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Es. Marco · BitPolito"
                    maxLength={60}
                  />
                </Field>
                <Field label="Chiave privata Nostr (nsec1… o hex)">
                  <input
                    type="password"
                    value={nsec}
                    onChange={(e) => setNsec(e.target.value)}
                    placeholder="nsec1..."
                    autoComplete="off"
                    spellCheck={false}
                    required
                  />
                </Field>
                <PasswordInput
                  label="Password locale (opzionale)"
                  value={password}
                  onChange={setPassword}
                  autoComplete="new-password"
                />
                <PasswordInput
                  label="Conferma password"
                  value={confirmation}
                  onChange={setConfirmation}
                  autoComplete="new-password"
                />
              </>
            )}

            {mode === "backup" && (
              <>
                <Field label="File backup (.json)">
                  <input
                    type="file"
                    accept="application/json,.json"
                    required
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) void f.text().then(setBackupText);
                    }}
                  />
                </Field>
                <PasswordInput
                  label="Password del backup"
                  value={password}
                  onChange={setPassword}
                  autoComplete="current-password"
                />
              </>
            )}

            {mode === "create" && step === 0 && (
              <>
                <Field label="Nome profilo">
                  <input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Come ti vedranno gli altri utenti"
                    maxLength={60}
                    required
                  />
                </Field>
                <Field label="Bio (opzionale)">
                  <textarea
                    value={about}
                    onChange={(e) => setAbout(e.target.value)}
                    placeholder="Fotografia street, architettura, Bitcoin..."
                    maxLength={280}
                    rows={2}
                  />
                </Field>
              </>
            )}

            {mode === "create" && step === 1 && (
              <>
                <PasswordInput
                  label="Password locale (opzionale)"
                  value={password}
                  onChange={setPassword}
                  autoComplete="new-password"
                  hint="Lascia entrambi i campi vuoti per accesso immediato."
                />
                <PasswordInput
                  label="Conferma password"
                  value={confirmation}
                  onChange={setConfirmation}
                  autoComplete="new-password"
                />
              </>
            )}

            {mode === "create" && step === 2 && draft && (
              <>
                <ol className="word-grid">
                  {draft.mnemonic.split(" ").map((w, i) => (
                    <li key={i}>
                      <span>{i + 1}</span>
                      {w}
                    </li>
                  ))}
                </ol>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={savedWords}
                    onChange={(e) => setSavedWords(e.target.checked)}
                    required
                  />
                  Ho annotato la mia frase di recupero.
                </label>
              </>
            )}

            <button className="btn-primary wide" disabled={task.busy}>
              <LockKeyhole size={16} />
              {task.busy
                ? "Accesso in corso…"
                : mode === "unlock"
                  ? "Sblocca ed entra"
                  : mode === "nsec"
                    ? "Accedi con Nostr"
                    : mode === "backup"
                      ? "Ripristina ed entra"
                      : step === 2
                        ? "Entra nel social"
                        : "Continua"}
            </button>

            {mode === "create" && step > 0 && (
              <button
                type="button"
                className="btn-ghost wide"
                style={{ marginTop: 8 }}
                onClick={() => setStep(step - 1)}
              >
                <ArrowLeft size={14} /> Indietro
              </button>
            )}
          </form>
        </section>
      </main>
    </div>
  );
}
