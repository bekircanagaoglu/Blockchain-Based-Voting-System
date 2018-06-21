package Server;

import Common.Crypto.Crypto;

import java.io.IOException;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

public class ServerKeyKeeper {
    private static Map<Integer, KeyPair> blindSignatureKeys;
    private static KeyPair keys;
    private final static String prefix = "/home/user/workspace/IdeaProjects/Blockchain-Based-Voting-System/test_keys/";

    public static void init() {
        try {
            keys = new KeyPair(Crypto.readPublicKeyFromFile(prefix + "server_public.bin"),
                    Crypto.readPrivateKeyFromFile(prefix + "server_private.bin"));
            blindSignatureKeys = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void addBlindSignatureKeys(int id, KeyPair pair) {
        blindSignatureKeys.put(id, pair);
    }

    public static synchronized void removeBlindSignatureKey(int id) {
        blindSignatureKeys.remove(id);
    }

    public static synchronized KeyPair getBlindSignatureKeys(int id) {
        return blindSignatureKeys.get(id);
    }

    public static KeyPair getKeys() {
        return keys;
    }
}
