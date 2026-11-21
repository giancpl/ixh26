import org.junit.Test;

/** Runs the same byte-preservation checks through Gradle's Android unit-test task. */
public class CaptureEvidenceUnitTest {
    @Test
    public void preservesOriginalAndRejectsChanges() throws Exception {
        CaptureEvidenceTest.main(new String[0]);
    }
}
