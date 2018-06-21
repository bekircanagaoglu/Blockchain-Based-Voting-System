package Common.Transaction;

import org.bouncycastle.util.Arrays;

import java.nio.ByteBuffer;
import java.sql.Timestamp;


public  abstract class Transaction implements ITransaction{

    private int electionID;
    private byte[] serversSignature;
    private Timestamp timestamp;

    @Override
    public int getElectionID() {
        return electionID;
    }

    public void setElectionID(int electionID) {
        this.electionID = electionID;
    }

    @Override
    public byte[] getRawDataToSign() {
        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE / 8);
        buf.putInt(getElectionID());

        return buf.array();
    }

    public byte[] getRawDataWithTimestampToSign() {
        ByteBuffer buf = ByteBuffer.allocate(Long.SIZE / 8);
        buf.putLong(timestamp.getTime());
        return Arrays.concatenate(getRawDataToSign(), buf.array());
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void timestamp() {
        this.timestamp = new Timestamp(System.currentTimeMillis());
    }

    public byte[] getServersSignature() {
        return serversSignature;
    }

    public void setServersSignature(byte[] serversSignature) {
        this.serversSignature = serversSignature;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "electionID=" + electionID +
                ", timestamp=" + timestamp +
                "}    ";
    }

    @Override
    abstract public boolean equals(Object obj);
}
