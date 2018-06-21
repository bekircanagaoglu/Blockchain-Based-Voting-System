package Miner.Requests;

public abstract class AbstractRequest implements Request {
    private final int election;

    public AbstractRequest(int election) {
        this.election = election;
    }

    @Override
    public int getElectionID() {
        return election;
    }

    @Override
    public String toString() {
        return "AbstractRequest{" +
                "election=" + election +
                '}';
    }
}
