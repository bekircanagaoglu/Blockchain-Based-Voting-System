package Server;

import java.io.Serializable;
import java.util.Objects;

public class Email implements Serializable  {
    private final String uname;
    private final String host;

    public Email(String uname, String host) {
        this.uname = uname;
        this.host = host;
    }

    public String getUname() {
        return uname;
    }

    public String getHost() {
        return host;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return  Objects.equals(host, email.host) && Objects.equals(uname, email.uname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uname, host);
    }

    @Override
    public String toString() {
        return "Email{" +
                "uname='" + uname + '\'' +
                ", host='" + host + '\'' +
                '}';
    }
}
