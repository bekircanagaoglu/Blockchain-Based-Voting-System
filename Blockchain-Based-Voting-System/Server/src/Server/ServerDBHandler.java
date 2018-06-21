package Server;

import Common.Election;
import Server.Registrar.Authenticator;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.MutablePair;

import java.net.InetAddress;
import java.security.KeyPair;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerDBHandler {
    private static Connection conn;

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:server.db");
            Statement stmt = conn.createStatement();

            String sql = "CREATE TABLE IF NOT EXISTS elections ( "
                    + "id integer PRIMARY KEY, "
                    + "election blob not null, "
                    + "auth blob not null, "
                    + "ongoing integer not null);";
            stmt.execute(sql);

            sql = "CREATE TABLE IF NOT EXISTS miners ( "
                    + "id integer primary key, addr blob );";
            stmt.execute(sql);

            stmt.close();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    public static int getNumElections() {
        int n = 0;

        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            ResultSet r = stmt.executeQuery("SELECT election, auth FROM elections");

            while (r.next())
                n++;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return n;
    }

    public static List<MutablePair<MutablePair<KeyPair, Election>, Authenticator>> getElections() {
        List<MutablePair<MutablePair<KeyPair, Election>, Authenticator>> elections = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet r = stmt.executeQuery("SELECT election, auth FROM elections WHERE ongoing = 1");

            while (r.next()) {
                elections.add(new MutablePair<>(
                        SerializationUtils.deserialize(r.getBytes(1)),
                        SerializationUtils.deserialize(r.getBytes(2))
                ));
            }

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return elections;
    }

    public static void updateAuthenticator(int election, Authenticator auth) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE elections SET auth = ? WHERE id = ?");
            pstmt.setBytes(1, SerializationUtils.serialize(auth));
            pstmt.setInt(2, election);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    public static void addElection(MutablePair<MutablePair<KeyPair, Election>, MutablePair<Map<String, String>, List<Email>>> election,
                                   Authenticator auth) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO elections(id, election, auth, ongoing) values(?, ?, ?, 1)");
            pstmt.setInt(1, election.left.right.getId());
            pstmt.setBytes(2, SerializationUtils.serialize(election.left));
            pstmt.setBytes(3, SerializationUtils.serialize(auth));
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeElection(int id) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("UPDATE elections SET ongoing = 0 WHERE id = ?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void addMiner(int id, InetAddress addr) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO miners(id, addr) values(?, ?)");
            pstmt.setInt(1, id);
            pstmt.setBytes(2, SerializationUtils.serialize(addr));
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<InetAddress> getMiners() {
        List<InetAddress> miners = new ArrayList<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet r = stmt.executeQuery("SELECT addr FROM miners");

            while (r.next())
                miners.add(SerializationUtils.deserialize(r.getBytes(1)));
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return miners;
    }

    public static void removeMiners() {
        try {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("DELETE FROM miners");
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeMiner(int id) {
        try {
            PreparedStatement pstmt = conn.prepareStatement("DELETE FROM miners WHERE id = ?");
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            pstmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
