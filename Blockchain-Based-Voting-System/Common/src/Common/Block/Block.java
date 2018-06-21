package Common.Block;

import Common.Transaction.Transaction;
import org.apache.commons.lang3.tuple.MutablePair;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static Common.Common.bytesToHex;

/**
 *  note: this class cited from course "Bitcoin and Crypto Currency Technologies" from Coursera with slight changes
 */
public class Block implements Serializable {

    private int id;
    private MutablePair<Integer, String> electionInfo;
    private byte[] hash;
    private byte[] prevBlockHash;
    private final List<Transaction> txs;

    public Block() {
        txs = new ArrayList<>();
    }

    public Block(List<Transaction> t) { txs = t; }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public byte[] getPrevBlockHash() {
        return prevBlockHash;
    }

    public void setPrevBlockHash(byte[] prevBlockHash) {
        this.prevBlockHash = prevBlockHash;
    }

    public List<Transaction> getTxs() {
        return txs;
    }

    public void addTransaction(Transaction tx) {
        txs.add(tx);
    }

    public void updateHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(getRawBlock());
            hash = md.digest();
        } catch (NoSuchAlgorithmException x) {
            x.printStackTrace(System.err);
        }
    }

    protected byte[] getRawBlock() {
        List<Byte> rawBlock = new ArrayList<>();
        byte[] retval = null;

        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.SIZE / 8);
        byteBuffer.putInt(id);

        for (byte b : byteBuffer.array())
            rawBlock.add(b);

        byteBuffer = ByteBuffer.allocate(Integer.SIZE / 8);
        byteBuffer.putInt(electionInfo.getKey());

        for (byte b : byteBuffer.array())
            rawBlock.add(b);

        try {
            for (byte b : electionInfo.getValue().getBytes("UTF-8"))
                rawBlock.add(b);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (prevBlockHash != null)
            for (byte b : prevBlockHash)
                rawBlock.add(b);

        for (Transaction tx : txs) {
            byte[] buf = tx.getRawDataToSign();

            for (byte b : buf)
                rawBlock.add(b);
        }

        retval = new byte[rawBlock.size()];

        for (int i = 0; i < rawBlock.size(); ++i)
            retval[i] = rawBlock.get(i);

        return retval;
    }

    @Override
    public String toString() {

        StringBuilder txList = new StringBuilder();

        for (Transaction tx : txs)
            txList.append(tx).append("\n");

        return  "Block{" +
                "\nid=" + id +
                "\nelection=" + electionInfo +
                "\nprevBlockHash=" + bytesToHex(prevBlockHash) +
                "\nhash=" + bytesToHex(hash) +
                "\ntxs=" + txList.toString() +
                "}";
    }

    public MutablePair<Integer, String> getElectionInfo() {
        return electionInfo;
    }

    public void setElectionInfo(MutablePair<Integer, String> electionInfo) {
        this.electionInfo = electionInfo;
    }
}
