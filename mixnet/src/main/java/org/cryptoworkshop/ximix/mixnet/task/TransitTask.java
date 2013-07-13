/**
 * Copyright 2013 Crypto Workshop Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cryptoworkshop.ximix.mixnet.task;

import org.cryptoworkshop.ximix.common.message.BoardUploadMessage;
import org.cryptoworkshop.ximix.common.service.NodeContext;
import org.cryptoworkshop.ximix.mixnet.board.BulletinBoardRegistry;

public class TransitTask
    implements Runnable
{
    private final NodeContext nodeContext;
    private final BoardUploadMessage message;
    private final BulletinBoardRegistry boardRegistry;

    public TransitTask(NodeContext nodeContext, BulletinBoardRegistry boardRegistry, BoardUploadMessage message)
    {
        this.nodeContext = nodeContext;
        this.boardRegistry = boardRegistry;
        this.message = message;
    }

    public void run()
    {
        if (boardRegistry.hasBoard(message.getBoardName()))
        {
            boardRegistry.getBoard(message.getBoardName()).postMessage(message.getData());
        }
        else
        {
            boardRegistry.getTransitBoard(message.getBoardName()).postMessage(message.getData());
        }
    }
}
