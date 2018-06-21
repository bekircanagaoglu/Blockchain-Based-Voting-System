package Server.Registrar;

import Common.Election;
import Common.Token.Token;
import Common.User.User;
import Server.Email;
import Server.ServerDBHandler;
import Server.ServerKeyKeeper;
import org.apache.commons.lang3.tuple.MutablePair;

import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Registrar {
    private static final Map<Integer, Authenticator> authenticators = new HashMap<>();
    private static final List<Election> elections = new LinkedList<>();
    private static final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    public static boolean startElection(MutablePair<MutablePair<KeyPair, Election>,
            MutablePair<Map<String, String>, List<Email>>> election) {
        try {
            rwlock.writeLock().lock();
            int id = election.getKey().getValue().getId();
            if (authenticators.containsKey(id))
                return false;

            Authenticator a = new Authenticator(election.getValue(), id);
            ServerDBHandler.addElection(election, a);
            ServerKeyKeeper.addBlindSignatureKeys(id, election.getKey().getKey());
            authenticators.put(id, a);
            elections.add(election.getKey().getValue());

            return true;
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    public static void restore(List<MutablePair<MutablePair<KeyPair, Election>, Authenticator>> _elections) {
        for (MutablePair<MutablePair<KeyPair, Election>, Authenticator> e : _elections) {
            ServerKeyKeeper.addBlindSignatureKeys(e.left.right.getId(), e.left.left);
            elections.add(e.left.right);
            authenticators.put(e.left.right.getId(), e.right);
        }
    }

    public static void endElection(int id) {
        try {
            rwlock.writeLock().lock();
            ServerDBHandler.removeElection(id);
            authenticators.remove(id);

            Iterator<Election> it = elections.listIterator();
            while (it.hasNext()) {
                if (it.next().getId() == id) {
                    it.remove();
                    break;
                }
            }

            ServerKeyKeeper.removeBlindSignatureKey(id);
        } finally {
            rwlock.writeLock().unlock();
        }
    }

    private static Authenticator getAuthenticator(int election) {
        try {
            rwlock.readLock().lock();
            return authenticators.get(election);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static boolean isUserValid(User user, int election) {
        return getAuthenticator(election).isUserValid(user);
    }

    public static void addUser(User user, int election) {
        getAuthenticator(election).addUser(user);
    }

    public static boolean isEmailValid(String email, int election) {
        return getAuthenticator(election).isEmailValid(email);
    }

    public static boolean isTokenValid(Token t, int election) {
        return getAuthenticator(election).isTokenValid(t);
    }

    public static void validateUser(User user, int election) {
        getAuthenticator(election).validateUser(user);
    }

    public static void validateEmail(String mail, int election) {
        getAuthenticator(election).validateEmail(mail);
    }

    public static String[] getCandidateList(int id) {
        try {
            rwlock.readLock().lock();
            String[] r;
            int i;

            for (i = 0; i < elections.size(); ++i)
                if (elections.get(i).getId() == id)
                    break;

            if (i == elections.size())
                return null;

            r = new String[elections.get(i).getCandidates().size()];

            for (int j = 0; j < r.length; ++j)
                r[j] = elections.get(i).getCandidates().get(j).getName();

            return r;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static MutablePair<Integer, String>[] getOngoingElections() {
        try {
            rwlock.readLock().lock();

            MutablePair[] r = new MutablePair[elections.size()];

            for (int i = 0; i < elections.size(); ++i)
                r[i] = new MutablePair<>(elections.get(i).getId(), elections.get(i).getName());

            return r;
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static int getNumElections() {
        try {
            rwlock.readLock().lock();
            return ServerDBHandler.getNumElections();
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static List<Election> getElections() {
        try {
            rwlock.readLock().lock();
            return new ArrayList<>(elections);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static Set<Integer> getElectionIDs() {
        try {
            rwlock.readLock().lock();
            return new HashSet<>(authenticators.keySet());
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public static Election getElection(int id) {
        try {
            rwlock.readLock().lock();
            for (Election e : elections)
                if (e.getId() == id)
                    return e;
            return null;
        } finally {
            rwlock.readLock().unlock();
        }
    }
}
