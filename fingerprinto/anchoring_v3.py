"""v3 adapter to the existing OTS implementation; no new backend."""

from dataclasses import asdict

from .evidence_v3 import Anchor, Outcome, Required, reduce_evidence
from .verification import Check, VerificationResult
from .workflow_v3 import verify_v3


def _transport_result(result):
    unknown = Check("indeterminate", "not_used_by_adapter", "See separate v3 findings.")
    return VerificationResult(Check("accepted", "v3_schema_valid", "v3 schema and attachment binding checked."),
                              unknown, Check(result["signature"], "v3_signature_checked", "See v3 result."),
                              unknown, Check("absent", "not_yet_checked", "See v3 result."), result["computed"])


def verify_v3_ots(bundle, *, config, **verification):
    from .anchoring import _verify_ots_result
    result = verify_v3(bundle, **verification)
    checked = _verify_ots_result(_transport_result(result), bundle.ots_proof, config, "fingerprinto/evidence-v3-ots")
    if bundle.ots_proof is None:
        return result
    anchor = {"verified": Anchor.VERIFIED, "pending": Anchor.PENDING,
              "failed": Anchor.FAILED}.get(checked.anchor.status, Anchor.UNRESOLVED)
    result["anchor"] = anchor.value
    result["anchorDetails"] = checked.anchor_details
    result["anchorCheck"] = asdict(checked.anchor)
    result["decision"] = asdict(reduce_evidence(Outcome(result["manufacturer"]["status"]), Outcome(result["prnu"]["status"]),
        enrollment=Required(result["enrollment"]["status"]), binding=Required(result["binding"]),
        signature=Required(result["signature"]), anchor=anchor))
    return result


def stamp_v3(bundle, output_dir, *, config, **verification):
    from .anchoring import AnchorSubmission, _export_result
    from settlement import SettlementService
    result = verify_v3(bundle, **verification)
    if result["decision"]["state"] != "complete":
        raise ValueError("Current enrollment and evidence checks must complete before stamping")
    path = _export_result(_transport_result(result), output_dir)
    return AnchorSubmission(path, SettlementService(config).stamp_file(path, path.parent))
