package org.smartfrog.services.utils.setproperty;

import org.smartfrog.sfcore.common.SmartFrogException;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**

 */
public interface SystemProperties extends Remote {

    String ATTR_PROPERTIES = "properties";
    String ATTR_SETONSTARTUP = "setOnStartup";
    String ATTR_SETONDEPLOY = "setOnDeploy";
    String ATTR_SETONEARLYDEPLOY = "setOnEarlyDeploy";
    String ATTR_UNSETONTERMINATE = "unsetOnTerminate";
    String ATTR_IGNORESECURITYEXCEPTIONS = "ignoreSecurityExceptions";

    /**
     * Set a property in this JVM
     *
     * @param name  name of the property
     * @param value value of the property
     * @throws SmartFrogException -may wrap a security exception
     * @throws RemoteException network problems
     */
    void setProperty(String name, String value) throws SmartFrogException,
            RemoteException;

    /**
     * Unset a property in this JVM
     *
     * @param name name of the property
     * @throws SmartFrogException -may wrap a security exception
     * @throws RemoteException network problems
     */
    void unsetProperty(String name) throws SmartFrogException,
            RemoteException;
}
