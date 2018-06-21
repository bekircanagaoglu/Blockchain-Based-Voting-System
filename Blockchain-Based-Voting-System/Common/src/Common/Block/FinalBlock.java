package Common.Block;

import Common.Command.EndElection;
import org.bouncycastle.util.Arrays;

public class FinalBlock extends Block {
    private final EndElection e;

    public FinalBlock(EndElection e) {
        this.e = e;
    }

    @Override
    protected byte[] getRawBlock() {
        return Arrays.concatenate(super.getRawBlock(), e.getRawData());
    }

    public EndElection getEECmd() {
        return e;
    }

    @Override
    public String toString() {
        return "Final block\n" + super.toString();
    }
}
