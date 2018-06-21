package Common.Command;

import Common.Election;
import Common.IdNamePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StartElection extends Command {

    private final Election e;

    public StartElection(Election e) {
        this.e = e;
    }


    @Override
    public int getElectionID() {
        return e.getId();
    }

    public String getName() {
        return e.getName();
    }

    public byte[] getBlindSigKey() {
        return e.getBlindSigKey();
    }

    public List<IdNamePair> getCandidates() {
        return e.getCandidates();
    }

    public Election getElection() {
        return e;
    }

    @Override
    public byte[] getRawData() {
        List<Byte> raw = new ArrayList<>();

        for (byte b : super.getRawData())
            raw.add(b);

        for (byte b : e.getRawData())
            raw.add(b);

        int i = 0;
        byte[] data = new byte[raw.size()];
        for (Byte b : raw)
            data[i++] = b;

        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StartElection that = (StartElection) o;
        return Objects.equals(e, that.e);
    }

    @Override
    public int hashCode() {
        return Objects.hash(e);
    }

    @Override
    public String toString() {
        return "StartElection{" + super.toString() + "}";
    }
}
