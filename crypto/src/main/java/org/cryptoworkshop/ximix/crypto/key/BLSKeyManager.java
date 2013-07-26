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
package org.cryptoworkshop.ximix.crypto.key;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import it.unisa.dia.gas.crypto.jpbc.signature.bls01.generators.BLS01KeyPairGenerator;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.params.BLS01KeyGenerationParameters;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.params.BLS01Parameters;
import it.unisa.dia.gas.crypto.jpbc.signature.bls01.params.BLS01PublicKeyParameters;
import it.unisa.dia.gas.jpbc.CurveParameters;
import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.DefaultCurveParameters;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.ContentInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS12PfxPdu;
import org.bouncycastle.pkcs.PKCS12PfxPduBuilder;
import org.bouncycastle.pkcs.PKCS12SafeBag;
import org.bouncycastle.pkcs.PKCS12SafeBagFactory;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcePKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder;
import org.cryptoworkshop.ximix.common.asn1.XimixObjectIdentifiers;
import org.cryptoworkshop.ximix.common.service.Algorithm;
import org.cryptoworkshop.ximix.common.service.Decoupler;
import org.cryptoworkshop.ximix.common.service.NodeContext;
import org.cryptoworkshop.ximix.common.service.PrivateKeyOperator;
import org.cryptoworkshop.ximix.common.util.DecoupledListenerHandlerFactory;
import org.cryptoworkshop.ximix.common.util.ListenerHandler;
import org.cryptoworkshop.ximix.crypto.key.message.BLSCommittedSecretShareMessage;
import org.cryptoworkshop.ximix.crypto.key.message.ECKeyGenParams;
import org.cryptoworkshop.ximix.crypto.key.util.SubjectPublicKeyInfoFactory;
import org.cryptoworkshop.ximix.crypto.operator.jpbc.JpbcPrivateKeyOperator;
import org.cryptoworkshop.ximix.crypto.threshold.BLSCommittedSecretShare;
import org.cryptoworkshop.ximix.crypto.util.BigIntegerShare;
import org.cryptoworkshop.ximix.crypto.util.ElementShare;
import org.cryptoworkshop.ximix.crypto.util.Share;
import org.cryptoworkshop.ximix.crypto.util.ShareMap;
import org.cryptoworkshop.ximix.crypto.util.ShareMapListener;

