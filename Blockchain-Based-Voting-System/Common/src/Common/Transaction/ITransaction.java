package Common.Transaction;

import java.io.Serializable;

public interface ITransaction extends Serializable {

    int getElectionID();
    byte[] getRawDataToSign();
}
