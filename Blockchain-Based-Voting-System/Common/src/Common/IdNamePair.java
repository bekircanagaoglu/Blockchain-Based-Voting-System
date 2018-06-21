package Common;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IdNamePair implements Serializable {
    private int id;
    private String name;

    public IdNamePair() {
    }

    public IdNamePair(int id, String name) {
        this.id = id;
        this.name = name;
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

    public byte[] getRawData() {
        List<Byte> raw = new ArrayList<>();

        ByteBuffer buf = ByteBuffer.allocate((Integer.SIZE) / 8);
        buf.putInt(id);

        for (byte b : buf.array())
            raw.add(b);

        try {
            for (byte b : name.getBytes("UTF-8"))
                raw.add(b);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

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
        IdNamePair that = (IdNamePair) o;
        return id == that.id &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "(" + id + ", " + name + ")";
    }
}
