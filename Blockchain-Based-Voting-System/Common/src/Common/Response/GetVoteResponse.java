package Common.Response;

import java.io.Serializable;

public class GetVoteResponse implements Response {
    private byte[] key;
    private Serializable response;

    public Serializable getResponse() {
        return response;
    }

    public void setResponse(Serializable response) {
        this.response = response;
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }
}
