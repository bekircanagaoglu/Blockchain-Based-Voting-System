package Common;

import Common.Crypto.Crypto;
import org.apache.commons.lang3.tuple.MutablePair;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

public class Common {
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "null";

        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static MutablePair<List<Integer>, Integer> openVote(byte[] vote, PrivateKey key) {
        ByteBuffer buf = ByteBuffer.wrap(Crypto.decrypt(vote, key));
        int i = buf.getInt();
        List<Integer> r = new ArrayList<>();

        while (i-- > 0)
            r.add(buf.getInt());

        return new MutablePair<>(r, buf.getInt());
    }

    public static void writeTofile(byte[] data, String path) throws IOException {
        FileOutputStream outFile = new FileOutputStream(path);
        outFile.write(data);
        outFile.close();
    }

    public static void writeTofile(Serializable data, String path) throws IOException {
        ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(path));
        outputStream.writeObject(data);
        outputStream.close();
    }
}
