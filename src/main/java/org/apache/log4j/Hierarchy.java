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

// WARNING This class MUST not have references to the Category or
// WARNING RootCategory classes in its static initiliazation neither
// WARNING directly nor indirectly.

// Contributors:
//                Luke Blanshard <luke@quiq.com>
//                Mario Schomburg - IBM Global Services/Germany
//                Anders Kristensen
//                Igor Poteryaev

package org.apache.log4j;


import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.HierarchyEventListener;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.RendererSupport;
import org.apache.log4j.or.RendererMap;
import org.apache.log4j.or.ObjectRenderer;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.spi.ThrowableRenderer;

public class Hierarchy implements LoggerRepository, RendererSupport, ThrowableRendererSupport {

    private LoggerFactory defaultFactory;
    private Vector listeners;

    Hashtable ht;
    Logger root;
    RendererMap rendererMap;

    int thresholdInt;
    Level threshold;

    boolean emittedNoAppenderWarning = false;
    boolean emittedNoResourceBundleWarning = false;
    private ThrowableRenderer throwableRenderer = null;

    /**
     * ����һ��Hierarchy���󣺸���ʵ��LoggerRepository�ӿ�(log����)��
     * Hierarchy����һ��Hashtable���洢����Loggerʵ��������CategoryKey��Ϊkey��Logger��Ϊvalue��
     * ����CategoryKey�Ƕ�Logger������Name�ַ����ķ�װ�����������ԣ�һ����name,һ����hashcode��
     * ���Ỻ������Name�ַ�����hash code�������ڲ��ҹ����У�
     * ֱ�Ӽ����hash code���Ϳ���ֱ�ӻ�ö�Ӧ������������ܣ�
     */
    public Hierarchy(Logger root) {
        ht = new Hashtable();
        listeners = new Vector(1);
        this.root = root;
        // Enable all level levels by default.
        setThreshold(Level.ALL);
        this.root.setHierarchy(this);
        rendererMap = new RendererMap();
        defaultFactory = new DefaultCategoryFactory();
    }

    /**
     * Add an object renderer for a specific class.
     */
    public void addRenderer(Class classToRender, ObjectRenderer or) {
        rendererMap.put(classToRender, or);
    }

    public void addHierarchyEventListener(HierarchyEventListener listener) {
        if (listeners.contains(listener)) {
            LogLog.warn("Ignoring attempt to add an existent listener.");
        } else {
            listeners.addElement(listener);
        }
    }

    /**
     * This call will clear all logger definitions from the internal
     * hashtable. Invoking this method will irrevocably mess up the
     * logger hierarchy.
     * <p/>
     * <p>You should <em>really</em> know what you are doing before
     * invoking this method.
     *
     * @since 0.9.0
     */
    public void clear() {
        //System.out.println("\n\nAbout to clear internal hash table.");
        ht.clear();
    }

    public void emitNoAppenderWarning(Category cat) {
        // No appenders in hierarchy, warn user only once.
        if (!this.emittedNoAppenderWarning) {
            LogLog.warn("No appenders could be found for logger (" +
                    cat.getName() + ").");
            LogLog.warn("Please initialize the log4j system properly.");
            LogLog.warn("See http://logging.apache.org/log4j/1.2/faq.html#noconfig for more info.");
            this.emittedNoAppenderWarning = true;
        }
    }

    /**
     * Check if the named logger exists in the hierarchy. If so return
     * its reference, otherwise returns <code>null</code>.
     *
     * @param name The name of the logger to search for.
     */
    public Logger exists(String name) {
        Object o = ht.get(new CategoryKey(name));
        if (o instanceof Logger) {
            return (Logger) o;
        } else {
            return null;
        }
    }

    /**
     * The string form of {@link #setThreshold(Level)}.
     */
    public void setThreshold(String levelStr) {
        Level l = (Level) Level.toLevel(levelStr, null);
        if (l != null) {
            setThreshold(l);
        } else {
            LogLog.warn("Could not convert [" + levelStr + "] to Level.");
        }
    }


    /**
     * Enable logging for logging requests with level <code>l</code> or
     * higher. By default all levels are enabled.
     *
     * @param l The minimum level for which logging requests are sent to
     *          their appenders.
     */
    public void setThreshold(Level l) {
        if (l != null) {
            thresholdInt = l.level;
            threshold = l;
        }
    }

