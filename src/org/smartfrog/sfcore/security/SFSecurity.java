/** (C) Copyright 1998-2004 Hewlett-Packard Development Company, LP

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

package org.smartfrog.sfcore.security;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.net.InetAddress;
import java.security.AccessControlException;
import java.util.Properties;
import java.util.Map;

import org.smartfrog.sfcore.processcompound.SFServerSocketFactory;


/**
 * Provides basic security functionality to SF, i.e., triggers the
 * initialization of all the security mechanisms (this should be called right
 * at the beginning of SF startup) or returns information about the security
 * environment. We assume JDK 1.4 so crypto and JSSE is already fully
 * integrated.
 *
 */
public class SFSecurity {
    /** A flag that ensures only one initialization. */
    private static boolean alreadyInit = false;

    /** A flag that states whether SF security checks are active. */
    private static volatile boolean securityOn = false;

    /** A flag that states whether SF security checks for resources are active. */
    private static volatile boolean secureResourcesOff = false;

    /** A security environment shared by all the local SF components */
    private static SFSecurityEnvironment securityEnv;

    /** A RMIServerSocketFactory used when security is off */
    private static SFServerSocketFactory nonSecServerSocketFactory;

    /**
     * Get a string containing interesting security information.
     * @return a diagnostics string
     */
    public static String getSecurityInformation() {
        SecurityManager manager = System.getSecurityManager();
        if (manager == null) {
            return "";
        } else {
            StringBuilder details=new StringBuilder();
            details.append(manager.toString())
                    .append("; class: ").append(manager.getClass())
                    .append("; exit trapping: ").append(manager instanceof ExitTrapping);
            details.append("\nJava System Properties:\n");
            try {
                Properties properties = System.getProperties();
                for(Object key:properties.keySet()) {
                    details.append(key.toString());
                    details.append("=\"");
                    details.append(properties.get(key).toString());
                    details.append("\"\n");
                }
            } catch (SecurityException  e) {
                details.append("access to properties denied: ").append(e);
            }
            details.append("\nEnvironment variables:\n");
            try {
                Map<String,String> env = System.getenv();
                for(String key:env.keySet()) {
                    details.append(key);
                    details.append("=\"");
                    details.append(env.get(key));
                    details.append("\"\n");
                }
            } catch (SecurityException  e) {
                details.append("access to environment variables denied: ").append(e);
            }
            return details.toString();
        }
    }

    /**
     * Initializes the security using system properties to decide on the level
     * of security required.
     *
     * @throws SFGeneralSecurityException if there is any error initializing security.
     */
    synchronized public static void initSecurity()
        throws SFGeneralSecurityException {
        try {
            if (!alreadyInit) {
                // Add the new RMIClassLoaderSpi
                try {
                    System.setProperty("java.rmi.server.RMIClassLoaderSpi",
                            "org.smartfrog.sfcore.security." + "SFRMIClassLoaderSpi");
                } catch (AccessControlException e) {
                    throw new SFGeneralSecurityException(
                            "Java Security Access control exception - "
                                    + "SmartFrog is running under a security manager, but the main JAR is not "
                                    + "signed by a trusted CA, or the permissions files are mis-configured: "
                                    + "\n" + e
                                    + "\n Security manager "+ getSecurityInformation(),
                            e);

                }

                SFSecurityProperties.readSecurityProperties();

                if (Boolean.getBoolean(SFSecurityProperties.propSecurityOn)) {
                    // Activate the real security manager.
                    ExitTrappingSecurityManager.registerSecurityManager(true);

                    securityEnv = new SFSecurityEnvironmentImpl(null);

                    /*Make sure that we restrict downloading of stubs/RMIClientFactory
                       Note that this only works if this is called before RMI
                       classes are loaded... */
                    /* Not needed after using SFRMIClassLoaderSpi
                       System.setProperty(SFSecurityProperties.propUseCodebaseOnly,"true");
                     */
                    /* Set the java.rmi.server.codebase to the value of
                       org.smartfrog.codebase unless it was explicitly set. This
                       allow us to respect propUseCodebaseOnly by just setting the
                       SF codebase. */
                    /* No longer can rely on  java.rmi.server.codebase because
                       it is *static*
                       if (SFClassLoader.getTargetClassBase() != null) {
                         String currentCodebase =
                           System.getProperty("java.rmi.server.codebase");
                         if (currentCodebase == null) {
                           System.setProperty("java.rmi.server.codebase",
                                              SFClassLoader.getTargetClassBase());
                         } else if (!(currentCodebase.equals(SFClassLoader.
                                                             getTargetClassBase()))) {
                           System.out.println("WARNING: java.rmi.server.codebase "+
                                              "already set to " + currentCodebase +
                                               " that is different to  " +
                                              "org.smartfrog.codebase set to " +
                                              SFClassLoader.getTargetClassBase());
                         }
                       }
                       //Make objectIDs difficult to guess.
                       System.setProperty(SFSecurityProperties.propRandomIDs,"true");
                       /* Activate a safe default RMISocketFactory. This could be
                          modified by the one in the object reference, provided that
                          its class can be found in our codebase. For this reason,
                          we have to be *very* careful on what RMIClientSocketFactory
                          classes are available in our codebase...*/
                    RMISocketFactory.setSocketFactory(securityEnv.getRMISocketFactory());
                    secureResourcesOff = Boolean.getBoolean(SFSecurityProperties.propSecureResourcesOff);
                    securityOn = true;
                } else {
                    //System.setSecurityManager(new DummySecurityManager());
                    // if a java.security.policy is set then we initialize standard java security
                    // This is necessary for dynamic classloading to work.
                    String secPro = System.getProperty("java.security.policy");
                    if  (secPro != null) {
                        ExitTrappingSecurityManager.registerSecurityManager(false);
                    }

                    securityOn = false;
                    //Notification moved to SFSystem after the ini file is read.
                }
            }
        } catch (IOException e) {
            // Problems setting up RMI.
            throw new SFGeneralSecurityException(e.toString()
                    + "\nSecurity manager " + getSecurityInformation(),
                    e);
        }
    }

