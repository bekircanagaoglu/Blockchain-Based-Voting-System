package Common;

import java.io.Serializable;

public enum Job implements Serializable {
    TokenCreation, Voting, GetVote, GetCandidates, GetOngoingElections, Cancel, GetElection, GetMiners,
    GetFinishedElections, GetOTP
}
