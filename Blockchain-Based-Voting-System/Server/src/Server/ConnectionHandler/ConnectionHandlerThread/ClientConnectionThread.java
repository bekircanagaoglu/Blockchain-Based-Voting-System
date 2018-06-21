package Server.ConnectionHandler.ConnectionHandlerThread;

import java.io.*;
import java.net.Socket;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import Common.Command.Command;
import Common.Command.Request.GetVote;
import Common.Common;
import Common.Crypto.Crypto;
import Common.TLS.SSLUtil;
import Common.Token.Token;
import Common.Transaction.TokenCreation;
import Common.Transaction.Transaction;
import Common.Acknowledge;
import Common.User.User;
import Common.Job;
import Server.ConnectionHandler.ServerConnectionHandler;
import Server.Dispatcher.DispatcherMonitor;
import Server.ServerEmailSender;
import Server.ResponseMonitor.GetVoteResponseMonitor;
import Server.ServerKeyKeeper;
import Server.Registrar.*;
import Common.Transaction.Vote;
import org.apache.commons.lang3.tuple.MutablePair;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

public class ClientConnectionThread implements Runnable {

    private static final int PORT = 4000;

    @Override
    public void run() {
        try {
            SSLContext sc = SSLUtil.createServerSSLContext();
            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(PORT);
            for (; ; ) {
                SSLSocket s = (SSLSocket) serverSocket.accept();

                new Thread(new ClientHandler(s)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {

    private final Socket s;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public ClientHandler(Socket s) throws IOException {
        this.s = s;
        out = new ObjectOutputStream(s.getOutputStream());
        in = new ObjectInputStream(s.getInputStream());
    }

    private void clean() {
        try {
            in.close();
        } catch (IOException e) {}
        try {
            out.close();
        } catch (IOException e) {}
        try {
            s.close();
        } catch (IOException e) {}
    }

    @Override
    public void run() {
        Object o;
        Transaction tx;
        try {
            for (;;) {
                o = in.readObject();

                if (o instanceof Job) {
                    tx = null;
                    Job j = (Job) o;

                    System.out.println(j);

                    switch (j) {
                        case TokenCreation:
                            tx = tokenCreation();
                            break;
                        case Voting:
                            tx = voting();
                            break;
                        case GetVote:
                            getVote();
                            break;
                        case GetCandidates:
                            getCandidates();
                            break;
                        case GetElection:
                            getElection();
                            break;
                        case GetOngoingElections:
                            out.writeObject(Registrar.getOngoingElections());
                            break;
                        case GetMiners:
                            out.writeObject(DispatcherMonitor.getAuditsAndMiners());
                            break;
                        case GetOTP:
                            getOTP();
                            break;
                    }

                    if (tx != null) {
                        tx.timestamp();
                        tx.setServersSignature(Crypto.sign(ServerKeyKeeper.getKeys().getPrivate(),
                                tx.getRawDataWithTimestampToSign()));
                        Transaction finalTx = tx;
                        ServerConnectionHandler.broadcast(finalTx);
                        DispatcherMonitor.newTx();
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            clean();
        }
    }

    private void getElection() {
        Object o = null;
        try {
            o = in.readObject();

            if (!(o instanceof MutablePair))
                out.writeObject(Acknowledge.FAIL);

            MutablePair<Integer, String> election = (MutablePair<Integer, String>) o;

            out.writeObject(Registrar.getElection(election.left));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void getCandidates() {
        try {
            MutablePair<Integer, String> election = (MutablePair<Integer, String>) in.readObject();
            out.writeObject(Registrar.getCandidateList(election.getKey()));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void getVote() {

        try {
            Command c = (Command) in.readObject();

            c.setSignature(Crypto.sign(ServerKeyKeeper.getKeys().getPrivate(),
                    c.getRawData()));

            if (!ServerConnectionHandler.sendObjectToNthPeer(c, 0)) {
                out.writeObject(Acknowledge.FAIL);
            } else {
                Object o = GetVoteResponseMonitor.waitResponse(((GetVote) c).getKey()).getResponse();

                if (o.equals(Acknowledge.FAIL) || !(o instanceof Vote)) {
                    out.writeObject(Acknowledge.FAIL);
                } else {
                    MutablePair<List<Integer>, Integer> v = Common.openVote(((Vote) o).getVote(),
                            ServerKeyKeeper.getBlindSignatureKeys(((Vote) o).getElectionID()).getPrivate());
                    StringBuilder sb = new StringBuilder();
                    sb.append("Timestamp: ").append(((Vote) o).getTimestamp()).append("\n");

                    for (Integer i : v.left)
                        sb.append("Candidate: ").append(Registrar.getCandidateList(((Vote) o).getElectionID())[i]).append("\n");

                    out.writeObject(sb.toString());
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    private void getOTP() throws IOException {
        try {
            MutablePair<Integer, String> election = (MutablePair<Integer, String>) in.readObject();
            String email = (String) in.readObject();
            final int PWD_LEN = 6;

            if (Registrar.isEmailValid(email, election.left)) {
                try {
                    String pwd = generateOTP(6);
                    Registrar.addUser(new User(email,
                                    Common.bytesToHex(Crypto.calcSHA256sum(pwd.getBytes("UTF-8")))),
                            election.left);

                    if (ServerEmailSender.sendEmail(email, election.right + " OTP", "Password: " + pwd)) {
                        out.writeObject(Acknowledge.SUCCESS);
                    } else {
                        Registrar.validateEmail(email, election.left);
                        out.writeObject(Acknowledge.FAIL);
                    }
                } catch (IOException e) {
                    Registrar.validateEmail(email, election.left);
                }
            } else {
                out.writeObject(Acknowledge.FAIL);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * source: https://www.geeksforgeeks.org/generating-password-otp-java/
     */
    private static String generateOTP(int len)
    {
        // A strong password has Cap_chars, Lower_chars,
        // numeric value and symbols. So we are using all of
        // them to generate our password
        String Small_chars = "abcdefghijklmnopqrstuvwxyz";
        String numbers = "0123456789";

        String values =  Small_chars + numbers;
        StringBuilder sb = new StringBuilder();

        while (len-- > 0)
            sb.append(values.charAt(ThreadLocalRandom.current().nextInt(values.length())));

        return sb.toString();
    }

    private Transaction tokenCreation() throws IOException {
        Object o = null, o2 = null;
        Transaction tx = null;
        try {
            o2 = in.readObject();
            o = in.readObject();

            if (!((o2 instanceof MutablePair) && (o instanceof User))) {
                out.writeObject(Acknowledge.FAIL);
                return null;
            }

            User u = (User) o;
            MutablePair<Integer, String> election = (MutablePair<Integer, String>) o2;

            if (Registrar.isUserValid(u, election.getKey())) {
                try {
                    out.writeObject(Acknowledge.SUCCESS);
                    out.writeObject(ServerKeyKeeper.getBlindSignatureKeys(election.getKey()).getPublic());

                    //receive token
                    o = in.readObject();
                    Token t = (Token) o;

                    //sign it
                    t.setSignature(Crypto.signBlind(ServerKeyKeeper.getBlindSignatureKeys(election.getKey()).getPrivate(),
                            t.getRawDataToSign()));

                    //send it back
                    out.writeObject(t);
                    tx = new TokenCreation();
                    tx.setElectionID(election.getKey());
                    ((TokenCreation) tx).setUsername(u.getUsername());
                } catch (IOException e) {
                    Registrar.validateUser(u, election.getKey());
                }
            } else {
                out.writeObject(Acknowledge.FAIL);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return tx;
    }

    private Transaction voting() throws IOException {
        Object o;

        try {
            o = in.readObject();

            if (!(o instanceof MutablePair)) {
                out.writeObject(Acknowledge.FAIL);
                return null;
            }

            MutablePair<Integer, String> election = (MutablePair<Integer, String>) o;

            byte[] key = ServerKeyKeeper.getBlindSignatureKeys(election.getKey()).getPublic().getEncoded();
            out.writeObject(key);

            o = in.readObject();

            if (!(o instanceof Vote)) {
                out.writeObject(Acknowledge.FAIL);
                return null;
            }

            Vote v = (Vote) o;

            if (!Registrar.isTokenValid(v.getToken(), v.getElectionID())) {
                out.writeObject(Acknowledge.FAIL);
                return null;
            }

            PublicKey p = Crypto.decodePublicKey(v.getEncodedKey());

            if (Crypto.verifySignature(p, v.getRawDataToSign(), v.getSignature())) {
                out.writeObject(Acknowledge.SUCCESS);

                return v;
            } else {
                out.writeObject(Acknowledge.FAIL);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }
}