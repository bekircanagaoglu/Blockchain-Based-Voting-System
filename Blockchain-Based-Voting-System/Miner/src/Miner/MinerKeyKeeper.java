package Miner;

import Common.Crypto.Crypto;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class MinerKeyKeeper {
    private static PublicKey serverPublicKey;
    private static KeyPair keyPair;
    private static PublicKey lastMinersPK;
    private static final Map<Integer, PublicKey> blindSigKeys = new HashMap<>();

    public static PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    public static void readKeyPair(String publicKeyPath, String privateKeyPath) throws IOException {
        keyPair = new KeyPair(Crypto.readPublicKeyFromFile(publicKeyPath),
                Crypto.readPrivateKeyFromFile(privateKeyPath));
    }

    public static KeyPair getKeyPair() {
        return keyPair;
    }

    public static void generateKeyPair() {
        MinerKeyKeeper.keyPair = Crypto.generateKeyPair();
    }

    public static PublicKey getLastMinersPK() {
        return lastMinersPK;
    }

    public static void setLastMinersPK(PublicKey lastMinersPK) {
        MinerKeyKeeper.lastMinersPK = lastMinersPK;
    }

    public static void setServerPublicKey(PublicKey serverPublicKey) {
        MinerKeyKeeper.serverPublicKey = serverPublicKey;
    }

    public static synchronized void addBlindSigKey(int id, PublicKey key) {
        blindSigKeys.put(id, key);
    }

    public static synchronized PublicKey getBlindSigKey(int id) {
        return blindSigKeys.get(id);
    }

    public static synchronized void removeBlindSignatureKey(int id) {
        blindSigKeys.remove(id);
    }
}
