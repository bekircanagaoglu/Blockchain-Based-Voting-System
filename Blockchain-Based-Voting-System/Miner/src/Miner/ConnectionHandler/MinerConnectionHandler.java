package Miner.ConnectionHandler;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class MinerConnectionHandler  {
    private static Socket s;
    private static ObjectOutputStream out;
    private static Thread handler;
    private static Queue<Serializable> q = new ConcurrentLinkedQueue<>();

    public static void init() {
        handler = new Thread(()->{
            for (;;) {
                while (!q.isEmpty()) {
                    try {
                        out.writeObject(q.poll());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                synchronized (q) {
                    if (q.isEmpty()) {
                        try {
                            q.wait();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }
        });
        handler.start();
    }

    public static void setPeer(Socket s, ObjectOutputStream out) {
        MinerConnectionHandler.s = s;
        MinerConnectionHandler.out = out;
    }

    public static void sendObject(Serializable o) {
        q.add(o);
        synchronized (q) {
            q.notify();
        }
    }
}
