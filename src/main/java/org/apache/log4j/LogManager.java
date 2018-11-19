/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j;

import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.RepositorySelector;
import org.apache.log4j.spi.DefaultRepositorySelector;
import org.apache.log4j.spi.RootLogger;
import org.apache.log4j.spi.NOPLoggerRepository;
import org.apache.log4j.helpers.Loader;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.helpers.LogLog;

import java.net.URL;
import java.net.MalformedURLException;


import java.util.Enumeration;
import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * Use the <code>LogManager</code> class to retreive {@link Logger}
 * instances or to operate on the current {@link
 * LoggerRepository}. When the <code>LogManager</code> class is loaded
 * into memory the default initalzation procedure is inititated. The
 * default intialization procedure</a> is described in the <a
 * href="../../../../manual.html#defaultInit">short log4j manual</a>.
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class LogManager {

    /**
     * @deprecated This variable is for internal use only. It will
     * become package protected in future versions.
     */
    static public final String DEFAULT_CONFIGURATION_FILE = "log4j.properties";

    static final String DEFAULT_XML_CONFIGURATION_FILE = "log4j.xml";

    /**
     * @deprecated This variable is for internal use only. It will
     * become private in future versions.
     */
    static final public String DEFAULT_CONFIGURATION_KEY = "log4j.configuration";

    /**
     * @deprecated This variable is for internal use only. It will
     * become private in future versions.
     */
    static final public String CONFIGURATOR_CLASS_KEY = "log4j.configuratorClass";

    /**
     * @deprecated This variable is for internal use only. It will
     * become private in future versions.
     */
    public static final String DEFAULT_INIT_OVERRIDE_KEY = "log4j.defaultInitOverride";


    static private Object guard = null;
    static private RepositorySelector repositorySelector;

    // log4j��ʼ����һ����LogManager���ս�LoggerRepository��Configurator������һ��
    static {
        //��DEBUG�ȼ�����һ��RootLogger,Ȼ����RootLoggerΪ��������һ��Hierarchy���ʵ����Logger����������
        Hierarchy h = new Hierarchy(new RootLogger((Level) Level.DEBUG));
        //��HierarchyΪ��������һ��DefaultRepositorySelector���ʵ����
        // DefaultRepositorySelectorʵ����RepositorySelector�ӿڣ�RepositorySelector�ṩ��һ����ȡLoggerRepository�ķ�����
        //ͨ���ö�����Ի�ȡlogger��������
        repositorySelector = new DefaultRepositorySelector(h);
        //���ϵͳ���� log4j.defaultInitOverride ����������Ա�����Ϊfalse��Ϊnull����ִ�г�ʼ����
        String override = OptionConverter.getSystemProperty(DEFAULT_INIT_OVERRIDE_KEY, null);
        if (override == null || "false".equalsIgnoreCase(override)) {

            //��ȡ log4j.configuration ���ԣ�log4j�Ѿ����Ƽ�������ϵͳ����
            String configurationOptionStr = OptionConverter.getSystemProperty(DEFAULT_CONFIGURATION_KEY, null);
            //��ȡ log4j.configuratorClass ���ԣ�log4j�Ѿ����Ƽ�������ϵͳ����
            String configuratorClassName = OptionConverter.getSystemProperty(CONFIGURATOR_CLASS_KEY, null);
            URL url = null;

            // ��ȡ log4j.xml��log4j.properties �����ļ������Ȼ�ȡ����log4j.xml�ļ���
            if (configurationOptionStr == null) {
                url = Loader.getResource(DEFAULT_XML_CONFIGURATION_FILE);
                //��������ڣ����ȡlog4j.properties�ļ���
                if (url == null) {
                    url = Loader.getResource(DEFAULT_CONFIGURATION_FILE);
                }
            } else {
                try {
                    url = new URL(configurationOptionStr);
                } catch (MalformedURLException ex) {
                    url = Loader.getResource(configurationOptionStr);
                }
            }
            if (url != null) {
                LogLog.debug("Using URL [" + url + "] for automatic log4j configuration.");
                try {
                    //���������ļ���������ѡ��
                    OptionConverter.selectAndConfigure(url, configuratorClassName, LogManager.getLoggerRepository());
                } catch (NoClassDefFoundError e) {
                    LogLog.warn("Error during default initialization", e);
                }
            } else {
                LogLog.debug("Could not find resource: [" + configurationOptionStr + "].");
            }
        } else {
            LogLog.debug("Default initialization of overridden by " +
                    DEFAULT_INIT_OVERRIDE_KEY + "property.");
        }
    }

    /**
     * Sets <code>LoggerFactory</code> but only if the correct
     * <em>guard</em> is passed as parameter.
     * <p/>
     * <p>Initally the guard is null.  If the guard is
     * <code>null</code>, then invoking this method sets the logger
     * factory and the guard. Following invocations will throw a {@link
     * IllegalArgumentException}, unless the previously set
     * <code>guard</code> is passed as the second parameter.
     * <p/>
     * <p>This allows a high-level component to set the {@link
     * RepositorySelector} used by the <code>LogManager</code>.
     * <p/>
     * <p>For example, when tomcat starts it will be able to install its
     * own repository selector. However, if and when Tomcat is embedded
     * within JBoss, then JBoss will install its own repository selector
     * and Tomcat will use the repository selector set by its container,
     * JBoss.
     */
    static
    public void setRepositorySelector(RepositorySelector selector, Object guard)
            throws IllegalArgumentException {
        if ((LogManager.guard != null) && (LogManager.guard != guard)) {
            throw new IllegalArgumentException(
                    "Attempted to reset the LoggerFactory without possessing the guard.");
        }

        if (selector == null) {
            throw new IllegalArgumentException("RepositorySelector must be non-null.");
        }

        LogManager.guard = guard;
        LogManager.repositorySelector = selector;
    }


    /**
     * This method tests if called from a method that
     * is known to result in class members being abnormally
     * set to null but is assumed to be harmless since the
     * all classes are in the process of being unloaded.
     *
     * @param ex exception used to determine calling stack.
     * @return true if calling stack is recognized as likely safe.
     */
    private static boolean isLikelySafeScenario(final Exception ex) {
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        String msg = stringWriter.toString();
        return msg.indexOf("org.apache.catalina.loader.WebappClassLoader.stop") != -1;
    }

    //��ȡ��־������
    static
    public LoggerRepository getLoggerRepository() {
        // DefaultRepositorySelectorʵ����RepositorySelector�ӿڣ�
        // RepositorySelector�ṩ��һ����ȡLoggerRepository�ķ�����ͨ���ö�����Ի�ȡlogger��������
        // ��LogManage��ʼ��ʱ��repositorySelector�ѱ���ֵΪ DefaultRepositorySelector
        if (repositorySelector == null) {
            repositorySelector = new DefaultRepositorySelector(new NOPLoggerRepository());
            guard = null;
            Exception ex = new IllegalStateException("Class invariant violation");
            String msg = "log4j called after unloading, see http://logging.apache.org/log4j/1.2/faq.html#unload.";
            if (isLikelySafeScenario(ex)) {
                LogLog.debug(msg, ex);
            } else {
                LogLog.error(msg, ex);
            }
        }
        //��ȡ��־�ֿ⣬Ҳ����LogManage��̬������д���DefaultRepositorySelector����ʱ���촫���Hierarchy����Logger������
        //���ص���Hierarchy����
        return repositorySelector.getLoggerRepository();
    }

    /**
     * Retrieve the appropriate root logger.
     */
    public
    static Logger getRootLogger() {
        // Delegate the actual manufacturing of the logger to the logger repository.
        return getLoggerRepository().getRootLogger();
    }

    //ͨ�������ƣ���ȡ��Ӧ��Logger����log4j��ȡ��־����ķ���������һ����
    public static Logger getLogger(final String name) {
        //��ȡ��־�ֿ⣬ͨ���ֿ��ȡ��Ӧ��־����getLoggerRepository()�õ�����Hierarchy����
        return getLoggerRepository().getLogger(name);
    }

    public static Logger getLogger(final Class clazz) {
        // Delegate the actual manufacturing of the logger to the logger repository.
        return getLoggerRepository().getLogger(clazz.getName());
    }


    /**
     * Retrieve the appropriate {@link Logger} instance.
     */
    public
    static Logger getLogger(final String name, final LoggerFactory factory) {
        // Delegate the actual manufacturing of the logger to the logger repository.
        return getLoggerRepository().getLogger(name, factory);
    }

    public
    static Logger exists(final String name) {
        return getLoggerRepository().exists(name);
    }

    public
    static Enumeration getCurrentLoggers() {
        return getLoggerRepository().getCurrentLoggers();
    }

    public
    static void shutdown() {
        getLoggerRepository().shutdown();
    }

    public
    static void resetConfiguration() {
        getLoggerRepository().resetConfiguration();
    }
}

