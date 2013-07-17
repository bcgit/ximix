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
package org.cryptoworkshop.ximix.crypto.key.message;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.cryptoworkshop.ximix.common.service.KeyType;

public class KeyPairGenerateMessage
    extends ASN1Object
{
    private final KeyType algorithm;
    private final Enum type;
    private final ASN1Encodable payload;

    public KeyPairGenerateMessage(KeyType algorithm, Enum type, ASN1Encodable payload)
    {
        this.algorithm = algorithm;
        this.type = type;
        this.payload = payload;
    }

    private KeyPairGenerateMessage(Enum[] types, ASN1Sequence seq)
    {
        this.algorithm = KeyType.values()[ASN1Integer.getInstance(seq.getObjectAt(0)).getValue().intValue()];
        this.type = types[ASN1Integer.getInstance(seq.getObjectAt(1)).getValue().intValue()];
        this.payload = seq.getObjectAt(2);
    }

    public static final KeyPairGenerateMessage getInstance(Enum[] types, Object o)
    {
        if (o instanceof KeyPairGenerateMessage)
        {
            return (KeyPairGenerateMessage)o;
        }
        else if (o != null)
        {
            return new KeyPairGenerateMessage(types, ASN1Sequence.getInstance(o));
        }

        return null;
    }

    @Override
    public ASN1Primitive toASN1Primitive()
    {
        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new ASN1Integer(algorithm.ordinal()));
        v.add(new ASN1Integer(type.ordinal()));
        v.add(payload);

        return new DERSequence(v);
    }

    public ASN1Encodable getPayload()
    {
        return payload;
    }

    public KeyType getAlgorithm()
    {
        return algorithm;
    }

    public Enum getType()
    {
        return type;
    }
}
