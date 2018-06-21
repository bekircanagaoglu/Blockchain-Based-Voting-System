package Common.Transaction;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TokenCreation extends Transaction {

    private String username;

    @Override
    public byte[] getRawDataToSign() {
        List<Byte> data = new ArrayList<>();

        for (byte b : super.getRawDataToSign())
            data.add(b);

        try {
            for (byte b : username.getBytes("UTF-8"))
                data.add(b);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        byte[] result = new byte[data.size()];
        int i = 0;

        for (Byte b : data)
            result[i++] = b;

        return result;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenCreation that = (TokenCreation) o;
        return Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {

        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return super.toString() + " TokenCreation{" +
                "username='" + username + '\'' +
                '}';
    }
}
