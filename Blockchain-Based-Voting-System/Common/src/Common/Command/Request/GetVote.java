package Common.Command.Request;


import Common.Command.Command;

import java.util.Arrays;

public class GetVote extends Command {
    private byte[] key;

    public byte[] getKey() {
        return key;
    }

    @Override
    public byte[] getRawData() {
        return org.bouncycastle.util.Arrays.concatenate(super.getRawData(), getKey());
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetVote that = (GetVote) o;
        return Arrays.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }
}
