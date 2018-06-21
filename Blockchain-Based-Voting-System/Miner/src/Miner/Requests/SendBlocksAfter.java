package Miner.Requests;

public class SendBlocksAfter extends AbstractRequest {
    private final int id;

    public SendBlocksAfter(int election, int id) {
        super(election);
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "SendBlocksAfter{" +
                "id=" + id +
                '}';
    }
}
