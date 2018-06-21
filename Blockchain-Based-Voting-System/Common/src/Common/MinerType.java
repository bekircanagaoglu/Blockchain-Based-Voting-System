package Common;

import java.io.Serializable;

public enum MinerType implements Serializable {
    AUDIT(0), MINER(1);

    private final int val;

    MinerType(int val) {
        this.val = val;
    }

    public int value() {
        return val;
    }
}
