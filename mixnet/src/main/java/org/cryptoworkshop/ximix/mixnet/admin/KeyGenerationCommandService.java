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
package org.cryptoworkshop.ximix.mixnet.admin;

import java.io.IOException;
import java.math.BigInteger;

import org.cryptoworkshop.ximix.common.message.ClientMessage;
import org.cryptoworkshop.ximix.common.message.CommandMessage;
import org.cryptoworkshop.ximix.common.message.FetchPublicKeyMessage;
import org.cryptoworkshop.ximix.common.message.GenerateKeyPairMessage;
import org.cryptoworkshop.ximix.common.message.MessageReply;
import org.cryptoworkshop.ximix.common.service.AdminServicesConnection;
import org.cryptoworkshop.ximix.common.service.ServiceConnectionException;
import org.cryptoworkshop.ximix.crypto.client.KeyGenerationService;

public class KeyGenerationCommandService
    implements KeyGenerationService
{
    private AdminServicesConnection connection;

    public KeyGenerationCommandService(AdminServicesConnection connection)
    {
        this.connection = connection;
    }

    @Override
    public byte[] generatePublicKey(String keyID, int thresholdNumber, String... nodeNames)
        throws ServiceConnectionException
    {
        BigInteger h = BigInteger.valueOf(1000001); // TODO
        final GenerateKeyPairMessage genKeyPairMessage = new GenerateKeyPairMessage(keyID, thresholdNumber, h, nodeNames);

        MessageReply reply = connection.sendMessage(nodeNames[0], CommandMessage.Type.INITIATE_GENERATE_KEY_PAIR, genKeyPairMessage);

        try
        {
            return reply.getPayload().toASN1Primitive().getEncoded();
        }
        catch (IOException e)
        {
            throw new ServiceConnectionException("malformed public key returned: " + e.getMessage());
        }
    }

    @Override
    public byte[] fetchPublicKey(String keyID)
        throws ServiceConnectionException
    {
        final FetchPublicKeyMessage genKeyPairMessage = new FetchPublicKeyMessage(keyID);

        MessageReply reply = connection.sendMessage(ClientMessage.Type.FETCH_PUBLIC_KEY, genKeyPairMessage);

        try
        {
            return reply.getPayload().toASN1Primitive().getEncoded();
        }
        catch (IOException e)
        {
            throw new ServiceConnectionException("malformed public key returned: " + e.getMessage());
        }
    }
}
