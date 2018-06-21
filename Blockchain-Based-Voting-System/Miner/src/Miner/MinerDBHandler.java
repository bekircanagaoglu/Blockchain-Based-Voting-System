package Miner;

import Common.Block.Block;
import Common.Block.FinalBlock;
import Common.Command.Request.GetVote;
import Common.Command.StartElection;
import Common.Common;
import Common.Crypto.Crypto;
import Common.Election;
import Common.Transaction.TokenCreation;
import Common.Transaction.Transaction;
import Common.Transaction.Vote;
import Miner.BlockChain.BlockChain;
import Miner.BlockChain.BlockChainHandler;
import Miner.BlockChain.TransactionPool;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.MutablePair;

import java.security.PrivateKey;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MinerDBHandler {
    private static Connection conn;

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:miner.db");
            Statement stmt = conn.createStatement();

            String sql = "CREATE TABLE IF NOT EXISTS elections ( "
                    + "eid integer PRIMARY KEY, "
                    + "startElectionCmd blob not null, "
                    + "ongoing integer not null);";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS blockchains ( "
                    + "eid integer not null, "
                    + "block blob not null);";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS txPools ( "
                    + "eid integer not null PRIMARY KEY, "
                    + "txPool blob);";
            stmt.execute(sql);

            stmt.close();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void addElection(StartElection e) {
        try {
            if (getStartElectionCmds().contains(e) || getFinishedElections().contains(e.getElection()))
                deleteElection(e.getElectionID());
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO elections(eid, startElectionCmd, ongoing) " +
                    "VALUES(?, ?, ?)");
            pstmt.setInt(1, e.getElectionID());
            pstmt.setBytes(2, SerializationUtils.serialize(e));
            pstmt.setInt(3, 1);
            pstmt.executeUpdate();
            pstmt.close();

            Statement stmt = conn.createStatement();
            stmt.executeUpdate("INSERT INTO txPools(eid, txPool) VALUES(" + e.getElectionID() + ", NULL)");
            stmt.close();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    }

    public static String getElectionResult(int id) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT block "
                    + "FROM blockchains WHERE eid = " + id);

            List<Vote> votes = new ArrayList<>();
            PrivateKey key = null;
            int tokens = 0;

            while (rs.next()) {
                Block block = SerializationUtils.deserialize(rs.getBytes(1));

                if (block instanceof FinalBlock) {
                    key = Crypto.decodePrivateKey(((FinalBlock) block).getEECmd().getKey());
                } else {
                    List<Transaction> txs = block.getTxs();
                    for (Transaction tx : txs)
                        if (tx instanceof Vote)
                            votes.add((Vote) tx);
                        else if (tx instanceof TokenCreation)
                            tokens++;
                }
            }

            if (key == null)
                return null;

            rs = stmt.executeQuery("SELECT startElectionCmd FROM elections where eid = "
                    + id);
            if (!rs.next())
                return null;

            StartElection e = SerializationUtils.deserialize(rs.getBytes(1));

            return tokens + " token(s) generated\n" + BlockChainHandler.countVotes(e.getElection(), votes, key);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;


    }

    public static String getVote(GetVote gv) {
        try {
            Statement stmt = conn.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT block "
                    + "FROM blockchains WHERE eid = " + gv.getElectionID());

            Vote v = null;
            while (rs.next()) {
                Block block = SerializationUtils.deserialize(rs.getBytes(1));

                if (block instanceof FinalBlock) {
                    if (v == null)
                        return null;

                    FinalBlock finalBlock = (FinalBlock) block;

                    MutablePair<List<Integer>, Integer> vote = Common.openVote(v.getVote(),
                            Crypto.decodePrivateKey(finalBlock.getEECmd().getKey()));

                    rs = stmt.executeQuery("SELECT startElectionCmd FROM elections where eid = "
                            + gv.getElectionID());
                    if (!rs.next())
                        return null;

                    StartElection e = SerializationUtils.deserialize(rs.getBytes(1));

                    StringBuilder sb = new StringBuilder();
                    sb.append("Timestamp: ").append(v.getTimestamp()).append("\n");

                    for (Integer i : vote.left)
                        sb.append("Candidate: ").append(e.getCandidates().get(i)).append("\n");

                    return sb.toString();
                }

                if (v == null)
                    v = BlockChainHandler.findVote(block, gv.getKey());

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void addBlock(int election, Block block, TransactionPool pool) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO blockchains(eid, block) VALUES(?, ?)");
            pstmt.setInt(1, election);
            pstmt.setBytes(2, SerializationUtils.serialize(block));
            pstmt.executeUpdate();
            pstmt.close();
            updateTxPool(election, pool);
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    }

    public static void importBlocks(int election, BlockChain chain) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT block "
                    + "FROM blockchains WHERE eid = " + election);

            while (rs.next())
                chain.addVerifiedBlock(SerializationUtils.deserialize(rs.getBytes(1)));

            stmt = conn.createStatement();
            rs = stmt.executeQuery("SELECT txPool "
                    + "FROM txPools WHERE eid = " + election);

            if (rs.next()) {
                byte[] tmp  = rs.getBytes(1);
                if (tmp != null)
                    chain.setTransactionPool(SerializationUtils.deserialize(tmp));
            }
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public static void updateTxPool(int election, TransactionPool pool) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE txPools SET txPool = ? WHERE eid = ?");
            pstmt.setBytes(1, SerializationUtils.serialize(pool));
            pstmt.setInt(2, election);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public static void removeElection(int id) {
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("UPDATE elections SET ongoing = 0 WHERE eid = " + id);
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteElection(int id) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("DELETE FROM elections WHERE eid = ?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            pstmt = conn.prepareStatement("DELETE FROM blockchains WHERE eid = ?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            pstmt = conn.prepareStatement("DELETE FROM txPools WHERE eid = ?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public static List<StartElection> getStartElectionCmds() {
        List<StartElection> l = new ArrayList<>();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT startElectionCmd "
                    + "FROM elections WHERE ongoing = 1");

            while (rs.next())
                l.add(SerializationUtils.deserialize(rs.getBytes(1)));

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return l;
    }

    public static List<Election> getFinishedElections() {
        List<Election> l = new ArrayList<>();
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT startElectionCmd "
                    + "FROM elections WHERE ongoing = 0");

            while (rs.next())
                l.add(((StartElection) SerializationUtils.deserialize(rs.getBytes(1))).getElection());

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return l;
    }

}
