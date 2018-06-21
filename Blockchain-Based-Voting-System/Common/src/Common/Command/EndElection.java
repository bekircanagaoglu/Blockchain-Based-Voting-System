package Common.Command;

import org.bouncycastle.util.Arrays;

public class EndElection extends Command {
    private byte[] key;

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    @Override
    public byte[] getRawData() {
        return Arrays.concatenate(super.getRawData(), key);
    }
}
