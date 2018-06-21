package Common.Block;

import java.io.Serializable;

public class BlockFrame implements Serializable {
    private final Block block;
    private final byte[] minerSignature;
    private boolean allTxsInBlock;

    public BlockFrame(Block block, byte[] minerSignature, boolean allTxsInBlock) {
        this.block = block;
        this.minerSignature = minerSignature;
        this.allTxsInBlock = allTxsInBlock;
    }

    public Block getBlock() {
        return block;
    }

    public boolean isAllTxsInBlock() {
        return allTxsInBlock;
    }

    public byte[] getMinerSignature() {
        return minerSignature;
    }
}
