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

import org.cryptoworkshop.ximix.common.conf.Config;
import org.cryptoworkshop.ximix.common.conf.ConfigException;
import org.cryptoworkshop.ximix.common.service.ServicesConnection;
import org.cryptoworkshop.ximix.registrar.XimixRegistrarFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

public class XimixNodeBuilder
{
    private ThrowableHandler exceptionHandler = new ThrowableHandler()
    {
        @Override
        public void handle(Throwable throwable)
        {
            throwable.printStackTrace(System.err);
        }
    };

    private final Config peersConfig;


    public XimixNodeBuilder(Config peersConfig)
    {
        this.peersConfig = peersConfig;
    }

    public XimixNodeBuilder(File file)
        throws ConfigException, FileNotFoundException
    {
        this(new Config(file));
    }

    public XimixNodeBuilder withThrowableHandler(ThrowableHandler exceptionHandler)
    {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public XimixNode build(Config nodeConfig)
        throws ConfigException
    {
        final Map<String, ServicesConnection> servicesMap = XimixRegistrarFactory.createServicesRegistrarMap(peersConfig);
        return new DefaultXimixNode(nodeConfig, servicesMap, exceptionHandler);
    }

    public XimixNode build(File file)
        throws ConfigException, FileNotFoundException
    {
        return build(new Config(file));
    }
}