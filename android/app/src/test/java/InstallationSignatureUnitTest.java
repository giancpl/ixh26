import org.junit.Test;

public class InstallationSignatureUnitTest {
    @Test
    public void signsExactRootAndRejectsMalformedEvidence() throws Exception {
        InstallationSignatureTest.main(new String[0]);
    }
}
