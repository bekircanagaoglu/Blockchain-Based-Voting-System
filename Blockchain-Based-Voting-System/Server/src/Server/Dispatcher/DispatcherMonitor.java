package Server.Dispatcher;

import Common.Command.Command;
import Common.Command.GenerateNextBlock;
import Common.Crypto.Crypto;
import Common.MinerInfo;
import Common.MinerType;
import Server.ConnectionHandler.ServerConnectionHandler;
import Server.ServerKeyKeeper;
import Server.ServerMain;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class DispatcherMonitor {
    private static AtomicBoolean txInPool = new AtomicBoolean(false);
    private static final List<MinerInfo> miners = new ArrayList<>();
    private static final List<MinerInfo> audits = new ArrayList<>();
    public static final AtomicBoolean dispatchSeqWorking = new AtomicBoolean(false);

    public static synchronized boolean isTxInPool() {
        return txInPool.get();
    }

    public static void newTx() {
        txInPool.set(true);
    }

    public static void noTx() {
        txInPool.set(false);
    }

    private static Command initGNBCmd(MinerInfo minerInfo) {
        Command c = new GenerateNextBlock(minerInfo, getPingTime(minerInfo.getAddr()), DispatcherThread.getControlPeriod());
        c.setElectionID(-1);
        c.setSignature(Crypto.sign(ServerKeyKeeper.getKeys().getPrivate(), c.getRawData()));

        return c;
    }

    private static double getPingTime(InetAddress addr) {
        double t = 0d;
        final int TIMEOUT = 5000; //ms
        long startTime = System.nanoTime();

        try {
            if (addr.isReachable(TIMEOUT)) {
                long end = System.nanoTime();
                t = end - startTime;
                t = t / 1e6;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return t;
    }

    public static synchronized void dispatch() {
        int n = miners.size();

        if (n == 0)
            return;

        int index = ThreadLocalRandom.current().nextInt(n); //[0, n)
        MinerInfo minerInfo = miners.get(index);
        Command c = initGNBCmd(minerInfo);

        ServerConnectionHandler.broadcast(c);
    }

    public static synchronized void dispatchSeq() {
        int n = miners.size();

        if (n == 0)
            return;
        try {
            dispatchSeqWorking.set(true);
            do {
                for (int i = 0; i < n; ++i) {
                    MinerInfo minerInfo = miners.get(i);
                    Command c = initGNBCmd(minerInfo);

                    synchronized (ServerMain.mon) {
                        try {
                            ServerConnectionHandler.broadcast(c);
                            ServerMain.mon.wait(DispatcherThread.getControlPeriod());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } while (txInPool.get());
        } finally {
            dispatchSeqWorking.set(false);
        }
    }

    public static synchronized void addMiner(MinerInfo m) {
        if (m.getType().equals(MinerType.MINER))
            miners.add(m);
        else
            audits.add(m);
    }

    public static synchronized void removeMiner(MinerInfo m) {
        if (m.getType().equals(MinerType.MINER) ? miners.remove(m) : audits.remove(m)) {
            System.out.println("Miner/Audit " +  m.getId() + " removed");
        }
    }

    public static synchronized int getNumMiners() {
        return miners.size();
    }

    public static synchronized List<MinerInfo> getMiners() {
        return new ArrayList<>(miners);
    }

    public static synchronized List<MinerInfo> getAuditsAndMiners() {
        ArrayList<MinerInfo> r = new ArrayList<>(audits);
        r.addAll(miners);
        return r;
    }
}