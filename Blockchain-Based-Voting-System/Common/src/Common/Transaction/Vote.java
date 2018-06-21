package Common.Transaction;

import Common.Token.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Vote extends Transaction {

    private byte[] encodedKey;
    private byte[] vote;
    private Token token;
    private byte[] signature;

    public Vote() {
    }

    @Override
    public String toString() {
        return  super.toString() + "Vote{" +
                "electionID=" + getElectionID() +
                '}';
    }

    public byte[] getVote() {
        return vote;
    }

    public void setVote(byte[] vote) {
        this.vote = vote;
    }

    public byte[] getEncodedKey() {
        return encodedKey;
    }

    public void setEncodedKey(byte[] encodedKey) {
        this.encodedKey = encodedKey;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    @Override
    public byte[] getRawDataToSign() {
        List<Byte> data = new ArrayList<>();

        for (byte b : super.getRawDataToSign())
            data.add(b);

        for (byte b : encodedKey)
            data.add(b);

//        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE / 8);
//        buf.putInt(candidateID);

        for (byte b : vote)
            data.add(b);

        for (byte b : token.getRawDataToSign())
            data.add(b);

        byte[] result = new byte[data.size()];
        int i = 0;

        for (Byte b : data)
            result[i++] = b;

        return result;
    }

    public Token getToken() {
        return token;
    }

    public void setToken(Token token) {
        this.token = token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote = (Vote) o;
        return token.equals(vote.token);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encodedKey);
    }
}
