package Common.Crypto;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import javax.crypto.Cipher;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Crypto {
    private static final String algorithm = "RSA";
    private static final String hashAlgorithm = "SHA-256";

    public static byte[] sign(PrivateKey key, byte[] msg) {
        BigInteger modulus = ((RSAPrivateKey) key).getModulus();
        BigInteger exp = ((RSAPrivateKey) key).getPrivateExponent();

        Digest digest = new SHA256Digest();
        PSSSigner eng = new PSSSigner(new RSAEngine(), digest, digest.getDigestSize());

        eng.init(true, new RSAKeyParameters(true, modulus, exp));

        eng.update(msg, 0, msg.length);

        byte[] retval = null;
        try {
            retval = eng.generateSignature();
        } catch (CryptoException e) {
            e.printStackTrace();
        }

        return retval;
    }

    public static byte[] signBlind(PrivateKey key, byte[] msg) {
        BigInteger modulus = ((RSAPrivateKey) key).getModulus();
        BigInteger exp = ((RSAPrivateKey) key).getPrivateExponent();

        RSAEngine rsaEngine = new RSAEngine();
        rsaEngine.init(false, new RSAKeyParameters(true, modulus, exp));

        return rsaEngine.processBlock(msg, 0, msg.length);
    }


    /**
     * @return true is {@code signature} is a valid digital signature of {@code message} under the
     *         key {@code pubKey}. Internally, this uses RSA signature, but the student does not
     *         have to deal with any of the implementation details of the specific signature
     *         algorithm
     */
    public static boolean verifySignature(PublicKey key, byte[] data, byte[] signature) {
        BigInteger modulus = ((RSAPublicKey) key).getModulus();
        BigInteger exp = ((RSAPublicKey) key).getPublicExponent();

        Digest digest = new SHA256Digest();
        PSSSigner eng = new PSSSigner(new RSAEngine(), digest, digest.getDigestSize());

        eng.init(false, new RSAKeyParameters(false, modulus, exp));

        eng.update(data, 0, data.length);

        return eng.verifySignature(signature);
    }

    public static PublicKey decodePublicKey(byte[] encodedKey) {
        PublicKey retval = null;

        X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedKey);
        KeyFactory kf = null;

        try {
            kf = KeyFactory.getInstance(algorithm);
            retval = kf.generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        return retval;
    }

    public static PrivateKey decodePrivateKey(byte[] encodedKey) {
        PrivateKey retval = null;

        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encodedKey);
        KeyFactory kf = null;

        try {
            kf = KeyFactory.getInstance(algorithm);
            retval = kf.generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }

        return retval;
    }

    public static PublicKey readPublicKeyFromFile(String filename) throws IOException {
        FileInputStream in = new FileInputStream(filename);
        byte[] key = new byte[in.available()];
        in.read(key);
        in.close();

        return decodePublicKey(key);
    }

    public static PrivateKey readPrivateKeyFromFile(String filename) throws IOException {
        FileInputStream in = new FileInputStream(filename);
        byte[] key = new byte[in.available()];
        in.read(key);
        in.close();

        return decodePrivateKey(key);
    }

    public static byte[] calcSHA256sum(byte[] data) {
        try {
            return MessageDigest.getInstance(hashAlgorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static KeyPair generateKeyPair() {
        KeyPairGenerator keyGen = null;
        try {
            keyGen = KeyPairGenerator.getInstance(algorithm);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encrypt(byte[] data, PublicKey key) {
        byte[] cipherText = null;
        try {
            // get an RSA cipher object and print the provider
            final Cipher cipher = Cipher.getInstance(algorithm);
            // encrypt the plain text using the public key
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipherText = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cipherText;
    }

    public static byte[] decrypt(byte[] data, PrivateKey key) {
        byte[] dectyptedText = null;
        try {
            // get an RSA cipher object and print the provider
            final Cipher cipher = Cipher.getInstance(algorithm);

            // decrypt the text using the private key
            cipher.init(Cipher.DECRYPT_MODE, key);
            dectyptedText = cipher.doFinal(data);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return dectyptedText;
    }
}
