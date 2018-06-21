package Miner.BlockChain;

import Common.Block.Block;
import Common.Block.BlockFrame;
import Common.Block.FinalBlock;
import Common.Block.GenesisBlock;
import Common.Command.EndElection;
import Common.Common;
import Common.Crypto.Crypto;
import Common.IdNamePair;
import Common.Election;
import Common.Command.StartElection;
import Common.Transaction.TokenCreation;
import Common.Transaction.Transaction;
import Common.Transaction.Vote;
import Miner.ConnectionHandler.MinerConnectionHandler;
import Miner.MinerDBHandler;
import Miner.MinerKeyKeeper;
import org.apache.commons.lang3.tuple.MutablePair;

import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockChainHandler {

    //map election id to attributes
    final Map<Integer, BlockChain> blockChains;
    final Map<Integer, List<IdNamePair>> candidates;

    public BlockChainHandler() {
        blockChains = new ConcurrentHashMap<>();
        candidates = new HashMap<>();
    }

    public void startElection(StartElection e) {

        if (blockChains.containsKey(e.getElectionID()))
            return;

        MinerKeyKeeper.addBlindSigKey(e.getElectionID(), Crypto.decodePublicKey(e.getBlindSigKey()));

        Block block = new GenesisBlock(e);
        block.setId(0);
        block.setElectionInfo(new MutablePair<>(e.getElectionID(), e.getName()));
        block.updateHash();
        blockChains.put(e.getElectionID(), new BlockChain(block));
        candidates.put(e.getElectionID(), e.getCandidates());
    }

    public void startElectionAndAddDB(StartElection e) {
        MinerDBHandler.addElection(e);
        startElection(e);
    }

    public void addTx(Transaction tx) {
        getBlockchain(tx.getElectionID()).addTransaction(tx);
        MinerDBHandler.updateTxPool(tx.getElectionID(), getBlockchain(tx.getElectionID()).getTransactionPool());
    }

    public boolean generateBlock(MutablePair<Integer, String> electionInfo, AtomicBoolean timesUp) {

        BlockChain blockChain = getBlockchain(electionInfo.getKey());

        if (blockChain.getTransactionPool().getTransactions().size() == 0)
            return false;

        List<Transaction> txs = blockChain.getTransactionPool().getTransactions();
        List<Transaction> validTxs = TxHandler.handleTxs(txs, blockChain.getUsedTokens(), blockChain.getUsedUsernames(), timesUp);

        boolean allTxInBlock = !timesUp.get();

        if (validTxs.isEmpty())
            return false;

        //generate block
        Block last = blockChain.getLastBlock();
        Block block = new Block(validTxs);
        block.setPrevBlockHash(last.getHash());
        block.setElectionInfo(electionInfo);
        block.setId(last.getId() + 1);
        block.updateHash();

        BlockFrame b = new BlockFrame(block, Crypto.sign(MinerKeyKeeper.getKeyPair().getPrivate(),
                block.getHash()), allTxInBlock);
        MinerConnectionHandler.sendObject(b);

        blockChain.addVerifiedBlock(b.getBlock());
        MinerDBHandler.addBlock(b.getBlock().getElectionInfo().getKey(), b.getBlock(),
                getBlockchain(b.getBlock().getElectionInfo().getKey()).getTransactionPool());

        for (Transaction tx : txs)
            blockChain.getTransactionPool().removeTransaction(tx.getRawDataToSign());

        return true;
    }

    public boolean addBlock(BlockFrame b) {

        try {
            if (MinerKeyKeeper.getLastMinersPK() != null
                    && Crypto.verifySignature(MinerKeyKeeper.getLastMinersPK(), b.getBlock().getHash(), b.getMinerSignature())
                    && getBlockchain(b.getBlock().getElectionInfo().getKey()).addBlock(b.getBlock())) {
                MinerConnectionHandler.sendObject(b);
                MinerDBHandler.addBlock(b.getBlock().getElectionInfo().getKey(), b.getBlock(),
                        getBlockchain(b.getBlock().getElectionInfo().getKey()).getTransactionPool());

                return true;
            }
        } catch (Exception e) {}

        return false;
    }

    public BlockChain getBlockchain(Integer electionId) {
        return blockChains.get(electionId);
    }

    public void endElection(EndElection c) {
        Block b = new FinalBlock(c);
        Block last = blockChains.get(c.getElectionID()).getLastBlock();
        b.setPrevBlockHash(last.getHash());
        b.setId(last.getId() + 1);
        b.setElectionInfo(last.getElectionInfo());
        b.updateHash();
        MinerDBHandler.addBlock(c.getElectionID(), b,
                blockChains.get(c.getElectionID()).getTransactionPool());
        MinerDBHandler.removeElection(c.getElectionID());
        blockChains.remove(c.getElectionID());
        candidates.remove(c.getElectionID());
    }

    private static int[] countVotes(Election e, List<Vote> votes, PrivateKey key, int lo, int hi) {
        int[] count = new int[e.getCandidates().size()];

        for (int i = lo; i < hi; ++i) {
            Vote v = votes.get(i);
            MutablePair<List<Integer>, Integer> retval = Common.openVote(v.getVote(), key);
            int chksm = 0;

            if (!((retval.left.size() < e.getNvotes_l()) || (retval.left.size() > e.getNvotes_h()))) {
                for (Integer j : retval.left)
                    chksm += e.getCandidates().get(j).getName().hashCode();

                if (chksm == retval.right.intValue()) {
                    for (int j = 0; j < retval.left.size(); ++j) {
                        if (retval.left.lastIndexOf(retval.left.get(j)) != j) {
                            break;
                        }
                        count[retval.left.get(j)]++;
                    }
                }
            }
        }
        return count;
    }

    public static String countVotes(Election e, List<Vote> votes, PrivateKey key) {
        VoteCounter cnt = new VoteCounter(e, votes, key, 0, votes.size());
        int[] count = ForkJoinPool.commonPool().invoke(cnt);

        StringBuilder sb = new StringBuilder();
        sb.append(votes.size()).append(" vote(s) casted\n");

        for (int i = 0; i < e.getCandidates().size(); ++i)
            sb.append("Vote: ").append(Integer.toString(count[i])).append(" IdNamePair: ").append(e.getCandidates().get(i)).append("\n");

        return sb.toString();

    }

    public void printElectionResult(Election e, PrivateKey key) {
        if (e == null)
            return;

        int tokens = 0;
        List<Vote> votes = new ArrayList<>();

        List<Block> blocks = getBlockchain(e.getId()).getAllBlocks();

        for (Block b : blocks) {
            for (Transaction tx : b.getTxs()) {
                if (tx instanceof Vote)
                    votes.add((Vote) tx);
                else if (tx instanceof TokenCreation)
                    tokens++;
            }
        }

        System.out.println(tokens + " token(s) generated");
        System.out.println(countVotes(e, votes, key));
    }

    public List<MutablePair<Integer, String>> getElectionInfos() {

        List<MutablePair<Integer, String>> infos = new ArrayList<>();

        for (BlockChain b : blockChains.values())
            infos.add(b.getLastBlock().getElectionInfo());

        return infos;
    }

    public Vote getVote(int id, byte[] key) {
        BlockChain b = blockChains.get(id);
        if (b == null)
            return null;

        List<Block> blocks = b.getAllBlocks();

        for (Block block : blocks) {
            Vote v = findVote(block, key);
            if (v != null)
                return v;
        }

        return null;
    }

    public static Vote findVote(Block block, byte[] key) {
        List<Transaction> txs = block.getTxs();

        for (Transaction tx : txs) {
            if (tx instanceof Vote)
                if (Arrays.equals(((Vote) tx).getEncodedKey(), key))
                    return ((Vote) tx);
        }
        return null;
    }

    private static class VoteCounter extends RecursiveTask<int[]> {
        private Election e;
        private List<Vote> votes;
        private PrivateKey key;
        private int lo, hi;
        private int THRESHOLD = 175;

        public VoteCounter(Election e, List<Vote> votes, PrivateKey key, int lo, int hi) {
            this.e = e;
            this.votes = votes;
            this.key = key;
            this.lo = lo;
            this.hi = hi;
        }
        @Override
        protected int[] compute() {
            if (hi - lo < THRESHOLD) {
                return countVotes(e, votes, key, lo, hi);
            } else {
                int mid = (hi + lo) / 2;
                VoteCounter left = new VoteCounter(e, votes, key, lo, mid),
                        right = new VoteCounter(e, votes, key, mid, hi);
                right.fork();
                int[] r1 = left.compute();
                int[] r2 = right.join();

                for (int i = 0; i < r1.length; ++i)
                    r1[i] += r2[i];
                return r1;
            }
        }
    }
}
