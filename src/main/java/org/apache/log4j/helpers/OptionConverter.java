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

package org.apache.log4j.helpers;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.Configurator;
import org.apache.log4j.spi.LoggerRepository;

// Contributors:   Avy Sharell (sharell@online.fr)
//                 Matthieu Verbert (mve@zurich.ibm.com)
//                 Colin Sampaleanu

/**
 * A convenience class to convert property values to specific types.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Simon Kitching;
 * @author Anders Kristensen
 */
public class OptionConverter {

    static String DELIM_START = "${";
    static char DELIM_STOP = '}';
    static int DELIM_START_LEN = 2;
    static int DELIM_STOP_LEN = 1;

    /**
     * OptionConverter is a static class.
     */
    private OptionConverter() {
    }

    public
    static String[] concatanateArrays(String[] l, String[] r) {
        int len = l.length + r.length;
        String[] a = new String[len];

        System.arraycopy(l, 0, a, 0, l.length);
        System.arraycopy(r, 0, a, l.length, r.length);

        return a;
    }

    public
    static String convertSpecialChars(String s) {
        char c;
        int len = s.length();
        StringBuffer sbuf = new StringBuffer(len);

        int i = 0;
        while (i < len) {
            c = s.charAt(i++);
            if (c == '\\') {
                c = s.charAt(i++);
                if (c == 'n') c = '\n';
                else if (c == 'r') c = '\r';
                else if (c == 't') c = '\t';
                else if (c == 'f') c = '\f';
                else if (c == '\b') c = '\b';
                else if (c == '\"') c = '\"';
                else if (c == '\'') c = '\'';
                else if (c == '\\') c = '\\';
            }
            sbuf.append(c);
        }
        return sbuf.toString();
    }


    /**
     * Very similar to <code>System.getProperty</code> except
     * that the {@link SecurityException} is hidden.
     *
     * @param key The key to search for.
     * @param def The default value to return.
     * @return the string value of the system property, or the default
     * value if there is no property with that key.
     * @since 1.1
     */
    public
    static String getSystemProperty(String key, String def) {
        try {
            return System.getProperty(key, def);
        } catch (Throwable e) { // MS-Java throws com.ms.security.SecurityExceptionEx
            LogLog.debug("Was not allowed to read system property \"" + key + "\".");
            return def;
        }
    }

    //通过key找value,进行实例化操作：
    public static Object instantiateByKey(Properties props, String key, Class superClass, Object defaultValue) {
        //通过key,找对应的value
        String className = findAndSubst(key, props);
        //如果配置文件中设置的  log4j.appender.FILE 的值为空：返回默认的；
        if (className == null) {
            LogLog.error("Could not find value for key " + key);
            return defaultValue;
        }
        //进行实例化操作：
        return OptionConverter.instantiateByClassName(className.trim(), superClass, defaultValue);
    }

    /**
     * If <code>value</code> is "true", then <code>true</code> is
     * returned. If <code>value</code> is "false", then
     * <code>true</code> is returned. Otherwise, <code>default</code> is
     * returned.
     * <p/>
     * <p>Case of value is unimportant.
     */
    public
    static boolean toBoolean(String value, boolean dEfault) {
        if (value == null)
            return dEfault;
        String trimmedVal = value.trim();
        if ("true".equalsIgnoreCase(trimmedVal))
            return true;
        if ("false".equalsIgnoreCase(trimmedVal))
            return false;
        return dEfault;
    }

    public
    static int toInt(String value, int dEfault) {
        if (value != null) {
            String s = value.trim();
            try {
                return Integer.valueOf(s).intValue();
            } catch (NumberFormatException e) {
                LogLog.error("[" + s + "] is not in proper int form.");
                e.printStackTrace();
            }
        }
        return dEfault;
    }

