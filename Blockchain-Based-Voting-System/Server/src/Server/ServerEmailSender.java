package Server;

import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class ServerEmailSender {
    private static Mailer mailer = null;
    private static String from = null;
    private static Queue<Email> q = new ConcurrentLinkedQueue<>();
    private static Thread mailman = null;

    public static void init(String mail, String pwd, String smtp) {
        from = mail;
        mailer = MailerBuilder
                .withSMTPServer("smtp." + smtp, 587, mail, pwd)
                .buildMailer();
        mailer.testConnection();

        if (mailman == null) {
            mailman = new Thread(()->{
                for (;;) {
                    while (!q.isEmpty())
                        mailer.sendMail(q.poll());

                    synchronized (q) {
                        if (q.isEmpty()) {
                            try {
                                q.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            mailman.start();
        }
    }

    public static boolean sendEmail(String to, String subject, String txt) {
        if (mailer == null)
            return false;

        Email email = EmailBuilder.startingBlank()
                .to(to)
                .from(from)
                .withSubject(subject)
                .withPlainText(txt)
                .buildEmail();
        q.add(email);
        synchronized (q) {
            q.notify();
        }
        return true;
    }

    /**
     * source: https://www.geeksforgeeks.org/check-email-address-valid-not-java/
     */
    public static boolean isValid(String email)
    {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\."+
                "[a-zA-Z0-9_+&*-]+)*@" +
                "(?:[a-zA-Z0-9-]+\\.)+[a-z" +
                "A-Z]{2,7}$";

        Pattern pat = Pattern.compile(emailRegex);
        if (email == null)
            return false;
        return pat.matcher(email).matches();
    }

    public static String getAccount() {
        return from;
    }
}
