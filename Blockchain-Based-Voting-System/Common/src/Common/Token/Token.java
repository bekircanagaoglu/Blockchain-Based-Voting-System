package Common.Token;

import java.io.Serializable;
import java.util.Arrays;

public class Token implements Serializable {

    private byte[] data;
    private byte[] signature;

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getRawDataToSign() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Arrays.equals(data, token.data);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(data);
        return result;
    }

    @Override
    public String toString() {
        return  super.toString() +
                "\nToken{" +
                "\ndata=" + Common.Common.bytesToHex(data) +
                "\nsignature=" + Common.Common.bytesToHex(signature) +
                "\n}";
    }
}
