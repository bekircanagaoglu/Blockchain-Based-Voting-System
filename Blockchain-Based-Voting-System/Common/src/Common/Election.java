package Common;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Election implements Serializable {
    private int id;
    private int nvotes_l; //min
    private int nvotes_h; //max
    private String name;
    private byte[] blindSigKey;
    private List<IdNamePair> candidates;

    public Election() {
    }

    public byte[] getRawData() {
        List<Byte> raw = new ArrayList<>();

        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE / 8 * 3);
        buf.putInt(id);
        buf.putInt(nvotes_l);
        buf.putInt(nvotes_h);
        for (byte b : buf.array())
            raw.add(b);

        try {
            for (byte b : name.getBytes("UTF-8"))
                raw.add(b);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        for (byte b : blindSigKey)
            raw.add(b);

        for (IdNamePair c : candidates)
            for (byte b : c.getRawData())
                raw.add(b);

        int i = 0;
        byte[] data = new byte[raw.size()];
        for (Byte b : raw)
            data[i++] = b;

        return data;
    }

    public int getNvotes_l() {
        return nvotes_l;
    }

    public void setNvotes_l(int nvotes_l) {
        this.nvotes_l = nvotes_l;
    }

    public int getNvotes_h() {
        return nvotes_h;
    }

    public void setNvotes_h(int nvotes_h) {
        this.nvotes_h = nvotes_h;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getBlindSigKey() {
        return blindSigKey;
    }

    public void setBlindSigKey(PublicKey blindSigKey) {
        this.blindSigKey = blindSigKey.getEncoded();
    }

    public void setCandidates(List<IdNamePair> candidates) {
        this.candidates = candidates;
    }

    public List<IdNamePair> getCandidates() {
        return candidates;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                "ID: " + id + "\n" +
                "Name: " + name + "\n" +
                "Votes: " + nvotes_l + " - " + nvotes_h + "\n" +
                "Candidates:\n");

        for (IdNamePair pair : candidates)
            sb.append("    ").append(pair).append("\n");

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Election election = (Election) o;
        return id == election.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
