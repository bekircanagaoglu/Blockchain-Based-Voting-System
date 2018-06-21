package Common.Block;

import Common.Command.StartElection;
import org.bouncycastle.util.Arrays;

public class GenesisBlock extends Block {
    private final StartElection e;

    public GenesisBlock(StartElection e) {
        this.e = e;
    }

    @Override
    protected byte[] getRawBlock() {
        return Arrays.concatenate(super.getRawBlock(), e.getRawData());
    }

    @Override
    public String toString() {
        return  e.getElection() + "\n" +
                super.toString();
    }
}
