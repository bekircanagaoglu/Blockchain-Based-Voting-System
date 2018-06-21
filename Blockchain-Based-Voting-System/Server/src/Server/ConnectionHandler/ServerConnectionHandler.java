package Server.ConnectionHandler;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerConnectionHandler {

    private static final List<Socket> peers  = new LinkedList<>();
    private static Queue<Serializable> q = new ConcurrentLinkedQueue<>();
    private static final Map<Socket, ObjectOutputStream> objOutStreams = new HashMap<>();
    private static final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private static final String PATH_Q = "ServerConnectionHandlerQ.tmp";
    private static Thread handler;

    public static void init() {
        handler = new Thread(()->{
            for(;;) {
                if (!anyPeers()) {
                    synchronized (peers) {
                        if (!anyPeers()) {
                            try {
                                peers.wait();
                            } catch (InterruptedException e) {}
                        }
                    }
                }

                while (!q.isEmpty()) {
                    try {
                        rwlock.readLock().lock();
                        Serializable data = q.poll();
                        for (ObjectOutputStream o : objOutStreams.values()) {
                            try {
                                synchronized (o) {
                                    o.writeObject(data);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } finally {
                        rwlock.readLock().unlock();
                    }
                }

                synchronized (q) {
                    if (q.isEmpty()) {
                        try {
                            q.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        handler.start();
    }

    public static void restoreQ() {
        ObjectInputStream inp = null;
        try {
            inp = new ObjectInputStream(new FileInputStream(PATH_Q));
            q = (Queue<Serializable>) inp.readObject();
            inp.close();

            if (anyPeers())
                broadcast(null);

        } catch (IOException | ClassNotFoundException e) {
        }
    }

    public static void addPeer(Socket s, ObjectOutputStream out) {
        try {
            rwlock.writeLock().lock();
            peers.add(s);
            objOutStreams.put(s, out);

            if (peers.size() == 1) {
                synchronized (peers) {
                    peers.notify();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    protected static boolean anyPeers() {
        try {
            rwlock.readLock().lock();
            return !peers.isEmpty();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static void removePeer(Socket s) {
        try {
            rwlock.writeLock().lock();
            peers.remove(s);
            objOutStreams.remove(s);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public static boolean sendObjectToNthPeer(Serializable data, int i) {
        try {
            rwlock.readLock().lock();
            if (peers.size() <= i)
                return false;

            try {
                ObjectOutputStream tmp = objOutStreams.get(peers.get(i));
                synchronized (tmp) {
                    tmp.writeObject(data);
                }
                return true;
            } catch (IOException e) {
                System.err.println("IOException" + e.getMessage());
            }
            return false;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static InetAddress[] getPeers() {
        try {
            rwlock.readLock().lock();
            InetAddress[] tmp = new InetAddress[peers.size()];
            int i = 0;
            for (Socket s : peers)
                tmp[i++] = s.getInetAddress();

            return tmp;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static synchronized void broadcast(Serializable data) {
        q.add(data);

        synchronized (q) {
            q.notify();
        }

        try {
            Common.Common.writeTofile((Serializable) q, PATH_Q);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
