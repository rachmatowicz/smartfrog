/* (C) Copyright 2005-2008 Hewlett-Packard Development Company, LP

 This library is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 2.1 of the License, or (at your option) any later version.

 This library is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public
 License along with this library; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

 For more information: www.smartfrog.org

 */
package org.smartfrog.services.os.java;

import org.smartfrog.sfcore.common.SmartFrogException;
import org.smartfrog.sfcore.logging.Log;
import org.smartfrog.sfcore.logging.LogFactory;
import org.smartfrog.sfcore.prim.Prim;
import org.smartfrog.sfcore.prim.PrimImpl;
import org.smartfrog.sfcore.prim.TerminationRecord;
import org.smartfrog.sfcore.reference.Reference;
import org.smartfrog.sfcore.utils.ComponentHelper;
import org.smartfrog.sfcore.utils.ListUtils;
import org.smartfrog.sfcore.workflow.conditional.Condition;
import org.smartfrog.sfcore.security.SFClassLoader;
import org.smartfrog.services.filesystem.FileSystem;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.List;
import java.io.InputStream;

/**
 * Class to force load another class (and keep it in memory till we undeploy. Liveness checks verify that the class
 * loaded properly. A flag can also force instantiate an instance if they have an empty constructor. Otherwise, just the
 * class gets loaded. created 20-Sep-2005 11:28:38
 */

public class LoadClassImpl extends PrimImpl implements LoadClass, Condition {
    public static final String ERROR_NO_PUBLIC_CONSTRUCTOR = "No public empty constructor for class ";
    public static final String MESSAGE_CREATING_AN_INSTANCE = "Creating an instance of ";
    private static final Reference REF_CLASSES = new Reference(ATTR_CLASSES);
    private static final Reference REF_RESOURCES = new Reference(ATTR_RESOURCES);
    private ComponentHelper helper;
    private String codebase;

    public LoadClassImpl() throws RemoteException {
    }

    /**
     * Classes we load
     */
    private Class[] classInstances = new Class[0];
    /**
     * any instance
     */
    private Object[] objectInstances = new Object[0];


    private List<String> classes, resources;

    private boolean create = false;

    private boolean retain = true;

    private String message;

    /**
     * a log
     */
    private Log log;


    /**
     * Can be called to start components. Subclasses should override to provide functionality Do not block in this call,
     * but spawn off any main loops!
     *
     * @throws SmartFrogException failure while starting
     * @throws RemoteException    In case of network/rmi error
     */
    @Override
    public synchronized void sfStart() throws SmartFrogException, RemoteException {
        super.sfStart();
        log = LogFactory.getOwnerLog(this);
        classes = ListUtils.resolveStringList(this, REF_CLASSES, true);
        resources = ListUtils.resolveStringList(this, REF_RESOURCES, true);
        create = sfResolve(ATTR_CREATE, create, true);
        retain = sfResolve(ATTR_RETAIN, retain, true);
        message = sfResolve(ATTR_MESSAGE,"",true);

        boolean isCondition = sfResolve(ATTR_IS_CONDITION, false, true);

        helper = new ComponentHelper(this);
        codebase = helper.getCodebase();

        if(!isCondition) {
            loadResourcesAndClasses();
            new ComponentHelper(this).sfSelfDetachAndOrTerminate(null,
                    null,
                    null,
                    null);
        }
    }

    /**
     * Attempt to load the resources and then the classes
     * @throws SmartFrogException if a resource or class is not found.
     */
    private void loadResourcesAndClasses() throws SmartFrogException {
        loadResources();
        loadClasses();
    }

    /**
     * Attempt to load the specific resources, fail if any is missing
     * @throws SmartFrogException if a resource is not found.
     */
    private void loadResources() throws  SmartFrogException {
        int failures = 0;
        StringBuilder text = new StringBuilder();
        for (String resource : resources) {
            log.debug("Loading resource" + resource);
            if (!doesResourceExist(resource, codebase)) {
                failures++;
                text.append("Resource not found: \"").append(resource).append("\"\n");
            }
            if (failures > 0) {
                throw new SmartFrogException(message + "\n" + text.toString(), this);
            }
        }
    }

    /**
     * Attempt to load the classes
     * @throws SmartFrogException if a class does not load
     */
    private void loadClasses() throws SmartFrogException {
        cleanup();
        int size = classes.size();
        classInstances = new Class[size];
        int instanceSize = size;
        if (!create) {
            instanceSize = 0;
        }
        objectInstances = new Object[instanceSize];

        String messageText = message != null && message.length() > 0 ? ('\n' + message) : "";
        int count = 0;
        int failures = 0;
        for (String classname : classes) {
            log.debug("Loading class " + classname);
            try {
                classInstances[count] = SFClassLoader.forName(classname, codebase, true);
            } catch (Throwable e) {
                //failure.
                failures++;
                handleLoadFailure(classname, messageText, codebase, e);
            }
            count++;
        }
        if (create) {
            for (int i = 0; i < classInstances.length; i++) {
                Class clazz = classInstances[i];
                log.debug("Creating class " + clazz.getName());
                objectInstances[i] = createInstance(clazz);
            }
        }
        if (!retain) {
            cleanup();
        }
    }


