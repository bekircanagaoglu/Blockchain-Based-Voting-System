package Server.Dispatcher;

import java.util.concurrent.atomic.AtomicInteger;

public class DispatcherThread implements Runnable {

    static private AtomicInteger controlPeriod = new AtomicInteger((int) 3e3);

    @Override
    public void run() {
        for (;;) {
            try {
                Thread.sleep(getControlPeriod());
            } catch (InterruptedException e) {
            }

            if (DispatcherMonitor.isTxInPool())
                DispatcherMonitor.dispatch();
        }
    }

    public static int getControlPeriod() {
        return controlPeriod.get();
    }

    public static void setControlPeriod(int controlPeriod) {
        DispatcherThread.controlPeriod.set(controlPeriod);
    }
}
