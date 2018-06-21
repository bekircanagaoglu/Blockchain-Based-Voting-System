package Miner.BlockChain;

import Common.ByteArrayWrapper;
import Common.Transaction.Transaction;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  note: this class cited from course "Bitcoin and Crypto Currency Technologies" from Coursera
 */
public class TransactionPool implements Serializable {

    private final Map<ByteArrayWrapper, Transaction> H;

    public TransactionPool() {
        H = new ConcurrentHashMap<>();
    }

    public TransactionPool(TransactionPool txPool) {
        H = new HashMap<>(txPool.H);
    }

    public void addTransaction(Transaction tx) {
        ByteArrayWrapper hash = new ByteArrayWrapper(tx.getRawDataToSign());
        H.put(hash, tx);
    }

    public void removeTransaction(byte[] txHash) {
        ByteArrayWrapper hash = new ByteArrayWrapper(txHash);
        H.remove(hash);
    }

    public Transaction getTransaction(byte[] txHash) {
        ByteArrayWrapper hash = new ByteArrayWrapper(txHash);
        return H.get(hash);
    }

    public List<Transaction> getTransactions() {
        List<Transaction> T = new LinkedList<>(H.values());
        return T;
    }
}
