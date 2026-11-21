package com.example.photoanchor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure reducer of independently evaluated findings; never feed it signed app claims. */
public final class EvidenceV3 {
    public enum Outcome { UNAVAILABLE, INCONCLUSIVE, PASS, FAIL, PENDING }
    public enum Required { VALID, INVALID, PENDING }
    public enum Anchor { ABSENT, PENDING, VERIFIED, UNRESOLVED, FAILED }

    public static final class Decision {
        public final String state, level, label;
        public final boolean canIssue, canOfferChoice;
        private final String[] failed, pending;
        private Decision(String state, String level, String label, boolean issue, boolean choice,
                         List<String> failed, List<String> pending) {
            this.state = state; this.level = level; this.label = label;
            this.canIssue = issue; this.canOfferChoice = choice;
            this.failed = failed.toArray(new String[0]); this.pending = pending.toArray(new String[0]);
        }
        public String[] failedChecks() { return failed.clone(); }
        public String[] pendingChecks() { return pending.clone(); }
    }

    private EvidenceV3() {}

    public static Outcome guardedPrnu(Outcome finding, boolean trustedReference, boolean supportedGeometry,
            boolean adequateQuality, boolean acceptanceCalibrated, boolean rejectionCalibrated) {
        Objects.requireNonNull(finding);
        if (finding != Outcome.PASS && finding != Outcome.FAIL) return finding;
        boolean enabled = finding == Outcome.PASS ? acceptanceCalibrated : rejectionCalibrated;
        return trustedReference && supportedGeometry && adequateQuality && enabled ? finding : Outcome.INCONCLUSIVE;
    }

    public static Decision reduce(Outcome manufacturer, Outcome prnu, Required enrollment,
            Required binding, Required signature, Anchor anchor, boolean issuance) {
        Objects.requireNonNull(manufacturer); Objects.requireNonNull(prnu);
        Objects.requireNonNull(enrollment); Objects.requireNonNull(binding); Objects.requireNonNull(anchor);
        if ((issuance && signature != null) || (!issuance && signature == null))
            throw new IllegalArgumentException("Signature required on import, absent only before signing");
        List<String> failed = new ArrayList<>(), pending = new ArrayList<>();
        if (manufacturer == Outcome.FAIL) failed.add("manufacturer");
        if (prnu == Outcome.FAIL) failed.add("prnu");
        if (enrollment == Required.INVALID) failed.add("enrollment");
        if (binding == Required.INVALID) failed.add("binding");
        if (signature == Required.INVALID) failed.add("signature");
        if (anchor == Anchor.FAILED) failed.add("anchor");
        if (manufacturer == Outcome.PENDING) pending.add("manufacturer");
        if (prnu == Outcome.PENDING) pending.add("prnu");
        if (enrollment == Required.PENDING) pending.add("enrollment");
        if (binding == Required.PENDING) pending.add("binding");
        if (signature == Required.PENDING) pending.add("signature");
        if (anchor == Anchor.UNRESOLVED) pending.add("anchor");
        if (!failed.isEmpty()) return new Decision("failed", null, "Verifica fallita", false, false, failed, pending);
        if (!pending.isEmpty()) return new Decision("pending", null, "Verifica in corso", false, false, failed, pending);
        String level = manufacturer == Outcome.PASS ? "manufacturer" : prnu == Outcome.PASS ? "prnu" : "none";
        String label = level.equals("manufacturer") ? "Verifica del produttore" : level.equals("prnu") ? "Verifica PRNU" : "Nessuna verifica";
        return new Decision("complete", level, label, issuance, !issuance, failed, pending);
    }
}
