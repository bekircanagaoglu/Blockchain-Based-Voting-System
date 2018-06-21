package Common;

import java.io.Serializable;
import java.util.Arrays;

/**
 * note: this class cited from course "Bitcoin and Crypto Currency Technologies" from Coursera
 *
 * a wrapper for byte array with hashCode and equals function implemented
 * */
public class ByteArrayWrapper implements Serializable {

    private final byte[] contents;

    public ByteArrayWrapper(byte[] b) {
        contents = new byte[b.length];
        System.arraycopy(b, 0, contents, 0, contents.length);
    }

    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (getClass() != other.getClass()) {
            return false;
        }

        ByteArrayWrapper otherB = (ByteArrayWrapper) other;
        byte[] b = otherB.contents;
        if (contents == null) {
            if (b == null)
                return true;
            else
                return false;
        } else {
            if (b == null)
                return false;
            else {
                if (contents.length != b.length)
                    return false;
                for (int i = 0; i < b.length; i++)
                    if (contents[i] != b[i])
                        return false;
                return true;
            }
        }
    }

    public int hashCode() {
        return Arrays.hashCode(contents);
    }
}
