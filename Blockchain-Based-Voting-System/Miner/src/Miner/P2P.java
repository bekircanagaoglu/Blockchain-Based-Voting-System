package Miner;

import Common.Acknowledge;
import Common.Block.Block;
import Common.Command.Command;
import Common.Command.ConnectMe;
import Common.Command.Request.GetElectionResult;
import Common.Command.Request.GetVote;
import Common.Crypto.Crypto;
import Common.Job;
import Common.Token.Token;
import Common.Transaction.Transaction;
import Common.Transaction.Vote;
import Miner.BlockChain.BlockChain;
import Miner.BlockChain.TransactionPool;
import Miner.CommandHandler.CommandHandler;
import Miner.Requests.Request;
import Miner.Requests.SendBlocksAfter;
import Miner.Requests.SendLastBlock;
import Miner.Requests.SendSECmd;
import org.apache.commons.lang3.tuple.MutablePair;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class P2P {
    public static final int PORT = 4002;
    private static String host = null;

    public static String getHost() {
        return host;
    }

    public static void handleRequests(CommandHandler c, Object mon) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);

            for (;;) {
                Socket s = serverSocket.accept();

                new Thread(()->
                {
                    try {
                        ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                        ObjectInputStream in = new ObjectInputStream(s.getInputStream());

                        for (;;) {
                            Object o = in.readObject();
                            System.out.println(o);
                            if (o instanceof Request) {
                                if (o instanceof SendLastBlock) {
                                    out.writeObject(c.getBlockChainHandler()
                                            .getBlockchain(((SendLastBlock) o).getElectionID()).getLastBlock());
                                } else if (o instanceof SendBlocksAfter) {
                                    MutablePair<Object, Block> p =
                                            c.getBlockChainHandler().getBlockchain(((SendBlocksAfter) o)
                                                    .getElectionID()).reverseIterate(null);

                                    while (p.right.getId() > ((SendBlocksAfter) o).getId()) {
                                        out.writeObject(p.right);
                                        p = c.getBlockChainHandler().getBlockchain(((SendBlocksAfter) o)
                                                .getElectionID()).reverseIterate(p.left);
                                    }

                                    out.writeObject(c.getBlockChainHandler().getBlockchain(((SendBlocksAfter) o)
                                            .getElectionID()).getTransactionPool());

                                } else if (o instanceof SendSECmd) {
                                    out.writeObject(c.getSECmd(((SendSECmd) o).getElectionID()));
                                }
                            } else if (o instanceof ConnectMe) {
                                if (Crypto.verifySignature(MinerKeyKeeper.getServerPublicKey(),
                                        ((ConnectMe) o).getRawData(), ((ConnectMe) o).getSignature())) {
                                    host = s.getInetAddress().getHostAddress();
                                    synchronized (mon) {
                                        mon.notify();
                                    }
                                }
                            } else if (o.equals(Job.GetFinishedElections)) {
                                out.writeObject(MinerDBHandler.getFinishedElections());
                            } else if (o.equals(Job.GetOngoingElections)) {
                                out.writeObject(c.getOngoingElections());
                            } else if (o instanceof GetVote) {
                                String vote = null;
                                if (c.getElections().contains(((GetVote) o).getElectionID())) {
                                    Vote v = c.getBlockChainHandler().getVote(((GetVote) o).getElectionID(),
                                            ((GetVote) o).getKey());
                                    if (v != null) {
                                        vote = "Your vote is in the blockchain.\nYou can see your vote from the server";
                                    }
                                } else {
                                    vote = MinerDBHandler.getVote((GetVote) o);
                                }
                                out.writeObject(vote == null ? Acknowledge.FAIL : vote);
                            } else if (o instanceof GetElectionResult) {
                                String result = MinerDBHandler.getElectionResult(((GetElectionResult) o).getElectionID());
                                out.writeObject(result == null ? Acknowledge.FAIL : result);
                            }
                        }
                    } catch (IOException e) {
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sync(InetAddress[] peers, InetAddress srvAddr, CommandHandler commandHandler, Set<Integer> ids) {
        for (InetAddress peer : peers) {
            try {
                Socket s = new Socket(peer.getHostAddress().startsWith("127") ? srvAddr : peer, PORT);
                ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                Set<Integer> elections = commandHandler.getElections();
                boolean ok = true;

                for (Integer id : ids) {
                    if (!elections.contains(id)) {
                        out.writeObject(new SendSECmd(id));
                        Command c = (Command) in.readObject();
                        commandHandler.handleCommand(c);
                    }

                    out.writeObject(new SendLastBlock(id));
                    Block tmp = (Block) in.readObject();
                    int last = commandHandler.getBlockChainHandler()
                            .getBlockchain(id).getLastBlock().getId();
                    int n = tmp.getId() - last;
                    if (n > 0) {
                        Set<Token> usedTokens = new HashSet<>(commandHandler.getBlockChainHandler()
                                .getBlockchain(id).getUsedTokens());
                        Set<String> usedUsernames = new HashSet<>(commandHandler.getBlockChainHandler()
                                .getBlockchain(id).getUsedUsernames());
                        Stack<Block> stack = new Stack<>();

                        out.writeObject(new SendBlocksAfter(id, last));
                        tmp = commandHandler.getBlockChainHandler()
                                .getBlockchain(id).getLastBlock();
                        while (n-- > 0) {
                            Block b = (Block) in.readObject();
                            stack.push(b);
                        }

                        for (int i = stack.size() - 1; i >= 0; --i) {
                            Block b = stack.get(i);
                            List<Transaction> validTxs = BlockChain.tryAddBlock(tmp, b, usedTokens, usedUsernames);
                            if (validTxs == null) {
                                ok = false;
                                break;
                            }
                            tmp = b;
                            for (Transaction tx : validTxs)
                                if (tx instanceof Vote)
                                    usedTokens.add(((Vote) tx).getToken());
                        }

                        if (!ok)
                            break;

                        TransactionPool pool = (TransactionPool) in.readObject();

                        while (!stack.empty())
                            commandHandler.getBlockChainHandler()
                                    .getBlockchain(id).addBlock(stack.pop());

                        commandHandler.getBlockChainHandler()
                                .getBlockchain(id).setTransactionPool(pool);
                    }
                }
                s.close();

                if (ok)
                    break;

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}


