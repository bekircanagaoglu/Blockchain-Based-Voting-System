package Miner.CommandHandler;

import Common.Command.GenerateNextBlock;
import Miner.BlockChain.BlockChainHandler;

public class AuditCommandHandler extends CommandHandler {

    public AuditCommandHandler(BlockChainHandler blockChainHandler) {
        super(blockChainHandler);
    }

    @Override
    protected void generateNextBlock(GenerateNextBlock c) {
        return;
    }
}
