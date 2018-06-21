package Common.Command;

import java.io.Serializable;
import java.nio.ByteBuffer;

public abstract class Command implements Serializable {

    private int electionID;
    private byte[] signature;

    public Command() {
        electionID = -1;
    }

    public byte[] getRawData() {
        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE / 8);
        buf.putInt(getElectionID());
        return buf.array();
    }

    public int getElectionID() {
        return electionID;
    }

    public void setElectionID(int electionID) {
        this.electionID = electionID;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "Command{" +
                ", electionID=" + electionID +
                '}';
    }
}
