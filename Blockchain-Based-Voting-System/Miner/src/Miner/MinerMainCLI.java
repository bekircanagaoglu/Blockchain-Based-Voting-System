package Miner;

import Common.Acknowledge;
import Common.Block.BlockFrame;
import Common.Command.Command;
import Common.Command.StartElection;
import Common.MinerInfo;
import Common.MinerType;
import Common.TLS.SSLUtil;
import Common.Transaction.Transaction;
import Miner.BlockChain.BlockChainHandler;
import Miner.CommandHandler.AuditCommandHandler;
import Miner.CommandHandler.CommandHandler;
import Miner.ConnectionHandler.MinerConnectionHandler;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MinerMainCLI {
    private static MinerType type;
    private static ObjectInputStream in;
    private static ObjectOutputStream out;
    private static CommandHandler commandHandler;
    private static BlockChainHandler b;
    private static final Object mon = new Object();
    private static SSLContext sc = null;
    private static SSLSocketFactory sslSocketFactory = null;

    public static void main(String[] args) {
        MinerDBHandler.init();
        MinerConnectionHandler.init();
        type = MinerType.MINER;

        try {
            MinerKeyKeeper.readKeyPair("pub.key", "priv.key");
        } catch (IOException e) {
            MinerKeyKeeper.generateKeyPair();
        }
        try {
            sc = SSLUtil.createClientSSLContext();
        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException | KeyManagementException e) {
            e.printStackTrace();
        }
        sslSocketFactory = sc.getSocketFactory();
        initMiner(null, args.length == 0 ? "localhost" : args[0], 4001);
    }


    public static void initMiner(SSLSocket s, String host, int port) {

        boolean init = true, connected = false;

        for (;;) {
            try {
                if (s == null) {
                    s = (SSLSocket) sslSocketFactory.createSocket(host, port);
                    s.startHandshake();
                }
                out = new ObjectOutputStream(s.getOutputStream());
                in = new ObjectInputStream(s.getInputStream());
                connected = true;
                MinerConnectionHandler.setPeer(s, out);
                MinerKeyKeeper.setServerPublicKey((PublicKey) in.readObject());

                Object o = in.readObject();
                if (init) {
                    Queue<Object> q = new LinkedList<>();

                    if (o.equals(true)) {
                        Set<Integer> electionIDs = (Set<Integer>) in.readObject();
                        InetAddress[] peers = (InetAddress[]) in.readObject();
                        SSLSocket finalS = s;
                        new Thread(() ->
                        {
                            b = new BlockChainHandler();
                            commandHandler = new CommandHandler(b);

                            List<StartElection> l = MinerDBHandler.getStartElectionCmds();
                            for (StartElection se : l) {
                                if (!electionIDs.contains(se.getElectionID())) {
                                    MinerDBHandler.deleteElection(se.getElectionID());
                                    l.remove(se);
                                } else {
                                    commandHandler.startElection(se);
                                    MinerDBHandler.importBlocks(se.getElectionID(), b.getBlockchain(se.getElectionID()));
                                }
                            }

                            P2P.sync(peers, finalS.getInetAddress(), commandHandler, electionIDs);

                            MinerConnectionHandler.sendObject(Acknowledge.SUCCESS);
                        }).start();

                        while (true) {
                            Object tmp = in.readObject();
                            if (tmp.equals(Acknowledge.SUCCESS)) //blocks are received
                                break;
                            ((LinkedList<Object>) q).push(tmp);
                        }
                    } else {
                        b = new BlockChainHandler();
                        commandHandler = type.equals(MinerType.MINER) ? new CommandHandler(b) : new AuditCommandHandler(b);
                    }

                    new Thread(() -> P2P.handleRequests(commandHandler, mon)).start();

                    while (!q.isEmpty())
                        handler(((LinkedList<Object>) q).pop());
                }

                Queue<Object> q = new ConcurrentLinkedQueue<>();

                new Thread(()->{
                    for (;;) {
                        while (!q.isEmpty()) {
                            try {
                                handler(q.poll());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        synchronized (q) {
                            try {
                                if (q.isEmpty())
                                    q.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();

                for (;;) {
                    o = in.readObject();
                    q.add(o);
                    synchronized (q) {
                        q.notify();
                    }
                }
            } catch (IOException e) {
                if (!connected) {
                    System.out.println("Couldn't connected to server: " + e.getMessage());
                    System.exit(0);
                } else {
                    try {
                        s = null;
                        synchronized (mon) {
                            System.out.println("Server disconnected. Waiting...");
                            mon.wait();
                            host = P2P.getHost();
                            init = false;
                        }
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handler(Object o) {
        System.out.println(o);
        synchronized (b) {
            try {
                if (o instanceof Command) {
                    System.out.println(o);
                    commandHandler.handleCommand((Command) o);
                } else if (o instanceof Transaction) {
                    b.addTx((Transaction) o);
                } else if (o instanceof BlockFrame) {
                    b.addBlock((BlockFrame) o);
                } else if (o instanceof MinerInfo) {
                    initMinerInfo(o);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    private static void initMinerInfo(Object o) {
        System.out.println("I'm miner " + ((MinerInfo) o).getId());

        if (type.equals(MinerType.AUDIT)) {
            ((MinerInfo) o).setType(MinerType.AUDIT);

        } else if (type.equals(MinerType.MINER)) {
            ((MinerInfo) o).setType(MinerType.MINER);
            ((MinerInfo) o).setPublicKey(MinerKeyKeeper.getKeyPair().getPublic());
        }

        MinerConnectionHandler.sendObject((Serializable) o);
        commandHandler.setMinerInfo((MinerInfo) o);
    }
}