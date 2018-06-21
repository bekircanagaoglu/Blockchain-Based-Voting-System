package Miner.BlockChain;

import Common.Block.Block;
import Common.Token.Token;
import Common.Transaction.TokenCreation;
import Common.Transaction.Transaction;
import Common.Transaction.Vote;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class BlockChain {

    private TransactionPool transactionPool;
    private final Set<Token> usedTokens;
    private final Set<String> usedUsernames;
    private final AtomicReference<Node> last;

    public BlockChain(Block genesisBlock) {
        transactionPool = new TransactionPool();
        usedTokens = new HashSet<>();
        usedUsernames = new HashSet<>();
        last = new AtomicReference<>(new Node(genesisBlock));

        System.out.println("\nGenesis block:");
        System.out.println(last.get().block);
    }

    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    public void setTransactionPool(TransactionPool transactionPool) {
        this.transactionPool = transactionPool;
    }

    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }

    public Block getLastBlock() {
        return last.get().block;
    }

    public List<Block> getAllBlocks() {
        List<Block> retval = new ArrayList<>();

        for (Node n = last.get(); n != null; n = n.prev)
            retval.add(n.block);

        return retval;
    }

    public boolean addBlock(Block block) {
        List<Transaction> validTxs = BlockChain.tryAddBlock(last.get().block, block, usedTokens, usedUsernames);

        if (validTxs == null)
            return false;

        return addVerifiedBlock(block);
    }

    public boolean addVerifiedBlock(Block block) {
        List<Transaction> validTxs = block.getTxs();

        //add block
        last.set(new Node(last.get(), block));

        //update transaction pool
        for (Transaction tx : validTxs) {
            if (tx instanceof TokenCreation)
                usedUsernames.add(((TokenCreation) tx).getUsername());
            else if (tx instanceof Vote)
                usedTokens.add(((Vote) tx).getToken());
            transactionPool.removeTransaction(tx.getRawDataToSign());
        }

        System.out.println("\nNew block:");
        System.out.println(last.get().block);

        return true;
    }

    public static List<Transaction> tryAddBlock(Block prev, Block block, Set<Token> usedTokens, Set<String> usedUsernames) {
        //hash/id control
        if ((block == null) || (block.getPrevBlockHash() == null))
            return null;

        if (block.getId() - prev.getId() != 1)
            return null;

        if (!Arrays.equals(block.getPrevBlockHash(), prev.getHash()))
            return null;

        //transaction control
        List<Transaction> validTxs = TxHandler.handleTxs(block.getTxs(), usedTokens, usedUsernames);

        if (validTxs.size() != block.getTxs().size())
            return null;

        return validTxs;
    }

    public Set<Token> getUsedTokens() {
        return usedTokens;
    }

    public Set<String> getUsedUsernames() {
        return usedUsernames;
    }

    public MutablePair<Object, Block> reverseIterate(Object pos) {
        if (pos == null) {
            Node n = last.get();
            return new MutablePair<>(n.prev, n.block);
        }
        return new MutablePair<>(((Node) pos).prev, ((Node) pos).block);
    }

    private static class Node {
        private final int height;
        private final Node prev;
        private final Block block;

        public Node(Block block) {
            this(null, block);
        }

        public Node(Node prev, Block block) {
            this.height = (prev == null) ? 0 : (prev.height + 1);
            this.prev = prev;
            this.block = block;
        }
    }
}