public class BLSKeyManager
    implements KeyManager
{
    private static final int TIME_OUT = 20;

    private final Map<String, BLS01Parameters> paramsMap = new HashMap<>();
    private final Map<String, BigInteger> hMap = new HashMap<>();
    private final Set<String> signingKeys = new HashSet<>();
    private final ShareMap<String, BigInteger> sharedPrivateKeyMap;
    private final ShareMap<String, Element> sharedPublicKeyMap;
    private final NodeContext nodeContext;
    private final ListenerHandler<KeyManagerListener> listenerHandler;
    private final KeyManagerListener notifier;

    public BLSKeyManager(NodeContext nodeContext)
    {
        this.nodeContext = nodeContext;
        this.listenerHandler = new DecoupledListenerHandlerFactory(nodeContext.getDecoupler(Decoupler.LISTENER)).createHandler(KeyManagerListener.class);
        this.notifier = listenerHandler.getNotifier();

        sharedPublicKeyMap = new ShareMap<>(nodeContext.getScheduledExecutor(), nodeContext.getDecoupler(Decoupler.SHARING));
        sharedPrivateKeyMap = new ShareMap<>(nodeContext.getScheduledExecutor(), nodeContext.getDecoupler(Decoupler.SHARING));

        sharedPrivateKeyMap.addListener(new ShareMapListener<String, BigInteger>()
        {
            @Override
            public void shareCompleted(ShareMap<String, BigInteger> shareMap, String id)
            {
                notifier.keyAdded(BLSKeyManager.this, id);
            }
        });
    }

    @Override
    public String getID()
    {
        return "BLS";
    }

    @Override
    public synchronized boolean hasPrivateKey(String keyID)
    {
        return sharedPrivateKeyMap.containsKey(keyID);
    }

    @Override
    public synchronized boolean isSigningKey(String keyID)
    {
        return true;
    }

    public BLS01Parameters getParams(String keyID)
    {
        return paramsMap.get(keyID);
    }

    public synchronized AsymmetricCipherKeyPair generateKeyPair(String keyID, Algorithm algorithm, int numberOfPeers, ECKeyGenParams keyGenParams)
    {
        BLS01Parameters domainParameters = paramsMap.get(keyID);

        if (domainParameters == null)
        {
            BLS01KeyPairGenerator kpGen = new BLS01KeyPairGenerator();
            CurveParameters       curveParameters = new DefaultCurveParameters().load(this.getClass().getResourceAsStream("d62003-159-158.param"));
            Pairing               pairing = PairingFactory.getInstance().getPairing(curveParameters);
            BLS01Parameters       blsParameters = new BLS01Parameters(curveParameters, pairing.getG2().newRandomElement());

            // TODO: need to sort out source of randomness.

            kpGen.init(new BLS01KeyGenerationParameters(new SecureRandom(), blsParameters));

            AsymmetricCipherKeyPair kp =  kpGen.generateKeyPair();

            sharedPrivateKeyMap.init(keyID, numberOfPeers);
            sharedPublicKeyMap.init(keyID, numberOfPeers);

            hMap.put(keyID, keyGenParams.getH());
            paramsMap.put(keyID, blsParameters);

            return kp;
        }
        else
        {
            throw new IllegalStateException("Key " + keyID + " already exists.");
        }
    }

    @Override
    public SubjectPublicKeyInfo fetchPublicKey(String keyID)
        throws IOException
    {
        if (sharedPublicKeyMap.containsKey(keyID))
        {
            Element pK = sharedPublicKeyMap.getShare(keyID, TIME_OUT, TimeUnit.SECONDS).getValue();
            BLS01Parameters params = paramsMap.get(keyID);
                                                                                  // TODO;
            return SubjectPublicKeyInfo.getInstance(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(new BLS01PublicKeyParameters(params, pK)).getEncoded());
        }

        return null;
    }

    public synchronized void buildSharedKey(String keyID, BLSCommittedSecretShareMessage message)
    {
        BLS01Parameters domainParams = paramsMap.get(keyID);
        BLSCommittedSecretShare share = new BLSCommittedSecretShare(message.getValue(), message.getWitness(), message.getCommitmentFactors());

        // TODO:
//        if (share.isRevealed(message.getIndex(), domainParams, hMap.get(keyID)))
//        {
            sharedPrivateKeyMap.addValue(keyID, new BigIntegerShare(message.getIndex(), message.getValue()));
            sharedPublicKeyMap.addValue(keyID, new ElementShare(message.getIndex(), message.getPk()));
//        }
//        else
//        {
//            throw new IllegalStateException("Commitment for " + keyID + " failed!");
//        }
    }

    public BigInteger getPartialPrivateKey(String keyID)
    {
        return null;
//        return sharedPrivateKeyMap.getShare(keyID).getValue();
    }

    public synchronized byte[] getEncoded(char[] password)
        throws IOException, GeneralSecurityException
    {
        KeyFactory fact = KeyFactory.getInstance("ECDSA", "BC");

        try
        {
            OutputEncryptor encOut = new JcePKCSPBEOutputEncryptorBuilder(NISTObjectIdentifiers.id_aes256_CBC).setProvider("BC").build(password);


            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            PKCS12PfxPduBuilder builder = new PKCS12PfxPduBuilder();

            for (String keyID : sharedPrivateKeyMap.getIDs())
            {
//                ECDomainParameters domainParams = paramsMap.get(keyID);
//                PrivateKey privKey = fact.generatePrivate(
//                           new PKCS8EncodedKeySpec(
//                                PrivateKeyInfoFactory.createPrivateKeyInfo(
//                                    new ECPrivateKeyParameters(sharedPrivateKeyMap.getShare(keyID).getValue(), domainParams)).getEncoded()));
                SubjectPublicKeyInfo pubKey = this.fetchPublicKey(keyID);

//                PKCS12SafeBagBuilder eeCertBagBuilder = new PKCS12SafeBagBuilder(createCertificate(
//                                                                 keyID, sharedPrivateKeyMap.getShare(keyID).getSequenceNo(), privKey));

//                eeCertBagBuilder.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, new DERBMPString(keyID));

                SubjectKeyIdentifier pubKeyId = extUtils.createSubjectKeyIdentifier(pubKey);

//                eeCertBagBuilder.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, pubKeyId);

//                PKCS12SafeBagBuilder keyBagBuilder = new JcaPKCS12SafeBagBuilder(privKey, encOut);
//
//                keyBagBuilder.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, new DERBMPString(keyID));
//                keyBagBuilder.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, pubKeyId);
//
//                builder.addEncryptedData(new JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd128BitRC2_CBC).setProvider("BC").build(password), new PKCS12SafeBag[] { eeCertBagBuilder.build() });
//
//                builder.addData(keyBagBuilder.build());
            }

            PKCS12PfxPdu pfx = builder.build(new JcePKCS12MacCalculatorBuilder(NISTObjectIdentifiers.id_sha256), password);

            return pfx.getEncoded(ASN1Encoding.DL);
        }
        catch (PKCSException e)
        {
            throw new GeneralSecurityException("Unable to create key store: " + e.getMessage(), e);
        }
        catch (OperatorCreationException e)
        {
            throw new GeneralSecurityException("Unable to create operator: " + e.getMessage(), e);
        }
    }

    public synchronized void load(char[] password, byte[] encoding)
        throws IOException, GeneralSecurityException
    {
        try
        {
            PKCS12PfxPdu pfx = new PKCS12PfxPdu(encoding);
            InputDecryptorProvider inputDecryptorProvider = new JcePKCSPBEInputDecryptorProviderBuilder()
                .setProvider("BC").build(password);
            ContentInfo[] infos = pfx.getContentInfos();

            for (int i = 0; i != infos.length; i++)
            {
                if (infos[i].getContentType().equals(PKCSObjectIdentifiers.encryptedData))
                {
                    PKCS12SafeBagFactory dataFact = new PKCS12SafeBagFactory(infos[i], inputDecryptorProvider);

                    PKCS12SafeBag[] bags = dataFact.getSafeBags();

                    Attribute[] attributes = bags[0].getAttributes();

                    X509CertificateHolder cert = (X509CertificateHolder)bags[0].getBagValue();

                    String keyID = getKeyID(attributes);
                    ECPublicKeyParameters publicKeyParameters = (ECPublicKeyParameters)PublicKeyFactory.createKey(cert.getSubjectPublicKeyInfo());

//                    paramsMap.put(keyID, publicKeyParameters.getParameters());
//                    sharedPublicKeyMap.init(keyID, 0);
//                    sharedPublicKeyMap.addValue(keyID, new ECPointShare(
//                        ASN1Integer.getInstance(cert.getExtension(XimixObjectIdentifiers.ximixShareIdExtension).getParsedValue()).getValue().intValue(),
//                        publicKeyParameters.getQ()));

                    if (KeyUsage.fromExtensions(cert.getExtensions()).hasUsages(KeyUsage.digitalSignature))
                    {
                        signingKeys.add(keyID);
                    }
                }
                else
                {
                    PKCS12SafeBagFactory dataFact = new PKCS12SafeBagFactory(infos[i]);

                    PKCS12SafeBag[] bags = dataFact.getSafeBags();
                    String keyID = getKeyID(bags[0].getAttributes());

                    PKCS8EncryptedPrivateKeyInfo encInfo = (PKCS8EncryptedPrivateKeyInfo)bags[0].getBagValue();
                    PrivateKeyInfo info = encInfo.decryptPrivateKeyInfo(inputDecryptorProvider);

//                    sharedPrivateKeyMap.init(keyID, 0);
//                    sharedPrivateKeyMap.addValue(keyID, new BigIntegerShare(sharedPublicKeyMap.getShare(keyID).getSequenceNo(), ECPrivateKey.getInstance(info.parsePrivateKey()).getKey()));
                }
            }
        }
        catch (PKCSException e)
        {
            throw new GeneralSecurityException("Unable to create key store: " + e.getMessage(), e);
        }
    }

//    public ECDomainParameters geParams(String keyID)
//    {
//        return paramsMap.get(keyID);
//    }

    @Override
    public void addListener(KeyManagerListener listener)
    {
        listenerHandler.addListener(listener);
    }

    @Override
    public PrivateKeyOperator getPrivateKeyOperator(String keyID)
    {
        Share<BigInteger> privateKeyShare = sharedPrivateKeyMap.getShare(keyID);
        if (privateKeyShare == null)
        {
            return null;
        }

        return new JpbcPrivateKeyOperator(privateKeyShare.getSequenceNo(), paramsMap.get(keyID), privateKeyShare.getValue());
    }

    // TODO: in this case we should get the private key from somewhere else - probably node config
    private X509CertificateHolder createCertificate(
        String keyID,
        int sequenceNo,
        PrivateKey privKey)
        throws GeneralSecurityException, OperatorCreationException, IOException
    {
        String name = "C=AU, O=Ximix Network Node, OU=" + nodeContext.getName();

        //
        // create the certificate - version 3
        //
        X509v3CertificateBuilder v3CertBuilder = new X509v3CertificateBuilder(
            new X500Name(name),
            BigInteger.valueOf(1),
            new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30),
            new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365)),
            new X500Name(name),
            this.fetchPublicKey(keyID));

        // we use keyUsage extension to distinguish between signing and encryption keys

        if (signingKeys.contains(keyID))
        {
            v3CertBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        }
        else
        {
            v3CertBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.dataEncipherment));
        }

        v3CertBuilder.addExtension(XimixObjectIdentifiers.ximixShareIdExtension, true, new ASN1Integer(sequenceNo));

        return v3CertBuilder.build(new JcaContentSignerBuilder("SHA1withECDSA").setProvider("BC").build(privKey));
    }

    private String getKeyID(Attribute[] attributes)
    {
        for (Attribute attr : attributes)
        {
            if (PKCS12SafeBag.friendlyNameAttribute.equals(attr.getAttrType()))
            {
                return DERBMPString.getInstance(attr.getAttrValues().getObjectAt(0)).getString();
            }
        }

        throw new IllegalStateException("No friendlyNameAttribute found.");
    }
}