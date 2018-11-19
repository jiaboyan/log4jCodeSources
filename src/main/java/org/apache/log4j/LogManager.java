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

    // log4j初始化第一步：LogManager最终将LoggerRepository和Configurator整合在一起。
    static {
        //以DEBUG等级创建一个RootLogger,然后以RootLogger为参数创建一个Hierarchy类的实例（Logger的容器）。
        Hierarchy h = new Hierarchy(new RootLogger((Level) Level.DEBUG));
        //以Hierarchy为参数创建一个DefaultRepositorySelector类的实例：
        // DefaultRepositorySelector实现了RepositorySelector接口，RepositorySelector提供了一个获取LoggerRepository的方法。
        //通过该对象可以获取logger容器对象：
        repositorySelector = new DefaultRepositorySelector(h);
        //检查系统属性 log4j.defaultInitOverride ，如果该属性被设置为false或为null，则执行初始化。
        String override = OptionConverter.getSystemProperty(DEFAULT_INIT_OVERRIDE_KEY, null);
        if (override == null || "false".equalsIgnoreCase(override)) {

            //获取 log4j.configuration 属性：log4j已经不推荐设置这系统变量
            String configurationOptionStr = OptionConverter.getSystemProperty(DEFAULT_CONFIGURATION_KEY, null);
            //获取 log4j.configuratorClass 属性：log4j已经不推荐设置这系统变量
            String configuratorClassName = OptionConverter.getSystemProperty(CONFIGURATOR_CLASS_KEY, null);
            URL url = null;

            // 获取 log4j.xml、log4j.properties 配置文件：首先获取的是log4j.xml文件：
            if (configurationOptionStr == null) {
                url = Loader.getResource(DEFAULT_XML_CONFIGURATION_FILE);
                //如果不存在，则获取log4j.properties文件：
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
                    //进行配置文件解析器的选择：
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

    //获取日志工厂：
    static
    public LoggerRepository getLoggerRepository() {
        // DefaultRepositorySelector实现了RepositorySelector接口，
        // RepositorySelector提供了一个获取LoggerRepository的方法。通过该对象可以获取logger容器对象：
        // 在LogManage初始化时候，repositorySelector已被赋值为 DefaultRepositorySelector
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
        //获取日志仓库，也就是LogManage静态代码块中创建DefaultRepositorySelector对象时构造传入的Hierarchy对象（Logger容器）
        //返回的是Hierarchy对象：
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

    //通过类名称，获取对应的Logger对象：log4j获取日志对象的方法：（第一步）
    public static Logger getLogger(final String name) {
        //获取日志仓库，通过仓库获取对应日志对象：getLoggerRepository()得到的是Hierarchy对象：
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

