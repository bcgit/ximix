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
package org.cryptoworkshop.ximix.node;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.cryptoworkshop.ximix.common.conf.Config;
import org.cryptoworkshop.ximix.common.conf.ConfigException;
import org.cryptoworkshop.ximix.common.conf.ConfigObjectFactory;
import org.cryptoworkshop.ximix.common.message.Capability;
import org.cryptoworkshop.ximix.common.service.NodeContext;
import org.cryptoworkshop.ximix.common.service.Service;
import org.cryptoworkshop.ximix.common.service.ServicesConnection;
import org.cryptoworkshop.ximix.crypto.service.NodeKeyGenerationService;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XimixNodeContext
    implements NodeContext
{
    private Map<String, ServicesConnection> peerMap;
    private NodeKeyGenerationService keyRetrievalService;
    private final KeyManager keyManager;

    private Executor multiTaskExecutor = Executors.newCachedThreadPool();

    private List<Service> services = new ArrayList<Service>();
    private final String  name;

    public XimixNodeContext(Map<String, ServicesConnection> peerMap, Config nodeConfig)
        throws ConfigException
    {
        this.peerMap = peerMap;
        this.name = Integer.toString(nodeConfig.getIntegerProperty("portNo"));  // TODO:

        List<ServiceConfig> configs = nodeConfig.getConfigObjects("services", new NodeConfigFactory());
        for (ServiceConfig config : configs)
        {
            if (config.getThrowable() != null)
            {
                config.getThrowable().printStackTrace();   // TODO: log!
            }
        }

        keyManager = new KeyManager();
    }

    public String getName()
    {
         return name;
    }

    public Capability[] getCapabilities()
    {
        List<Capability>  capabilityList = new ArrayList<Capability>();

        for (Service service : services)
        {
            capabilityList.add(service.getCapability());
        }

        return capabilityList.toArray(new Capability[capabilityList.size()]);
    }

    public void addConnection(Runnable task)
    {
        multiTaskExecutor.execute(task);
    }

    public Map<String, ServicesConnection> getPeerMap()
    {
        return peerMap;
    }

    public void scheduleTask(Runnable task)
    {
        multiTaskExecutor.execute(task);
    }
                                                                        // TODO: replace with private key info
    public AsymmetricKeyParameter getPrivateKey(String keyID)
    {
        return keyManager.getKeyPair(keyID).getPrivate();
    }

    public SubjectPublicKeyInfo getPublicKey(String keyID)
    {
        try
        {
            return keyManager.fetchPublicKey(keyID);
        }
        catch (IOException e)
        {
            e.printStackTrace();  // TODO:
            return null;
        }
    }

    public Service getService(Enum type)
    {
        for (Service service : services)
        {
            if (service.isAbleToHandle(type))
            {
                return service;
            }
        }

        return null;
    }

    private class ServiceConfig
    {
        private Exception throwable;

        ServiceConfig(Node configNode)
        {
            NodeList xmlNodes = configNode.getChildNodes();

            for (int i = 0; i != xmlNodes.getLength(); i++)
            {
                Node xmlNode = xmlNodes.item(i);

                if (xmlNode.getNodeName().equals("service"))
                {
                    NodeList attributes = xmlNode.getChildNodes();

                    for (int j = 0; j != xmlNodes.getLength(); j++)
                    {
                        Node attrNode = attributes.item(j);

                        if (attrNode == null)
                        {
                            continue;
                        }

                        if (attrNode.getNodeName().equals("implementation"))
                        {
                            try
                            {
                                Class clazz = Class.forName(attrNode.getTextContent());

                                Constructor constructor = clazz.getConstructor(NodeContext.class, Config.class);

                                Service impl = (Service)constructor.newInstance(XimixNodeContext.this, new Config(xmlNode));

                                services.add(impl);
                            }
                            catch (ClassNotFoundException e)
                            {
                                throwable = e;
                            }
                            catch (NoSuchMethodException e)
                            {
                                throwable = e;
                            }
                            catch (InvocationTargetException e)
                            {
                                throwable = e;
                            }
                            catch (InstantiationException e)
                            {
                                throwable = e;
                            }
                            catch (IllegalAccessException e)
                            {
                                throwable = e;
                            }
                            catch (ConfigException e)
                            {
                                throwable = e;
                            }
                        }
                    }
                }
            }
        }

        public Throwable getThrowable()
        {
            return throwable;
        }
    }

    private class NodeConfigFactory
        implements ConfigObjectFactory<ServiceConfig>
    {
        public ServiceConfig createObject(Node configNode)
        {
            return new ServiceConfig(configNode);
        }
    }
}