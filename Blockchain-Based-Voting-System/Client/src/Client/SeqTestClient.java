package Client;

import Common.Acknowledge;
import Common.Crypto.Crypto;
import Common.Election;
import Common.Job;
import Common.TLS.SSLUtil;
import Common.Token.Token;
import Common.Transaction.Vote;
import Common.User.User;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSABlindingEngine;
import org.bouncycastle.crypto.generators.RSABlindingFactorGenerator;
import org.bouncycastle.crypto.params.RSABlindingParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class SeqTestClient {
    public static int[][] results = new int[2][4];

    public static void main(String[] args) {
        int N = 1000;
        int t = 4;
        Thread[] threads = new Thread[8];

        for (int i = 0; i < 4; ++i) {
            threads[i] = new TestThread(i * N / t, (i + 1) * N / t, 0);
            threads[i + 4] = new TestThread(i * N / t, (i + 1) * N / t, 1);
            threads[i].start();
            threads[i + 4].start();
        }

        for (Thread th : threads) {
            try {
                th.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(Arrays.toString(results[0]));
        System.out.println(Arrays.toString(results[1]));
    }
}

class TestThread extends Thread {
    private int start;
    private int end;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private SSLSocket sslSocket = null;
    private SSLContext sc = null;
    private final String PATH = "/home/user/workspace/IdeaProjects/keygen/src/keys/";
    private int eid;

    public TestThread(int start, int end, int eid) {
        this.start = start;
        this.end = end;
        this.eid = eid;
        try {
            sc = SSLUtil.createClientSSLContext();
            SSLSocketFactory sslSocketFactory = sc.getSocketFactory();
            sslSocket = (SSLSocket) sslSocketFactory.createSocket("localhost", 4000);
            sslSocket.startHandshake();
            out = new ObjectOutputStream(sslSocket.getOutputStream());
            in = new ObjectInputStream(sslSocket.getInputStream());
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            out.writeObject(Job.GetOngoingElections);
            MutablePair<Integer, String>[] ongoingElections = (MutablePair[]) in.readObject();
            Token token = null;
            int[] count = new int[4];
            for (int i = start; i < end; ++i) {
                KeyPair keyPair = Crypto.generateKeyPair();
//                        new KeyPair(Crypto.readPublicKeyFromFile(PATH + i + ".pub"),
//                        Crypto.readPrivateKeyFromFile(PATH + i + ".prv")
//                );

                out.writeObject(Job.TokenCreation);
                out.writeObject(ongoingElections[eid]);
                String s = Integer.toString(i + 1);
                out.writeObject(new User(s, s));

                Object o = in.readObject();

                if(o.equals(Acknowledge.SUCCESS)) {
                    PublicKey pk = (PublicKey) in.readObject();

                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    digest.update(keyPair.getPublic().getEncoded());
                    byte[] hash = digest.digest();
                    token = createToken(hash, pk);
                } else {
                    System.out.println("Fail?");
                    continue;
                }

                out.writeObject(Job.GetElection);
                out.writeObject(ongoingElections[eid]);
                Election e = (Election) in.readObject();

                out.writeObject(Job.Voting);
                out.writeObject(ongoingElections[eid]);
                PublicKey pk = Crypto.decodePublicKey((byte[]) in.readObject());
                Vote v = new Vote();
                v.setElectionID(ongoingElections[eid].getKey());
                v.setEncodedKey(keyPair.getPublic().getEncoded());
                v.setToken(token);

                int n = ThreadLocalRandom.current().nextInt(e.getNvotes_l(), e.getNvotes_h() + 1);

                ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE / 8 * (n + 2) + Long.SIZE / 8);
                buf.putInt(n);

                int max = e.getCandidates().size();

                List<Integer> votes = new ArrayList<>();

                while (n-- > 0) {
                    for (;;) {
                        int tmp = ThreadLocalRandom.current().nextInt(max);
                        if (!votes.contains(tmp)) {
                            votes.add(tmp);
                            break;
                        }
                    }
                }
                int chksm = 0;

                for (Integer tmp : votes) {
                    buf.putInt(tmp);
                    chksm += e.getCandidates().get(tmp).getName().hashCode();
                }
                buf.putInt(chksm);
                long nonce = new Random().nextLong();
                buf.putLong(nonce);
                v.setVote(Crypto.encrypt(buf.array(), pk));
                v.setSignature(Crypto.sign(keyPair.getPrivate(), v.getRawDataToSign()));

                out.writeObject(v);

                o = in.readObject();

                if(!o.equals(Acknowledge.SUCCESS))
                    System.out.println("Fail?");
                else {
                    for (Integer tmp : votes)
                        count[tmp]++;
                }

//
//                try {
//                    Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 1500));
//                } catch (InterruptedException e1) {
//                    e1.printStackTrace();
//                }

            }

            synchronized (SeqTestClient.results) {
                for (int i = 0; i < count.length; ++i)
                    SeqTestClient.results[eid][i] += count[i];
            }
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    private Token createToken(byte[] msg, PublicKey server_key) throws IOException, ClassNotFoundException {

        RSAKeyParameters server_pub = new RSAKeyParameters(false, ((RSAPublicKey) server_key).getModulus(),
                ((RSAPublicKey) server_key).getPublicExponent());

        //blinding parameters
        RSABlindingFactorGenerator blindingFactorGenerator = new RSABlindingFactorGenerator();
        blindingFactorGenerator.init(server_pub);
        BigInteger blindingFactor = blindingFactorGenerator.generateBlindingFactor();
        RSABlindingParameters blindingParams = new RSABlindingParameters(server_pub, blindingFactor);

        //blind data
        Digest digest = new SHA256Digest();
        PSSSigner signer = new PSSSigner(new RSABlindingEngine(), digest, digest.getDigestSize());
        signer.init(true, blindingParams);
        signer.update(msg, 0, msg.length);

        byte[] blindedData = null;
        try {
            blindedData = signer.generateSignature();
        } catch (CryptoException e) {
            e.printStackTrace();
        }

        //send server
        Token token = new Token();
        token.setData(blindedData);
        out.writeObject(token);

        token  = (Token) in.readObject();

        // unblind the signature
        RSABlindingEngine blindingEngine = new RSABlindingEngine();
        blindingEngine.init(false, blindingParams);
        byte[] sig = blindingEngine.processBlock(token.getSignature(), 0, token.getSignature().length);

        //set return value
        token.setData(msg);
        token.setSignature(sig);

        return token;
    }
}