    //返回日志级别：
    public static Level toLevel(String value, Level defaultValue) {
        //如果设置的日志级别value为空 ：
        if (value == null) {
            //返回默认的日志级别：
            return defaultValue;
        }
        //忽略value中的空格：
        value = value.trim();
        //获取value中的'#'字符存在的角标：
        int hashIndex = value.indexOf('#');
        //如果不存在：
        if (hashIndex == -1) {
            //判断value是否为NULL：
            if ("NULL".equalsIgnoreCase(value)) {
                return null;
            } else {
                //将字符串value转换为Level对象：并返回；
                return (Level) Level.toLevel(value, defaultValue);
            }
        }
        Level result = defaultValue;
        String clazz = value.substring(hashIndex + 1);
        String levelName = value.substring(0, hashIndex);
        if ("NULL".equalsIgnoreCase(levelName)) {
            return null;
        }
        LogLog.debug("toLevel" + ":class=[" + clazz + "]" + ":pri=[" + levelName + "]");
        try {
            Class customLevel = Loader.loadClass(clazz);
            Class[] paramTypes = new Class[]{String.class, org.apache.log4j.Level.class};
            java.lang.reflect.Method toLevelMethod = customLevel.getMethod("toLevel", paramTypes);
            Object[] params = new Object[]{levelName, defaultValue};
            Object o = toLevelMethod.invoke(null, params);
            result = (Level) o;
        } catch (ClassNotFoundException e) {
            LogLog.warn("custom level class [" + clazz + "] not found.");
        } catch (NoSuchMethodException e) {
            LogLog.warn("custom level class [" + clazz + "]" + " does not have a class function toLevel(String, Level)", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getTargetException() instanceof InterruptedException || e.getTargetException() instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            LogLog.warn("custom level class [" + clazz + "]" + " could not be instantiated", e);
        } catch (ClassCastException e) {
            LogLog.warn("class [" + clazz + "] is not a subclass of org.apache.log4j.Level", e);
        } catch (IllegalAccessException e) {
            LogLog.warn("class [" + clazz + "] cannot be instantiated due to access restrictions", e);
        } catch (RuntimeException e) {
            LogLog.warn("class [" + clazz + "], level [" + levelName + "] conversion failed.", e);
        }
        return result;
    }

    public
    static long toFileSize(String value, long dEfault) {
        if (value == null)
            return dEfault;

        String s = value.trim().toUpperCase();
        long multiplier = 1;
        int index;

        if ((index = s.indexOf("KB")) != -1) {
            multiplier = 1024;
            s = s.substring(0, index);
        } else if ((index = s.indexOf("MB")) != -1) {
            multiplier = 1024 * 1024;
            s = s.substring(0, index);
        } else if ((index = s.indexOf("GB")) != -1) {
            multiplier = 1024 * 1024 * 1024;
            s = s.substring(0, index);
        }
        if (s != null) {
            try {
                return Long.valueOf(s).longValue() * multiplier;
            } catch (NumberFormatException e) {
                LogLog.error("[" + s + "] is not in proper int form.");
                LogLog.error("[" + value + "] not in expected format.", e);
            }
        }
        return dEfault;
    }

    /**
     * Find the value corresponding to <code>key</code> in
     * <code>props</code>. Then perform variable substitution on the
     * found value.
     */
    public static String findAndSubst(String key, Properties props) {
        String value = props.getProperty(key);
        if (value == null) {
            return null;
        }
        try {
            return substVars(value, props);
        } catch (IllegalArgumentException e) {
            LogLog.error("Bad option value [" + value + "].", e);
            return value;
        }
    }

    //通过反射，实例化Class对象：
    public static Object instantiateByClassName(String className, Class superClass, Object defaultValue) {
        if (className != null) {
            try {
                //Loader加载对应的 class文件：
                Class classObj = Loader.loadClass(className);
                if (!superClass.isAssignableFrom(classObj)) {
                    LogLog.error("A \"" + className + "\" object is not assignable to a \"" + superClass.getName() + "\" variable.");
                    LogLog.error("The class \"" + superClass.getName() + "\" was loaded by ");
                    LogLog.error("[" + superClass.getClassLoader() + "] whereas object of type ");
                    LogLog.error("\"" + classObj.getName() + "\" was loaded by [" + classObj.getClassLoader() + "].");
                    return defaultValue;
                }
                //反射，进行实例化：
                return classObj.newInstance();
            } catch (ClassNotFoundException e) {
                LogLog.error("Could not instantiate class [" + className + "].", e);
            } catch (IllegalAccessException e) {
                LogLog.error("Could not instantiate class [" + className + "].", e);
            } catch (InstantiationException e) {
                LogLog.error("Could not instantiate class [" + className + "].", e);
            } catch (RuntimeException e) {
                LogLog.error("Could not instantiate class [" + className + "].", e);
            }
        }
        return defaultValue;
    }


    public static String substVars(String val, Properties props) throws IllegalArgumentException {
        StringBuffer sbuf = new StringBuffer();
        int i = 0;
        int j, k;
        while (true) {
            j = val.indexOf(DELIM_START, i);
            if (j == -1) {
                if (i == 0) {
                    return val;
                } else {
                    sbuf.append(val.substring(i, val.length()));
                    return sbuf.toString();
                }
            } else {
                sbuf.append(val.substring(i, j));
                k = val.indexOf(DELIM_STOP, j);
                if (k == -1) {
                    throw new IllegalArgumentException('"' + val + "\" has no closing brace. Opening brace at position " + j + '.');
                } else {
                    j += DELIM_START_LEN;
                    String key = val.substring(j, k);
                    String replacement = getSystemProperty(key, null);
                    if (replacement == null && props != null) {
                        replacement = props.getProperty(key);
                    }

                    if (replacement != null) {
                        String recursiveReplacement = substVars(replacement, props);
                        sbuf.append(recursiveReplacement);
                    }
                    i = k + DELIM_STOP_LEN;
                }
            }
        }
    }

    /**
     * Configure log4j given an {@link InputStream}.
     * <p/>
     * <p>
     * The InputStream will be interpreted by a new instance of a log4j configurator.
     * </p>
     * <p>
     * All configurations steps are taken on the <code>hierarchy</code> passed as a parameter.
     * </p>
     *
     * @param inputStream The configuration input stream.
     * @param clazz       The class name, of the log4j configurator which will parse the <code>inputStream</code>. This must be a
     *                    subclass of {@link Configurator}, or null. If this value is null then a default configurator of
     *                    {@link PropertyConfigurator} is used.
     * @param hierarchy   The {@link org.apache.log4j.Hierarchy} to act on.
     * @since 1.2.17
     */

    static
    public void selectAndConfigure(InputStream inputStream, String clazz, LoggerRepository hierarchy) {
        Configurator configurator = null;

        if (clazz != null) {
            LogLog.debug("Preferred configurator class: " + clazz);
            configurator = (Configurator) instantiateByClassName(clazz,
                    Configurator.class,
                    null);
            if (configurator == null) {
                LogLog.error("Could not instantiate configurator [" + clazz + "].");
                return;
            }
        } else {
            configurator = new PropertyConfigurator();
        }

        configurator.doConfigure(inputStream, hierarchy);
    }


    //进行配置文件解析器的选择：Log4J支持两种配置文件：properties文件和xml文件。
    static public void selectAndConfigure(URL url, String clazz, LoggerRepository hierarchy) {
        //解析log4j配置文件对象的接口：
        Configurator configurator = null;
        //获取配置文件的名称：
        String filename = url.getFile();

        //配置文件為xml結尾：就用DOMConfigurator對象進行解析;
        if (clazz == null && filename != null && filename.endsWith(".xml")) {
            clazz = "org.apache.log4j.xml.DOMConfigurator";
        }
        //如果传递过来的 从系统变量中获取的 log4j.configuratorClass的值不为空：
        if (clazz != null) {
            LogLog.debug("Preferred configurator class: " + clazz);
            //实例化该值：
            configurator = (Configurator) instantiateByClassName(clazz, Configurator.class, null);
            //如果该值没实例化成功，则直接返回：
            if (configurator == null) {
                LogLog.error("Could not instantiate configurator [" + clazz + "].");
                return;
            }
        } else {
            //如果为log4j.properties文件：
            configurator = new PropertyConfigurator();
        }
        //进行文件解析：
        configurator.doConfigure(url, hierarchy);
    }
}