    /**
     * Gets the security environment shared by all the local SF components.
     *
     * @return The security environment shared by all the local SF components.
     */
    synchronized static SFSecurityEnvironment getSecurityEnvironment() {
        checkSFCommunity();
        return securityEnv;
    }

    /**
     * Checks that the calling stack has the SFCommunityPermission, i.e., all
     * the code involved is signed by a trusted key.
     */
    public static void checkSFCommunity() {
        SecurityManager securitymanager = System.getSecurityManager();

        if (securitymanager != null) {
            securitymanager.checkPermission(new SFCommunityPermission());
        }
    }

    /**
     * Returns whether the SF security checks are active or not. This can only
     * be changed once at initialization time for security reasons.
     *
     * @return whether the SF security is active.
     */
    public static boolean isSecurityOn() {
        return securityOn;
    }

    /**
     * Returns whether the SF security checks for resources are active or not. This can only
     * be changed once at initialization time for security reasons and does not apply to classes.
     *
     * @return whether the SF security is active.
     */
    public static boolean isSecureResourcesOff (){
        return secureResourcesOff;
    }

    /**
     * Creates and exports a <code>Registry</code> on the local host that
     * accepts requests on the specified <code>port</code>.
     *
     * @param port the port on which the registry accepts requests
     * @param bindAddr The address to bind on
     * @return the registry
     *
     * @throws RemoteException if the registry could not be exported
     *
     * @since JDK1.1
     */
    public static Registry createRegistry(int port, InetAddress bindAddr) throws RemoteException {
        if (isSecurityOn()) {
            return LocateRegistry.createRegistry(port,
                securityEnv.getEmptyRMIClientSocketFactory(),
                securityEnv.getRMIServerSocketFactory());
        } else {
            nonSecServerSocketFactory = new SFServerSocketFactory(bindAddr);
            return LocateRegistry.createRegistry(port, null, nonSecServerSocketFactory);
        }
    }

    /**
     * Returns a reference to the remote object <code>Registry</code> on the
     * specified <code>host</code> and <code>port</code>. If <code>host</code>
     * is <code>null</code>, the local host is used.
     *
     * @param host host for the remote registry
     * @param port port on which the registry accepts requests
     *
     * @return reference (a stub) to the remote object registry
     *
     * @throws RemoteException if the reference could not be created
     *
     * @since JDK1.1
     */
    public static Registry getRegistry(String host, int port)
        throws RemoteException {
        if (isSecurityOn()) {
            return LocateRegistry.getRegistry(host, port,
                securityEnv.getRMIClientSocketFactory());
        } else {
            return LocateRegistry.getRegistry(host, port);
        }
    }

    /**
     * Used inside a method call invoked by the RMI Server to find out
     * authenticated information of our peer that called this function
     * remotely.
     *
     * @return Authenticated information about our peer.
     */
    public static String getPeerAuthenticatedSubjects() {
        return SFInputStream.getPeerAuthenticatedSubjects();
    }
}
