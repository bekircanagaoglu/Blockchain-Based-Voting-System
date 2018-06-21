package Server.ConnectionHandler.ConnectionHandlerThread;

import Common.Acknowledge;
import Common.Block.BlockFrame;
import Common.Response.GetVoteResponse;
import Common.MinerInfo;
import Common.MinerType;
import Common.TLS.SSLUtil;
import Server.ConnectionHandler.ServerConnectionHandler;
import Server.ServerDBHandler;
import Server.Dispatcher.DispatcherMonitor;
import Server.Registrar.Registrar;
import Server.ResponseMonitor.GetVoteResponseMonitor;
import Server.ServerKeyKeeper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;

import static Server.ServerMain._miners;
import static Server.ServerMain.mon;

public class MinerConnectionThread implements Runnable {

    private static final int PORT = 4001;

    @Override
    public void run() {
        int numMiners = 0;
        try {
            SSLContext sc = SSLUtil.createServerSSLContext();
            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(PORT);

            synchronized (mon) {
                mon.notify();
            }

            for (; ; ) {
                SSLSocket s = (SSLSocket) serverSocket.accept();

                MinerInfo info = new MinerInfo(numMiners++);

                new Thread(new MinerHandler(s, info)).start();

                System.out.println("Miner connected");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class MinerHandler implements Runnable {

    private final Socket s;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;
    private MinerInfo info;

    public MinerHandler(Socket s, MinerInfo info) throws IOException {
        this.s = s;
        out = new ObjectOutputStream(s.getOutputStream());
        in = new ObjectInputStream(s.getInputStream());
        this.info = info;
    }

    private void clean() {
        ServerConnectionHandler.removePeer(s);
        DispatcherMonitor.removeMiner(info);
        try {
            in.close();
        } catch (IOException e) {
        }
        try {
            out.close();
        } catch (IOException e) {
        }
        try {
            s.close();
        } catch (IOException e) {
        }
    }

    @Override
    public void run() {
        Object o;

        try {
            out.writeObject(ServerKeyKeeper.getKeys().getPublic());

            if (Registrar.getNumElections() > 0) {
                out.writeObject(true);

                out.writeObject(Registrar.getElectionIDs());

                //send peers
                InetAddress[] peers = ServerConnectionHandler.getPeers();
                out.writeObject(peers);
            } else {
                out.writeObject(false);
            }

            out.writeObject(info);
            ServerConnectionHandler.addPeer(s, out);

            for (;;) {
                o = in.readObject();

                if (o instanceof BlockFrame) {
                    if (((BlockFrame) o).getBlock() != null) {
                        Object finalO = o;
                        ServerConnectionHandler.broadcast((Serializable) finalO);
                    }
                    if (DispatcherMonitor.dispatchSeqWorking.get()) {
                        synchronized (mon) {
                            mon.notify();
                        }
                    }
                    if (((BlockFrame) o).isAllTxsInBlock())
                        DispatcherMonitor.noTx();
                } else if (o instanceof GetVoteResponse) {
                    GetVoteResponseMonitor.addResponse((GetVoteResponse) o);
                } else if (o instanceof MinerInfo) {
                    info = (MinerInfo) o;
                    info.setAddr(s.getInetAddress());
                    _miners.add(info.toString());
                    ServerDBHandler.addMiner(info.getId(), info.getAddr());
                    DispatcherMonitor.addMiner(info);
                } else if (o.equals(Acknowledge.SUCCESS)) {
                    synchronized (out) {
                        out.writeObject(Acknowledge.SUCCESS);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Miner/Audit " + info.getId() + " disconnected");
            _miners.remove(info.toString());
            ServerDBHandler.removeMiner(info.getId());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            clean();
        }
    }
}

