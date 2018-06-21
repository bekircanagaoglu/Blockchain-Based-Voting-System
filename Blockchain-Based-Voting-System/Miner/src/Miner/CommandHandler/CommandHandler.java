package Miner.CommandHandler;

import Common.Block.BlockFrame;
import Common.Command.EndElection;
import Common.Command.Request.GetVote;
import Common.Election;
import Common.Response.GetVoteResponse;
import Common.MinerInfo;
import Common.Transaction.Vote;
import Common.Acknowledge;
import Miner.ConnectionHandler.MinerConnectionHandler;
import Miner.MinerKeyKeeper;
import Miner.BlockChain.BlockChainHandler;
import Common.Command.Command;
import Common.Command.GenerateNextBlock;
import Common.Command.StartElection;
import Common.Crypto.Crypto;
import org.apache.commons.lang3.tuple.MutablePair;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommandHandler {
    private final BlockChainHandler blockChainHandler;
    private MinerInfo minerInfo;
    private final Map<Integer, Command> seCommands;
    private Timer timer;
    private AtomicBoolean timesUp = new AtomicBoolean();

    public CommandHandler(BlockChainHandler blockChainHandler) {
        minerInfo = null;
        this.blockChainHandler = blockChainHandler;
        seCommands = new ConcurrentHashMap<>();
    }

    public void handleCommand(Command c) {

        if (!isCommandValid(c)) {
            System.err.println("Invalid command " + c);
            return;
        }
        if (c instanceof StartElection) {
            seCommands.put(c.getElectionID(), c);
            startElectionAndAddDB(c);
        } else if (c instanceof GenerateNextBlock) {
            MinerKeyKeeper.setLastMinersPK(((GenerateNextBlock) c).getMinerInfo().getPublicKey());
            generateNextBlock((GenerateNextBlock) c);
        } else if (c instanceof GetVote) {
            getVote(c);
        } else if (c instanceof EndElection) {
            StartElection cmd = (StartElection) seCommands.remove(c.getElectionID());
            blockChainHandler.printElectionResult(cmd.getElection(), Crypto.decodePrivateKey(((EndElection) c).getKey()));
            blockChainHandler.endElection((EndElection) c);
            MinerKeyKeeper.removeBlindSignatureKey(c.getElectionID());
        }
    }

    public Set<Integer> getElections() {return seCommands.keySet();}

    public List<Command> getSECommands() {
        return new ArrayList<>(seCommands.values());
    }

    public List<Election> getOngoingElections() {
        List<Election> oe = new ArrayList<>();
        List<Command> cmds = getSECommands();

        for (Command c : cmds)
            oe.add(((StartElection) c).getElection());

        return oe;
    }

    public Command getSECmd(int election) { return seCommands.get(election); }

    private void getVote(Command c) {
        Vote retval = blockChainHandler.getVote(c.getElectionID(), ((GetVote) c).getKey());
        Serializable data;

        if (retval == null)
            data = Acknowledge.FAIL;
        else
            data = retval;

        GetVoteResponse r = new GetVoteResponse();
        r.setResponse(data);
        r.setKey(((GetVote) c).getKey());

        MinerConnectionHandler.sendObject(r);
    }

    public void startElection(Command c) {
        seCommands.put(c.getElectionID(), c);
        blockChainHandler.startElection((StartElection) c);
    }

    private void startElectionAndAddDB(Command c) {
        blockChainHandler.startElectionAndAddDB((StartElection) c);
    }

    private boolean isCommandValid(Command c) {
        return Crypto.verifySignature(MinerKeyKeeper.getServerPublicKey(), c.getRawData(), c.getSignature());
    }

    protected void generateNextBlock(GenerateNextBlock c) {
        if (c.getMinerInfo().equals(minerInfo)) {
            timesUp.set(false);
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    timesUp.set(true);
                }
            }, (long) (c.getControlPeriod() - 4 * c.getPingTime()));

            List<MutablePair<Integer, String>> infos = blockChainHandler.getElectionInfos();

            for (MutablePair<Integer, String> p : infos) {
                if (timesUp.get())
                    break;
                if (blockChainHandler.generateBlock(p, timesUp) == false)
                    MinerConnectionHandler.sendObject(new BlockFrame(null, null,true));
            }
            timer.cancel();
        }
    }

    public BlockChainHandler getBlockChainHandler() {
        return blockChainHandler;
    }

    public void setMinerInfo(MinerInfo minerInfo) {
        this.minerInfo = minerInfo;
    }
}
