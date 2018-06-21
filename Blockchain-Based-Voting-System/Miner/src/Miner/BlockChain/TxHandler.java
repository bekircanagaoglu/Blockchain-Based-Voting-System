package Miner.BlockChain;

import Common.Token.Token;
import Miner.MinerKeyKeeper;
import Common.Crypto.Crypto;
import Common.Transaction.*;


import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TxHandler {

    private static List<Transaction> handleTxs(List<Transaction> txs, Set<Token> usedTokens, Set<String> usedUsernames,
                                               AtomicBoolean timesUp, int lo, int hi) {
        List<Transaction> result = new ArrayList<>();

        for (int i = lo; i < hi; ++i) {
            Transaction tx = txs.get(i);
            if (timesUp.get())
                break;

            if (!Crypto.verifySignature(MinerKeyKeeper.getServerPublicKey(), tx.getRawDataWithTimestampToSign(),
                    tx.getServersSignature())) {
//                System.err.println("Transaction signature is invalid: " + tx.toString());
                continue;
            }

            if (tx instanceof TokenCreation) {
                synchronized (usedUsernames) {
                    if (!usedUsernames.contains(((TokenCreation) tx).getUsername())) {
                        result.add(tx);
                        usedUsernames.add(((TokenCreation) tx).getUsername());
                    }
                }
            } else if (tx instanceof Vote) {
                PublicKey pk = Crypto.decodePublicKey(((Vote) tx).getEncodedKey());

                if (Crypto.verifySignature(MinerKeyKeeper.getBlindSigKey(tx.getElectionID()),
                        ((Vote) tx).getToken().getData(), ((Vote) tx).getToken().getSignature())
                        && Crypto.verifySignature(pk, tx.getRawDataToSign(), ((Vote) tx).getSignature())
                        && Arrays.equals(((Vote) tx).getToken().getData(),
                        Crypto.calcSHA256sum(((Vote) tx).getEncodedKey()))
                        )
                    synchronized (usedTokens) {
                        if (!usedTokens.contains(((Vote) tx).getToken())) {
                            result.add(tx);
                            usedTokens.add(((Vote) tx).getToken());
                        }
                    }
            }
        }
        return result;
    }

    public static List<Transaction> handleTxs(List<Transaction> txs, Set<Token> usedTokens, Set<String> usedUsernames, AtomicBoolean timesUp) {
        return ForkJoinPool.commonPool().invoke(new ParTxHandler(txs, new HashSet<>(usedTokens), new HashSet<>(usedUsernames), timesUp, 0, txs.size()));
    }

    public static List<Transaction> handleTxs(List<Transaction> txs, Set<Token> usedTokens, Set<String> usedUsernames) {
        return handleTxs(txs, usedTokens, usedUsernames, new AtomicBoolean(false));
    }

    private static class ParTxHandler extends RecursiveTask<List<Transaction>> {
        private List<Transaction> txs;
        private Set<Token> usedTokens;
        private Set<String> usedUsernames;
        private AtomicBoolean timesUp;
        private int lo, hi;
        private final int THRESHOLD = 175;

        public ParTxHandler(List<Transaction> txs, Set<Token> usedTokens, Set<String> usedUsernames, AtomicBoolean timesUp, int lo, int hi) {
            this.txs = txs;
            this.usedTokens = usedTokens;
            this.usedUsernames = usedUsernames;
            this.timesUp = timesUp;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        protected List<Transaction> compute() {
            if (hi - lo < THRESHOLD) {
                return handleTxs(txs, usedTokens, usedUsernames, timesUp, lo, hi);
            } else {
                int mid = (hi + lo) / 2;
                ParTxHandler left = new ParTxHandler(txs, usedTokens, usedUsernames, timesUp, lo, mid);
                ParTxHandler right = new ParTxHandler(txs, usedTokens, usedUsernames, timesUp, mid, hi);
                right.fork();
                List<Transaction> retval = left.compute();
                retval.addAll(right.join());
                return retval;
            }
        }
    }
}

