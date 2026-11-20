import { sha256 as nobleSha256 } from "@noble/hashes/sha2.js";
import { AlertCircle } from "./PixelIcon";
import {
  Children,
  cloneElement,
  isValidElement,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactElement,
  type ReactNode,
} from "react";

export function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  const id = useId();
  function associate(nodes: ReactNode): ReactNode {
    return Children.map(nodes, (node) => {
      if (!isValidElement(node) || typeof node.type !== "string") return node;
      const element = node as ReactElement<{
        children?: ReactNode;
        id?: string;
        "aria-describedby"?: string;
      }>;
      if (["input", "select", "textarea"].includes(node.type)) {
        return cloneElement(element, {
          id,
          "aria-describedby": hint ? `${id}-hint` : undefined,
        });
      }
      return element.props.children
        ? cloneElement(element, { children: associate(element.props.children) })
        : element;
    });
  }
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {associate(children)}
      {hint && (
        <small id={`${id}-hint`} className="field-hint">
          {hint}
        </small>
      )}
    </div>
  );
}

export function PasswordInput({
  label,
  value,
  onChange,
  autoComplete,
  hint,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: string;
  hint?: string;
}) {
  const [visible, setVisible] = useState(false);
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="password-control">
        <input
          id={id}
          type={visible ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          autoComplete={autoComplete}
          aria-describedby={hint ? `${id}-hint` : undefined}
        />
        <button
          type="button"
          className="btn-ghost text-control"
          onClick={() => setVisible(!visible)}
        >
          {visible ? "Nascondi" : "Mostra"}
        </button>
      </div>
      {hint && (
        <small id={`${id}-hint`} className="field-hint">
          {hint}
        </small>
      )}
    </div>
  );
}

export function Feedback({
  message,
  kind = "error",
  dismiss,
}: {
  message: string;
  kind?: "error" | "success";
  dismiss?: () => void;
}) {
  if (!message) return null;
  return (
    <div className={`notice ${kind}`} role={kind === "error" ? "alert" : "status"}>
      {kind === "error" && <AlertCircle size={18} />}
      <span>{message}</span>
      {dismiss && (
        <button type="button" className="btn-ghost" onClick={dismiss} style={{ minHeight: 30, padding: "2px 8px" }}>
          Chiudi
        </button>
      )}
    </div>
  );
}

export function Dialog({
  title,
  children,
  close,
  narrow = false,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
  narrow?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const id = useId();
  useEffect(() => {
    const d = ref.current!;
    if (!d.open) d.showModal();
    return () => {
      if (d.open) d.close();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={`modal-dialog ${narrow ? "narrow" : ""}`}
      aria-labelledby={id}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <div className="modal-surface">
        <div className="modal-head">
          <h2 id={id}>{title}</h2>
          <button type="button" className="btn-ghost" onClick={close}>
            Chiudi
          </button>
        </div>
        {children}
      </div>
    </dialog>
  );
}

export function useOperation() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const active = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  async function run(fn: () => Promise<unknown>) {
    if (active.current) return;
    active.current = true;
    setBusy(true);
    setError("");
    try {
      await fn();
    } catch (err) {
      if (mounted.current)
        setError(err instanceof Error ? err.message : "Operazione non riuscita.");
    } finally {
      active.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  return { busy, error, run, clearError: () => setError("") };
}

export function ThemeToggle() {
  const toggle = () => {
    const nextDark = !document.documentElement.classList.contains("dark");
    document.documentElement.classList.toggle("dark", nextDark);
    try {
      localStorage.setItem("bitpin-theme", nextDark ? "dark" : "light");
    } catch {
      /* ignore */
    }
  };
  return (
    <button
      id="theme"
      type="button"
      role="switch"
      aria-label="Cambia tema chiaro/scuro"
      onClick={toggle}
    >
      <span className="knob">
        <i className="ic i-sun" />
        <i className="ic i-moon" />
      </span>
    </button>
  );
}

export function formatTime(ts: number): string {
  return new Date(ts * 1000).toLocaleString("it-IT", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

export async function sha256File(file: Blob): Promise<string> {
  const buf = await file.arrayBuffer();
  if (typeof crypto !== "undefined" && crypto.subtle) {
    const digest = await crypto.subtle.digest("SHA-256", buf);
    return Array.from(new Uint8Array(digest), (b) =>
      b.toString(16).padStart(2, "0"),
    ).join("");
  }
  const digest = nobleSha256(new Uint8Array(buf));
  return Array.from(digest, (b) => b.toString(16).padStart(2, "0")).join("");
}