    public void fireAddAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            int size = listeners.size();
            HierarchyEventListener listener;
            for (int i = 0; i < size; i++) {
                listener = (HierarchyEventListener) listeners.elementAt(i);
                listener.addAppenderEvent(logger, appender);
            }
        }
    }

    void fireRemoveAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            int size = listeners.size();
            HierarchyEventListener listener;
            for (int i = 0; i < size; i++) {
                listener = (HierarchyEventListener) listeners.elementAt(i);
                listener.removeAppenderEvent(logger, appender);
            }
        }
    }

    /**
     * Returns a {@link Level} representation of the <code>enable</code>
     * state.
     *
     * @since 1.2
     */
    public Level getThreshold() {
        return threshold;
    }

    /**
     Returns an integer representation of the this repository's
     threshold.

     @since 1.2 */
    //public
    //int getThresholdInt() {
    //  return thresholdInt;
    //}


    //ʵ�ʻ�ȡ��־����ĵط���
    public Logger getLogger(String name) {
        return getLogger(name, defaultFactory);
    }

    //��ȡ��־����
    public Logger getLogger(String name, LoggerFactory factory) {
        //����CategoryKey���󣺸����Ƕ�Logger������Name�ַ����ķ�װ�����������ԣ�һ����name,һ����hashcode��
        CategoryKey key = new CategoryKey(name);
        Logger logger;
        synchronized (ht) {
            // Hierarchy����һ��Hashtable<CategoryKey,Logger>���洢����Loggerʵ��,
            // ͨ��CategoryKey����ȡ��Ӧ����־����
            Object o = ht.get(key);
            if (o == null) {
                //������־���󣺾���new��һ��Logger���󣻸ö���Category�����ࣻ
                logger = factory.makeNewLoggerInstance(name);
                //���ô���־�����е� ��־�ֿ� ���ԣ�repository����ʵ������Category��������ԣ�
                logger.setHierarchy(this);
                //��CategoryKey �� logger����洢��Hashtable<CategoryKey,Logger>�У�
                ht.put(key, logger);
                //����Logger����(Category����)�� parent���ԣ�
                updateParents(logger);
                //����logger����
                return logger;
            } else if (o instanceof Logger) {
                return (Logger) o;
            } else if (o instanceof ProvisionNode) {
                logger = factory.makeNewLoggerInstance(name);
                logger.setHierarchy(this);
                ht.put(key, logger);
                updateChildren((ProvisionNode) o, logger);
                updateParents(logger);
                return logger;
            } else {
                return null;
            }
        }
    }

    /**
     * Returns all the currently defined categories in this hierarchy as
     * an {@link java.util.Enumeration Enumeration}.
     * <p/>
     * <p>The root logger is <em>not</em> included in the returned
     * {@link Enumeration}.
     */
    public Enumeration getCurrentLoggers() {
        // The accumlation in v is necessary because not all elements in
        // ht are Logger objects as there might be some ProvisionNodes
        // as well.
        Vector v = new Vector(ht.size());

        Enumeration elems = ht.elements();
        while (elems.hasMoreElements()) {
            Object o = elems.nextElement();
            if (o instanceof Logger) {
                v.addElement(o);
            }
        }
        return v.elements();
    }

    /**
     * @deprecated Please use {@link #getCurrentLoggers} instead.
     */
    public Enumeration getCurrentCategories() {
        return getCurrentLoggers();
    }


    /**
     * Get the renderer map for this hierarchy.
     */
    public RendererMap getRendererMap() {
        return rendererMap;
    }


    /**
     * Get the root of this hierarchy.
     *
     * @since 0.9.0
     */
    public Logger getRootLogger() {
        return root;
    }

    /**
     * This method will return <code>true</code> if this repository is
     * disabled for <code>level</code> object passed as parameter and
     * <code>false</code> otherwise. See also the {@link
     * #setThreshold(Level) threshold} emthod.
     */
    public boolean isDisabled(int level) {
        return thresholdInt > level;
    }

    /**
     * @deprecated Deprecated with no replacement.
     */
    public void overrideAsNeeded(String override) {
        LogLog.warn("The Hiearchy.overrideAsNeeded method has been deprecated.");
    }

    /**
     * Reset all values contained in this hierarchy instance to their
     * default.  This removes all appenders from all categories, sets
     * the level of all non-root categories to <code>null</code>,
     * sets their additivity flag to <code>true</code> and sets the level
     * of the root logger to {@link Level#DEBUG DEBUG}.  Moreover,
     * message disabling is set its default "off" value.
     * <p/>
     * <p>Existing categories are not removed. They are just reset.
     * <p/>
     * <p>This method should be used sparingly and with care as it will
     * block all logging until it is completed.</p>
     *
     * @since 0.8.5
     */
    public void resetConfiguration() {

        getRootLogger().setLevel((Level) Level.DEBUG);
        root.setResourceBundle(null);
        setThreshold(Level.ALL);

        // the synchronization is needed to prevent JDK 1.2.x hashtable
        // surprises
        synchronized (ht) {
            shutdown(); // nested locks are OK

            Enumeration cats = getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.setLevel(null);
                c.setAdditivity(true);
                c.setResourceBundle(null);
            }
        }
        rendererMap.clear();
        throwableRenderer = null;
    }

    /**
     * Does nothing.
     *
     * @deprecated Deprecated with no replacement.
     */
    public void setDisableOverride(String override) {
        LogLog.warn("The Hiearchy.setDisableOverride method has been deprecated.");
    }


    /**
     * Used by subclasses to add a renderer to the hierarchy passed as parameter.
     */
    public void setRenderer(Class renderedClass, ObjectRenderer renderer) {
        rendererMap.put(renderedClass, renderer);
    }

    /**
     * {@inheritDoc}
     */
    public void setThrowableRenderer(final ThrowableRenderer renderer) {
        throwableRenderer = renderer;
    }

    /**
     * {@inheritDoc}
     */
    public ThrowableRenderer getThrowableRenderer() {
        return throwableRenderer;
    }


    /**
     * Shutting down a hierarchy will <em>safely</em> close and remove
     * all appenders in all categories including the root logger.
     * <p/>
     * <p>Some appenders such as {@link org.apache.log4j.net.SocketAppender}
     * and {@link AsyncAppender} need to be closed before the
     * application exists. Otherwise, pending logging events might be
     * lost.
     * <p/>
     * <p>The <code>shutdown</code> method is careful to close nested
     * appenders before closing regular appenders. This is allows
     * configurations where a regular appender is attached to a logger
     * and again to a nested appender.
     *
     * @since 1.0
     */
    public void shutdown() {
        Logger root = getRootLogger();

        // begin by closing nested appenders
        root.closeNestedAppenders();

        synchronized (ht) {
            Enumeration cats = this.getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.closeNestedAppenders();
            }

            // then, remove all appenders
            root.removeAllAppenders();
            cats = this.getCurrentLoggers();
            while (cats.hasMoreElements()) {
                Logger c = (Logger) cats.nextElement();
                c.removeAllAppenders();
            }
        }
    }


    //����Logger����(Category����)�� parent���ԣ�
    final private void updateParents(Logger cat) {
        //��ȡ��־��������֣�Ҳ�������Ǵ��ݽ�����������
        String name = cat.name;
        //���ֳ��ȣ�
        int length = name.length();
        boolean parentFound = false;

        //�������ݽ����������ַ��������磺com.jiaboyan.logDemo.slf4jDemo
        for (int i = name.lastIndexOf('.', length - 1); i >= 0; i = name.lastIndexOf('.', i - 1)) {
            //���λ�ȡ������Ŀ¼��com.jiaboyan.logDemo.slf4jDemo��com.jiaboyan.logDemo��com.jiaboyan��com;
            String substr = name.substring(0, i);
            //����CategoryKey���󣬸ö����Ƕ�Logger������Name�ַ������Լ�������hashcode�ķ�װ��
            CategoryKey key = new CategoryKey(substr);
            // ͨ��CategoryKey���� ��hashtable�л�ȡ��Ӧ��Logger:��logger��ʼ����ɺ�hashtable����������û�գ�
            Object o = ht.get(key);
            if (o == null) {
                // ����ProvisionNode����
                ProvisionNode pn = new ProvisionNode(cat);
                //����hashTable�У�����־����������ÿһ��Ŀ¼�����δ�Ž�hashtable�У�
                //ÿһ��Ŀ¼����Ӧ�Ķ�����ͬ��Logger����
                ht.put(key, pn);
            } else if (o instanceof Category) {
                parentFound = true;
                cat.parent = (Category) o;
                break;
            } else if (o instanceof ProvisionNode) {
                ((ProvisionNode) o).addElement(cat);
            } else {
                Exception e = new IllegalStateException("unexpected object type " + o.getClass() + " in ht.");
                e.printStackTrace();
            }
        }
        //�������������parentFound���ɷ�false�Ļ������ǾͰѸ�Logger����Ϊ��Logger��parent:
        if (!parentFound)
            cat.parent = root;
    }

    /**
     * We update the links for all the children that placed themselves
     * in the provision node 'pn'. The second argument 'cat' is a
     * reference for the newly created Logger, parent of all the
     * children in 'pn'
     * <p/>
     * We loop on all the children 'c' in 'pn':
     * <p/>
     * If the child 'c' has been already linked to a child of
     * 'cat' then there is no need to update 'c'.
     * <p/>
     * Otherwise, we set cat's parent field to c's parent and set
     * c's parent field to cat.
     */
    final
    private void updateChildren(ProvisionNode pn, Logger logger) {
        //System.out.println("updateChildren called for " + logger.name);
        final int last = pn.size();

        for (int i = 0; i < last; i++) {
            Logger l = (Logger) pn.elementAt(i);
            //System.out.println("Updating child " +p.name);

            // Unless this child already points to a correct (lower) parent,
            // make cat.parent point to l.parent and l.parent to cat.
            if (!l.parent.name.startsWith(logger.name)) {
                logger.parent = l.parent;
                l.parent = logger;
            }
        }
    }

}


