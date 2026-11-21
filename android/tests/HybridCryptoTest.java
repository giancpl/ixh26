import com.example.photoanchor.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Software JCA/BC fixtures only; never evidence of Android hardware protection. */
public class HybridCryptoTest {
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args[0]);
        if (args.length == 2) {
            HybridSoftwareKeys.Public pub = new HybridSoftwareKeys.Public(Files.readAllBytes(output.resolve("hardware.der")),
                Files.readAllBytes(output.resolve("ed.pub")), Files.readAllBytes(output.resolve("ml.pub")));
            pub.verify(Files.readAllBytes(output.resolve("device.bin")), Files.readAllBytes(output.resolve("root.bin")),
                new HybridSoftwareKeys.Signatures(Files.readAllBytes(output.resolve("ed.sig")), Files.readAllBytes(output.resolve("ml.sig"))));
            System.out.println("Python hybrid signatures verified by Java");
            return;
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair hardwareFixture = generator.generateKeyPair();
        byte[] spki = hardwareFixture.getPublic().getEncoded();
        byte[] root = ReceiptV1.hash("hybrid-interop".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] hardwareId = ReceiptV1.hash(spki);
        KeyGenerator aes = KeyGenerator.getInstance("AES"); aes.init(256);
        SecretKey wrappingFixture = aes.generateKey();
        try (HybridSoftwareKeys keys = HybridSoftwareKeys.generate(spki)) {
            HybridSoftwareKeys.Public pub = keys.publicKey();
            HybridSoftwareKeys.Signatures signatures = keys.sign(root);
            pub.verify(pub.deviceId(), root, signatures);
            byte[] binding = InstallationSignature.sign(hardwareFixture.getPrivate(), spki, pub.bindingRoot());
            pub.verifyBinding(binding);
            reject(() -> pub.verify(new byte[32], root, signatures));
            reject(() -> pub.verify(pub.deviceId(), new byte[32], signatures));
            byte[] payload = keys.privatePayload();
            try {
                byte[] sealed = HybridKeyEnvelope.seal(wrappingFixture, payload, pub.deviceId(), hardwareId, binding);
                byte[] again = HybridKeyEnvelope.seal(wrappingFixture, payload, pub.deviceId(), hardwareId, binding);
                if (Arrays.equals(sealed, again)) throw new AssertionError("Nonce reused");
                try (HybridKeyEnvelope.Opened opened = HybridKeyEnvelope.open(wrappingFixture, sealed, pub.deviceId(), hardwareId);
                     HybridSoftwareKeys restored = HybridSoftwareKeys.fromPrivatePayload(opened.payload())) {
                    pub.verify(pub.deviceId(), root, restored.sign(root));
                }
                for (int offset : new int[]{0, 4, 36, 69, sealed.length - 1}) {
                    byte[] changed = sealed.clone(); changed[offset] ^= 1;
                    reject(() -> HybridKeyEnvelope.open(wrappingFixture, changed, pub.deviceId(), hardwareId));
                }
                SecretKey wrongKey = aes.generateKey();
                reject(() -> HybridKeyEnvelope.open(wrongKey, sealed, pub.deviceId(), hardwareId));
                reject(() -> HybridKeyEnvelope.open(wrappingFixture, sealed, new byte[32], hardwareId));
            } finally { Arrays.fill(payload, (byte)0); }
            Files.createDirectories(output);
            Files.write(output.resolve("public.cbor"), pub.encoded());
            Files.write(output.resolve("signature.cbor"), signatures.encoded());
            Files.write(output.resolve("root.bin"), root);
            Files.write(output.resolve("binding.der"), binding);
            // Synthetic chain bytes exercise encoding only, not enrollment.
            HybridReceiptV2 receipt = new HybridReceiptV2(pub, new byte[][]{{0x30,0}}, "example.photo", 1,
                new byte[32], "Fixture", "Model", 123, null, 0, binding);
            byte[] receiptRoot = receipt.root(new java.io.ByteArrayInputStream("original".getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
            HybridSoftwareKeys.Signatures receiptSignature = keys.sign(receiptRoot);
            Files.write(output.resolve("receipt.cbor"), receipt.encoded(receiptSignature));
            Files.write(output.resolve("commitment.bin"), receipt.commitment(pub.deviceId(), receiptRoot, receiptSignature));
        }
        System.out.println("Hybrid AND-signatures and AES-GCM envelope checks passed");
    }
    interface Operation { void run() throws Exception; }
    static void reject(Operation operation) throws Exception {
        try { operation.run(); }
        catch (GeneralSecurityException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid hybrid evidence accepted");
    }
}
