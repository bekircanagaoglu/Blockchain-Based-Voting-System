package Common.Command;

import Common.MinerInfo;
import org.bouncycastle.util.Arrays;

import java.nio.ByteBuffer;

public class GenerateNextBlock extends Command {
    private final MinerInfo minerInfo;
    private final double pingTime; //ms
    private final int controlPeriod; //ms

    public GenerateNextBlock(MinerInfo minerInfo, double pingTime, int controlPeriod) {
        this.minerInfo = minerInfo;
        this.pingTime = pingTime;
        this.controlPeriod = controlPeriod;
    }

    public MinerInfo getMinerInfo() {
        return minerInfo;
    }

    public double getPingTime() {
        return pingTime;
    }

    public int getControlPeriod() {
        return controlPeriod;
    }

    @Override
    public byte[] getRawData() {
        ByteBuffer buf = ByteBuffer.allocate((Double.SIZE + Integer.SIZE) / 8);
        buf.putDouble(pingTime);
        buf.putInt(controlPeriod);
        return Arrays.concatenate(super.getRawData(), minerInfo.getRawData(), buf.array());
    }

    @Override
    public String toString() {
        return "GenerateNextBlock{" + minerInfo + "\nping time: " + pingTime
                + "\ncontrol period: " + controlPeriod + "}";
    }
}
