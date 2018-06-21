package Server.ResponseMonitor;

import Common.Crypto.Crypto;
import Common.Response.GetVoteResponse;
import Common.Transaction.Vote;
import Common.Acknowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class GetVoteResponseMonitor {

    private static final List<GetVoteResponse> responses = new ArrayList<>();
    private static final Lock lock = new ReentrantLock();
    private static final Condition cond = lock.newCondition();

    public static GetVoteResponse waitResponse(byte[] key) {
        lock.lock();
        GetVoteResponse result = null;
        try {
            while ((result = getResponse(key)) == null)
                cond.await();

            if (result.getResponse() instanceof Vote) {
                Vote v = (Vote) result.getResponse();

                if (!Crypto.verifySignature(Crypto.decodePublicKey(v.getEncodedKey()), v.getRawDataToSign(), v.getSignature()))
                    result.setResponse(Acknowledge.FAIL);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        return result;
    }

    public static void addResponse(GetVoteResponse r) {
        lock.lock();
        try {
            responses.add(r);
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private static GetVoteResponse getResponse(byte[] key) {
        lock.lock();
        GetVoteResponse r = null;

        try {
            ListIterator it = responses.listIterator();

            while (it.hasNext()) {
                GetVoteResponse tmp = (GetVoteResponse) it.next();

                if (Arrays.equals(tmp.getKey(), key)) {
                    it.remove();
                    r = tmp;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }

        return r;
    }
}