    /**
     * Handle a load failure by doing more diagnostics and throwing a more meaningful message
     *
     * @param classname class being looked for
     * @param message   any text message
     * @param codebase  the coebase string
     * @param thrown    what was thrown when the load failed
     * @throws SmartFrogException always
     */
    private String handleLoadFailure(String classname, String message, String codebase, Throwable thrown)
            throws SmartFrogException {

        if (thrown.getCause() != null) {
            return handleLoadFailure(classname, message, codebase, thrown.getCause());
        } else {
            StringBuilder text = new StringBuilder();
            text.append("Failed to load ");
            text.append(classname);
            text.append(" - ");
            text.append(message);
            text.append("\n");
            text.append(thrown.toString());
            text.append("\n");
            if (thrown instanceof ClassNotFoundException) {
                ClassNotFoundException cnfe = (ClassNotFoundException) thrown;
                text.append("ClassNotFoundException: the class or an import is missing");
            } else if (thrown instanceof NoClassDefFoundError) {
                NoClassDefFoundError ncdfe = (NoClassDefFoundError) thrown;
                text.append("NoClassDefFoundError: A class definition is missing");
            } else {
                text.append("Class failed to load due to: ").append(thrown.getClass().getSimpleName());
            }
            if (doesClassExistAsResource(classname, codebase)) {
                text.append("\n -the class was found on the classpath");
            } else {
                text.append("\n -the class is not on the classpath");
            }
            text.append("\ncodebase:");
            text.append(codebase);
            throw new SmartFrogException(text.toString(), thrown, this);
        }
//        return "";
    }

    /**
     * Verify that a class exists as a resource
     * @param classname class to look for
     * @param cbase codebase
     * @return true iff the .class file can be found
     */
    private boolean doesClassExistAsResource(String classname, String cbase) {
        String resourcename = "/"+classname.replace('.','/')+".class";
        return doesResourceExist(resourcename, cbase);
    }

    /**
     * Test for a resource existing
     * @param resourcename name of the resource
     * @param cbase code base
     * @return true iff the resource can be found
     */
    private boolean doesResourceExist(String resourcename, String cbase) {
        InputStream in = SFClassLoader.getResourceAsStream(resourcename, cbase, true);
        if (in == null) {
            return false;
        } else {
            FileSystem.close(in);
            return true;
        }
    }

    /**
     * Provides hook for subclasses to implement useful termination behavior. Deregisters component from local process
     * compound (if ever registered)
     *
     * @param status termination status
     */
    @Override
    public synchronized void sfTerminateWith(TerminationRecord status) {
        super.sfTerminateWith(status);
        cleanup();
    }

    /**
     * Evaluate the classload every time the request is made.
     *
     * @return true if it is successful, false if not
     * @throws RemoteException    for network problems
     * @throws SmartFrogException for any other problem
     */
    @Override
    public boolean evaluate() throws RemoteException, SmartFrogException {
        try {
            loadResourcesAndClasses();
            return true;
        } catch (SmartFrogException e) {
            return false;
        }
    }

    /**
     * forget about all our classes
     */
    private void cleanup() {
        //because Java lacks deterministic finalization, we don't know
        //when cleanup engages
        //all we can do is prepare for it.
        //delete all object instances from the array
        if (objectInstances != null) {
            for (int i = 0; i < objectInstances.length; i++) {
                objectInstances[i] = null;
            }
            objectInstances = null;
        }
        if (classInstances != null) {
            //delete all class instances
            for (int i = 0; i < classInstances.length; i++) {
                classInstances[i] = null;
            }
            classInstances = null;
        }

        //our component has purged all knowlege of the components, it is up to GC to do the rest.
        //we trigger this, even though it is only a hint
        System.gc();
    }


    /**
     * Create an instance of a class using the empty constructor
     *
     * @param clazz class to load
     * @return an instance
     * @throws SmartFrogException if something went wrong
     * @throws RemoteException    for network trouble
     */
    public static Object createInstance(Class clazz) throws SmartFrogException {
        return createInstance(clazz,null);
    }
    /**
     * Create an instance of a class using the empty constructor
     *
     * @param clazz class to load
     * @param message extra diagnostics message
     * @return an instance
     * @throws SmartFrogException if something went wrong
     * @throws RemoteException    for network trouble
     */
    public static Object createInstance(Class clazz, String message) throws SmartFrogException {
        Object instance;
        String suffix = message != null && message.length() > 0 ? '\n' + message : "";
        Class params[] = new Class[0];
        Constructor defaultConstructor;
        try {
            defaultConstructor = clazz.getConstructor(params);
        } catch (NoSuchMethodException e) {
            throw new SmartFrogException(ERROR_NO_PUBLIC_CONSTRUCTOR + clazz.getName() + suffix);
        }
        Object params2[] = new Object[0];
        String details = MESSAGE_CREATING_AN_INSTANCE + clazz.getName() + suffix;
        try {
            instance = defaultConstructor.newInstance(params2);
        } catch (InstantiationException e) {
            throw SmartFrogException.forward(details, e);
        } catch (IllegalAccessException e) {
            throw SmartFrogException.forward(details, e);
        } catch (InvocationTargetException e) {
            throw SmartFrogException.forward(details, e);
        }
        return instance;
    }

    /**
     * Load a class using the classloader of a nominated component
     *
     * @param owner     owner component
     * @param classname name of the class to load
     * @return the class
     * @throws RemoteException    for network trouble
     * @throws SmartFrogException if the loading failed; in which case a nested ClassNotFoundException will exist.
     */
    public static Class loadClass(Prim owner, String classname) throws RemoteException, SmartFrogException {
        ComponentHelper helper = new ComponentHelper(owner);
        return helper.loadClass(classname);
    }
}
