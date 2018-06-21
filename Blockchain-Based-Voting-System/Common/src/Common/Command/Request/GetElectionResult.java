package Common.Command.Request;

import Common.Command.Command;

public class GetElectionResult extends Command {
    public GetElectionResult(int id) {
        setElectionID(id);
    }
}
