package Server.Registrar;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.*;

import Common.Common;
import Common.Crypto.Crypto;
import Common.Token.Token;
import Common.User.User;
import Server.Email;
import Server.ServerEmailSender;
import Server.ServerDBHandler;
import Server.ServerKeyKeeper;
import org.apache.commons.lang3.tuple.MutablePair;

public class Authenticator implements Serializable {
    private final int id;
    private final Map<String, String> users;
    private final Set<Email> mails;
    private final Set<String> invalidatedUsers;
    private final Set<Email> invalidatedMails;
    private final Set<Token> usedTokens;

    public Authenticator(MutablePair<Map<String, String>, List<Email>> users, int id) {
        usedTokens = new HashSet<>();
        invalidatedUsers = new HashSet<>();
        invalidatedMails = new HashSet<>();
        this.users = users.left;
        this.mails = new HashSet<>(users.right);
        this.id = id;
    }

    public synchronized boolean isEmailValid(String mail) {
        if (!ServerEmailSender.isValid(mail))
            return false;

        String[] tmp = mail.split("@");
        Email email = new Email(tmp[0], tmp[1]);

        if (email.getUname().equals("*"))
            return false;

        if (!(mails.contains(email) || mails.contains(new Email("*", tmp[1]))) || invalidatedMails.contains(email))
            return false;

            invalidatedMails.add(email);
            ServerDBHandler.updateAuthenticator(id, this);
            return true;
    }

    public synchronized void validateEmail(String mail) {
        String[] tmp = mail.split("@");
        Email email = new Email(tmp[0], tmp[1]);
        invalidatedMails.remove(email);
    }

    public synchronized void addUser(User user) {
        users.put(user.getUsername(), user.getPasswd());
    }

    public synchronized boolean isUserValid(User user) {

        if (invalidatedUsers.contains(user.getUsername()))
            return false;

        String pwd = null;

        try {
            pwd = Common.bytesToHex(Crypto.calcSHA256sum(user.getPasswd().getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String s = users.get(user.getUsername());

        if (!pwd.equalsIgnoreCase(s))
            return false;

        invalidatedUsers.add(user.getUsername());
        ServerDBHandler.updateAuthenticator(id, this);

        return true;
    }

    public synchronized void validateUser(User user) {
        invalidatedUsers.remove(user.getUsername());
    }

    public synchronized boolean isTokenValid(Token t) {
        //check signature
        try {
            if (!Crypto.verifySignature(ServerKeyKeeper.getBlindSignatureKeys(id).getPublic(),
                    t.getRawDataToSign(), t.getSignature()))
                return false;
        } catch (Exception e) {
            return false;
        }

        synchronized (usedTokens) {
            //check if token used before
            if (usedTokens.contains(t))
                return false;

            usedTokens.add(t);
        }
        return true;
    }
}
