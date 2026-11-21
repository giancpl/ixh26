import com.example.photoanchor.EvidenceV3;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class EvidenceV3Test {
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (String row : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String[] v = row.split("\t", -1);
            EvidenceV3.Decision d = EvidenceV3.reduce(EvidenceV3.Outcome.valueOf(v[0]), EvidenceV3.Outcome.valueOf(v[1]),
                EvidenceV3.Required.valueOf(v[2]), EvidenceV3.Required.valueOf(v[3]),
                v[4].equals("NONE") ? null : EvidenceV3.Required.valueOf(v[4]), EvidenceV3.Anchor.valueOf(v[5]), Boolean.parseBoolean(v[6]));
            String[] actual = {d.state, String.valueOf(d.level), d.label, String.valueOf(d.canIssue),
                String.valueOf(d.canOfferChoice), String.join(",", d.failedChecks()), String.join(",", d.pendingChecks())};
            for (int i = 0; i < actual.length; i++) if (!actual[i].equals(v[i + 7]))
                throw new AssertionError("Reducer mismatch at row " + count + " field " + i);
            count++;
        }
        for (int mask = 0; mask < 32; mask++) {
            boolean trusted = (mask & 1) != 0, geometry = (mask & 2) != 0, quality = (mask & 4) != 0;
            boolean accept = (mask & 8) != 0, reject = (mask & 16) != 0;
            for (EvidenceV3.Outcome finding : EvidenceV3.Outcome.values()) {
                EvidenceV3.Outcome expected = finding;
                if ((finding == EvidenceV3.Outcome.PASS || finding == EvidenceV3.Outcome.FAIL)
                        && !(trusted && geometry && quality && (finding == EvidenceV3.Outcome.PASS ? accept : reject)))
                    expected = EvidenceV3.Outcome.INCONCLUSIVE;
                if (EvidenceV3.guardedPrnu(finding, trusted, geometry, quality, accept, reject) != expected)
                    throw new AssertionError("PRNU policy gate mismatch");
            }
        }
        System.out.println("EvidenceV3: " + count + " Python/Java decisions and 160 PRNU gate cases passed");
    }
}
