import com.example.photoanchor.InstallationSignature;
import com.example.photoanchor.ReceiptV1;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;

/** Real JCA signing and strict DER rejection, without Android SDK or hardware claims. */
public class InstallationSignatureTest {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        byte[] root = ReceiptV1.hash("installation-signature-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] signature = InstallationSignature.sign(pair.getPrivate(), spki, root);
        InstallationSignature.verify(spki, root, signature);
        byte[] changed = root.clone(); changed[0] ^= 1;
        reject(() -> InstallationSignature.verify(spki, changed, signature));
        reject(() -> InstallationSignature.sign(pair.getPrivate(), spki, new byte[31]));
        KeyPair other = generator.generateKeyPair();
        reject(() -> InstallationSignature.sign(pair.getPrivate(), other.getPublic().getEncoded(), root));
        for (byte[] invalid : new byte[][] {
                {}, {0x30,6,2,1,0,2,1,1}, {0x30,6,2,1,1,2,1,0},
                {0x30,7,2,2,0,1,2,1,1}, {0x30,6,2,1,(byte)0x80,2,1,1},
                {0x30,9,2,1,1,2,1,1,2,1,1}, Arrays.copyOf(signature, signature.length + 1)}) {
            reject(() -> InstallationSignature.validateDer(invalid));
        }
        byte[] wrongCurve = spki.clone(); wrongCurve[22] ^= 1;
        reject(() -> InstallationSignature.publicKey(wrongCurve));
        byte[] invalidPoint = spki.clone(); Arrays.fill(invalidPoint, 27, invalidPoint.length, (byte)0);
        reject(() -> InstallationSignature.publicKey(invalidPoint));
        reject(() -> InstallationSignature.verify(invalidPoint, root, signature));
        if (args.length == 1) {
            Path output = Paths.get(args[0]);
            Files.createDirectories(output);
            Files.write(output.resolve("public.der"), spki);
            Files.write(output.resolve("root.bin"), root);
            Files.write(output.resolve("signature.der"), signature);
        }
        System.out.println("InstallationSignature: JCA signing, exact-root verification and malformed evidence checks passed");
    }
    interface Operation { void run() throws Exception; }
    static void reject(Operation operation) throws Exception {
        try { operation.run(); }
        catch (GeneralSecurityException expected) { return; }
        throw new AssertionError("Invalid signature/key was accepted");
    }
}
