package Common;

import java.io.Serializable;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MinerInfo implements Serializable {
    private int id;
    private MinerType type;
    private PublicKey publicKey;
    private InetAddress addr;

    public MinerInfo() {
        this(-1);
    }

    public MinerInfo(int id) {
        this.id = id;
        addr = null;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public MinerType getType() {
        return type;
    }

    public void setType(MinerType type) {
        this.type = type;
    }

    public void setAddr(InetAddress addr) {
        this.addr = addr;
    }

    public InetAddress getAddr() {
        return addr;
    }

    public byte[] getRawData() {
        List<Byte> raw = new ArrayList<>();

        ByteBuffer buf = ByteBuffer.allocate((Integer.SIZE * 2) / 8);
        buf.putInt(id);
        buf.putInt(type.value());

        for (byte b : buf.array())
            raw.add(b);

        for (byte b : publicKey.getEncoded())
            raw.add(b);

        byte[] result = new byte[raw.size()];

        int i = 0;
        for (Byte b : raw)
            result[i++] = b;

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MinerInfo minerInfo = (MinerInfo) o;
        return id == minerInfo.id;
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ID: " + id + (addr != null ? (" Addr: " + addr.getHostAddress()) : "");
    }
}
