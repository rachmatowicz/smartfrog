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

package org.smartfrog.sfcore.processcompound;

import org.smartfrog.SFSystem;
import org.smartfrog.services.filesystem.files.FilesImpl;
import org.smartfrog.services.filesystem.files.Fileset;
import org.smartfrog.sfcore.common.Context;
import org.smartfrog.sfcore.common.ContextImpl;
import org.smartfrog.sfcore.common.Diagnostics;
import org.smartfrog.sfcore.common.ExitCodes;
import org.smartfrog.sfcore.common.Logger;
import org.smartfrog.sfcore.common.MessageKeys;
import org.smartfrog.sfcore.common.MessageUtil;
import org.smartfrog.sfcore.common.SmartFrogCoreKeys;
import org.smartfrog.sfcore.common.SmartFrogCoreProperty;
import org.smartfrog.sfcore.common.SmartFrogDeploymentException;
import org.smartfrog.sfcore.common.SmartFrogException;
import org.smartfrog.sfcore.common.SmartFrogLivenessException;
import org.smartfrog.sfcore.common.SmartFrogResolutionException;
import org.smartfrog.sfcore.common.SmartFrogRuntimeException;
import org.smartfrog.sfcore.common.TerminatorThread;
import org.smartfrog.sfcore.componentdescription.ComponentDescription;
import org.smartfrog.sfcore.componentdescription.ComponentDescriptionImpl;
import org.smartfrog.sfcore.compound.CompoundImpl;
import org.smartfrog.sfcore.prim.Prim;
import org.smartfrog.sfcore.prim.TerminationRecord;
import org.smartfrog.sfcore.reference.HereReferencePart;
import org.smartfrog.sfcore.reference.Reference;
import org.smartfrog.sfcore.reference.ReferencePart;
import org.smartfrog.sfcore.security.SFSecurityProperties;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;


/**
 * Implements deployment behaviour for a process. There is a single process
 * compound allowed per process. SFSystem asks SFProcess to make the
 * processcompound on startup. SFProcess also holds the logic for making a
 * process compound a root for a host. A single root process compound (defined
 * as owning a particular port) is allowed per host. Every processcompound tries
 * to locate its parent on deployment, if there is none, it tries to become the
 * root of the host.
 * <p/>
 * <p> Through the deployer class used for primitives "PrimProcessDeployerImpl"
 * the registration of components on deployment is guaranteed. A component being
 * registered only means that the component is known to the process compound and
 * will receive liveness from it. When the process compound is asked to
 * terminate (ie. asked to terminate the process) all components are terminated.
 * </p>
 * <p/>
 * <p> You do not need to instantiate this class in order to get new processes.
 * In your component description that you want to deploy, simply define
 * sfProcessName with a string name of the processname that you want to deploy
 * your component in. If the process does not exist, and processes are allowed
 * by the root compound on the host, the process will be created and your
 * component deployed. </p>
 */
public class ProcessCompoundImpl extends CompoundImpl
        implements ProcessCompound, MessageKeys {

    /**
     * A number used to generate a unique ID for registration
     */
    public static long registrationNumber = 0;

    /**
     * Name of this processcompound. If one is present it is a subprocess.
     */
    protected String sfProcessName = null;

    /**
     * Whether the process is a Root (whether is starts and registers with a
     * registry). Default is not. set using property org.smartfrog.sfcore.processcompound.sfProcessName="rootProcess"
     */
    protected boolean sfIsRoot = false;

    /**
     * Whether the ProcessCompound should cause the JVM to exit on termination.
     * By default set to true.
     */
    protected boolean systemExit = true;

    /**
     * On liveness check on a process compound checks if it has any components
     * registered. If not, the process compound \"garbage collects\" itself
     * (causing exit!). Since root process compound does not receive liveness it
     * will never do this. The GC is controlled by an attribute in
     * processcompound.sf "sfSubprocessGCTimeout" which indicates the number of
     * pings for which it should be consecutively with no components for the
     * process to be GCed. If this is set to 0, the GC is disabled.
     */
    protected int gcTimeout = -1;
    /**
     * The countdown to check the gcTimeout.
     */
    protected int countdown = 0;


    /**
     * A set that contains the names of the sub-processes that have been
     * requested, but not yet ready
     */
    protected Set<Object> processLocks = new HashSet<Object>();
    public static final String ATTR_PORT = "sfPort";
    private static final String JAVA_SECURITY_POLICY = "java.security.policy";


    public ProcessCompoundImpl() throws RemoteException {
    }

    /**
     * Test whether the Process Compound is the root process compound or not.
     *
     * @return true if it is the root
     *
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public boolean sfIsRoot() throws RemoteException {
        return sfIsRoot;
    }

    /**
     * Parent process compoound can not add me to the attribute list since he
     * did not create me. Uses sfRegister with specific name to register with
     * parent compound.
     *
     * @param parent parent process compound to register with
     *
     * @throws SmartFrogException failed to register with parent
     */
    protected void sfRegisterWithParent(ProcessCompound parent)
            throws SmartFrogException {
        if (parent == null) {
            return;
        }

        try {
            parent.sfRegister(sfProcessName, this);
        } catch (RemoteException rex) {
            throw new SmartFrogRuntimeException(MSG_FAILED_TO_CONTACT_PARENT,
                    rex, this);
        } catch (SmartFrogException resex) {
            resex.put(SmartFrogCoreKeys.SF_PROCESS_NAME, sfProcessName);
            throw resex;
        }
    }

    /**
     * Locate the parent process compound. If sfParent is already set, it is
     * returned, otherwise the parent is looked up using local host process
     * compound, sitting on port given by sfRootLocatorPort attribute.
     *
     * @return parent process compound or null if root
     *
     * @throws RemoteException In case of network/rmi error
     * @throws SmartFrogException In case of SmartFrog system error
     */
    protected ProcessCompound sfLocateParent()
            throws SmartFrogException, RemoteException {
        ProcessCompound root;

        if (sfParent != null) {
            return (ProcessCompound) sfParent;
        }

        if (sfProcessName == null) {
            return null;
        }

        try {
            root = SFProcess.getRootLocator().getRootProcessCompound(null, ((Number) sfResolveHere(SmartFrogCoreKeys.SF_ROOT_LOCATOR_PORT, false)).intValue());
        } catch (Throwable t) {
            throw (SmartFrogRuntimeException) SmartFrogRuntimeException.forward( "ProcessCompoundImpl.sfLocateParent()", t);
        }
        return root;
    }

    /**
     * Override standard compound behaviour to register all components that go
     * through here as a child compound. Sub-components of given description will
     * not go through here, and so will not be registered here. A component is
     * registered through sfRegister. The component can define its name in the
     * process compound through the sfProcessComponentName attribute.
     *
     * @param name   name to name deployed component under in context
     * @param parent of deployer component
     * @param cmp    compiled component to deploy
     * @param parms  parameters for description
     *
     * @return newly deployed component
     *
     * @throws SmartFrogDeploymentException failed to deploy compiled component
     */
    @Override
    public Prim sfDeployComponentDescription(Object name,
                                             Prim parent,
                                             ComponentDescription cmp,
                                             Context parms)
            throws SmartFrogDeploymentException {
        try {
            Prim result;

            if (parent == null) {
                result = super.sfDeployComponentDescription(name,
                        this,
                        cmp,
                        parms);
                // TODO: take care when user calls it
                result.sfDetach();
            } else {
                result = super.sfDeployComponentDescription(name,
                        parent,
                        cmp,
                        parms);
            }

            //          sfRegister(result.sfResolveId(SmartFrogCoreKeys.SF_PROCESS_COMPONENT_NAME), result);

            return result;
        } catch (Exception sfex) {
            throw (SmartFrogDeploymentException) SmartFrogDeploymentException.forward(
                    sfex);
        }
    }

    /**
     * Creates itself as the right form of process: root, sub or independant. If
     * sfProcessName is an empty string: independant if               is
     * rootProcess: become the root process anything else    is subprocess and
     * register with parent.
     *
     * @param parent parent prim. always null (and ignored) for this component
     * @param cxt    context for deployement
     *
     * @throws RemoteException In case of network/rmi error
     * @throws SmartFrogDeploymentException In case of any error while deploying
     * the component
     */
    @Override
    public synchronized void sfDeployWith(Prim parent, Context cxt)
            throws SmartFrogDeploymentException, RemoteException {
        try {
            // Set context for sfResolves to work when registering as root
            sfContext = cxt;

            // find name for this process. If found, get parent
            sfProcessName = (String) sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_NAME,
                    false);

            if (sfProcessName != null) {
                if (sfProcessName.equals(SmartFrogCoreKeys.SF_ROOT_PROCESS)) {
                    sfIsRoot = true;
                } else {
                    try {
                        sfParent = sfLocateParent();
                    } catch (Throwable t) {
                        throw new SmartFrogDeploymentException(
                                MSG_PARENT_LOCATION_FAILED,
                                t,
                                this,
                                null);

                    }
                }
            }
            //Clean any cached sfcompletename
            sfParentageChanged();
            // Now go on with normal deployment
            try {
                super.sfDeployWith(sfParent, sfContext);
            } catch (SmartFrogDeploymentException sfex) {
                if (sfProcessName != null) {
                    sfex.put(SmartFrogCoreKeys.SF_PROCESS_NAME, sfProcessName);
                }

                sfex.put("sfDeployWith", "failed");
                throw sfex;
            }

            // super.sfDeployWith should take care of throwables...
            // Set to root if no parent
            if ((sfParent == null) && sfIsRoot) {
                SFProcess.getRootLocator().setRootProcessCompound(this);
            }

            // Register with parent (does nothing if parent in null)
            sfRegisterWithParent((ProcessCompound) sfParent);

        } catch (SmartFrogException sfex) {
            throw ((SmartFrogDeploymentException) SmartFrogDeploymentException.forward(
                    sfex));
        }
    }

    /**
     * Exports this component using portObj. portObj can be a port or a vector
     * containing a set of valid ports. If a vector is used the component tries
     * to see if the port used by the local ProcessCompound is in the vector set
     * and use that if so. If not tries to use the first one available.
     *
     * @param portObj Object
     *
     * @return Object Reference to exported object
     *
     * @throws RemoteException In case of network/rmi error
     * @throws SmartFrogDeploymentException In case of any error while exporting
     * the component
     */
    @Override
    protected Object sfExport(Object portObj)
            throws RemoteException, SmartFrogException {
        Object exportRef = null;
        int port = 0; //default value
        if ((portObj != null)) {
            if (portObj instanceof Integer) {
                port = ((Integer) portObj).intValue();
                exportRef = sfExportRef(port);
                sfAddAttribute(ATTR_PORT, new Integer(port));
            } else if (portObj instanceof Vector) {
                //if not in range use vector and try
                int size = ((Vector) (portObj)).size();
                for (int i = 0; i < size; i++) {
                    //get
                    try {
                        port = ((Integer) ((Vector) (portObj)).elementAt(i)).intValue();
                        exportRef = sfExportRef(port);
                        sfAddAttribute(ATTR_PORT, new Integer(port));
                        break;
                    } catch (SmartFrogException ex) {
                        if (i >= size - 1) {
                            throw ex;
                        }
                    }
                } //for
            }
        } else {
            exportRef = sfExportRef(port);
        }
        return exportRef;
    }


    /**
     * {@inheritDoc}
     *
     * @throws RemoteException In case of network/rmi error
     * @throws SmartFrogDeploymentException In case of any error while
     * registering the component
     */
    @Override
    protected void registerWithProcessCompound()
            throws RemoteException, SmartFrogException {
        //This is a ProcessCompound. Don't need to register
    }

    /**
     * Starts the process compound. In addition to the normal Compound sfStart,
     * it notifes the root process compound (if it is a sub-process) that it is
     * now ready for action by calling sfNotifySubprocessReady.
     *
     * @throws SmartFrogException failed to start compound
     * @throws RemoteException In case of Remote/nework error
     */
    @Override
    public synchronized void sfStart() throws SmartFrogException, RemoteException {
        super.sfStart();
        //Set itself as single instance of process compound for this process
        SFProcess.setProcessCompound(this);

        // This call and method will disapear once we refactor ProcessCompound
        // SFProcess.addDefaultProcessDescriptions will replace all this code.
        // @TODO fix after refactoring ProcessCompound.
        deployDefaultProcessDescriptions();

        // Add diagnostics report
        if (Logger.processCompoundDiagReport) {
            sfAddAttribute(SmartFrogCoreKeys.SF_DIAGNOSTICS_REPORT, sfDiagnosticsReport());
        }
        if (sfLog().isDebugEnabled() && Logger.logStackTrace) {
            StringBuffer report = new StringBuffer();
            Diagnostics.doShortReport(report, (Prim) null);
            sfLog().debug(report);
        } else if (sfLog().isTraceEnabled()) {
            sfLog().trace(sfDiagnosticsReport());
        }
        // Add boot time only in rootProcess
        if (sfIsRoot) {
            sfAddAttribute(SmartFrogCoreKeys.SF_BOOT_DATE, new Date(System.currentTimeMillis()));
        }
        // the last act is to inform the root process compound that the
        // subprocess is now ready for action - only done if not the root
        try {
            if (!sfIsRoot()) {
                ProcessCompound parent = sfLocateParent();
                if (parent != null) {
                    parent.sfNotifySubprocessReady(sfProcessName);
                }
            }
        } catch (RemoteException rex) {
            throw new SmartFrogRuntimeException(MSG_FAILED_TO_CONTACT_PARENT, rex, this);
        }
        if (sfLog().isDebugEnabled()) {
            sfLog().debug("ProcessCompound '" + sfProcessName + "' started.");
        }
    }

    /**
     * @throws RemoteException In case of Remote/nework error
     * @throws SmartFrogException if fail deployment
     */
    private void deployDefaultProcessDescriptions() throws SmartFrogException, RemoteException {
        //Get a clone to protect possible concurrency access to it
        Properties props = (Properties)System.getProperties().clone();
        Context nameContext = null;
        String name ;
        String url;
        String key = null;
        try {
            for (Object o : props.keySet()) {
                key = o.toString();
                if (key.startsWith(SmartFrogCoreProperty.defaultDescPropBase)) {
                    // Collects all properties refering to default descriptions that
                    // have to be deployed inmediately after process compound
                    // is started.
                    url = (String) props.get(key);
                    name = key.substring(SmartFrogCoreProperty.defaultDescPropBase.length());
                    ComponentDescription cd = ComponentDescriptionImpl.sfComponentDescription(url.trim());
                    sfCreateNewApp(name, cd, nameContext);
                }
            }
        } catch (SmartFrogDeploymentException ex) {
            throw ex;
        } catch (SmartFrogException sfex) {
            throw new SmartFrogDeploymentException("deploying default description for '" + key + '\'', sfex, this, nameContext);
        }
    }


    /**
     * Process compound sub-component termination policy is currently not to
     * terminate itself (which is default compound behaviour. Component is
     * removed from liveness and attribute table (if present).
     *
     * @param rec  termination record
     * @param comp component that terminated
     */
    @Override
    public void sfTerminatedWith(TerminationRecord rec, Prim comp) {
        try {
            sfRemoveAttribute(sfAttributeKeyFor(comp));
        }
        catch (RemoteException ex) {
            if (sfLog().isIgnoreEnabled()) {
                sfLog().ignore(ex);
            }
        }
        catch (SmartFrogRuntimeException ex) {
            if (sfLog().isIgnoreEnabled()) {
                sfLog().ignore(ex);
            }
        }
    }

    /**
     * Override liveness sending failures to just remove component from table,
     * Does NOT to call termination since a child terminating does not mean that
     * this proces should die. If, however, the process is a sub-process, and
     * the failure is from the parent root process, then the process will carry
     * out normal component failure behaviour.
     *
     * @param source  sender that failed
     * @param target  target for the failure
     * @param failure The error
     */
    @Override
    public void sfLivenessFailure(Object source, Object target,
                                  Throwable failure) {
        if ((source == this) && (sfParent != null) && (target == sfParent)) {
            super.sfLivenessFailure(source, target, failure);
        }

        try {
            sfRemoveAttribute(sfAttributeKeyFor(target));
        } catch (Exception ex) {
            if (sfLog().isIgnoreEnabled()) {
                sfLog().ignore(ex);
            }
        }
    }

    /**
     * Termination call. Could be due to parent failing or management interface.
     * In any case it means terminating all registered components and exiting
     * this process.
     *
     * @param rec termination record
     */
    @Override
    public synchronized void sfTerminateWith(TerminationRecord rec) {
        super.sfTerminateWith(rec);
        if (sfIsRoot) {
            try {
                SFProcess.getRootLocator().unbindRootProcessCompound();
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
            }
        }
        if (systemExit) {
            try {
                String name = SmartFrogCoreKeys.SF_PROCESS_NAME;
                name = sfResolve(SmartFrogCoreKeys.SF_PROCESS_NAME,
                        name,
                        false);
                sfLog().out(MessageUtil.formatMessage(MSG_SF_DEAD,
                        name) + ' ' + new Date(System.currentTimeMillis()));
            } catch (Throwable thr) {
                sfLog().ignore("When exiting",thr);
            }
            ExitCodes.exitWithError(ExitCodes.EXIT_CODE_SUCCESS);
        }
    }

    /**
     * Iterates over children telling each of them to terminate quietly with
     * given status. It iterates from the last one created to the first one.
     *
     * @param status status to terminate with
     */
    @Override
    protected void sfSyncTerminateWith(TerminationRecord status) {
        // Terminate legitimate children except subProc
        for (Prim child : sfReverseChildren()) {
            try {
                if ((!(child instanceof ProcessCompound))
                        && (child.sfParent() == null)) {
                    //Logger.log("SynchTerminate sent to legitimate: "+ child.sfCompleteName());
                    (child).sfTerminateQuietlyWith(status);
                }
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
                // ignore
            }
        }
        // Terminate illegitimate children except subProc
        for (Prim child : sfReverseChildren()) {
            try {
                if ((!(child instanceof ProcessCompound))) {
                    //Logger.log("SynchTerminate sent to illegitimate: "+ child.sfCompleteName());
                    //Full termination notifying its parent
                    (child).sfTerminate(status);
                }
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
                // ignore
            }
        }
        // Terminate subprocesses
        for (Prim child : sfReverseChildren()) {
            try {
                //Logger.log("SynchTerminate sent to : "+ child.sfCompleteName());
                (child).sfTerminateQuietlyWith(status);
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
                // ignore
            }
        }
    }

    /**
     * Terminate children asynchronously using a separate thread for each call.
     * It iterates from the last one created to the first one.
     *
     * @param status status to terminate with
     */
    @Override
    protected void sfASyncTerminateWith(TerminationRecord status) {
        // Terminate legitimate children except subProc

        for (Prim child : sfReverseChildren()) {
            try {
                if ((!(child instanceof ProcessCompound)) && (child.sfParent() == null)) {
                    //Logger.log("ASynchTerminate sent to legitimate: "+ child.sfCompleteName());
                    (new TerminatorThread(child, status).quietly()).start();
                }
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
                // ignore
            }
        }
        // Terminate illegitimate children except subProc
        for (Prim child : sfReverseChildren()) {
            try {
                if ((!(child instanceof ProcessCompound))) {
                    //Full termination notifying its parent
                    //Logger.log("ASynchTerminate sent to (illegitimate): "+ child.sfCompleteName());
                    (new TerminatorThread(child, status)).start();
                }
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
                // ignore
            }
        }
        // Terminate subprocesses
        for (Prim child : sfReverseChildren()) {
            try {
                //Logger.log("ASynchTerminate sent to: "+ child.sfCompleteName());
                (new TerminatorThread(child, status).quietly()).start();
            } catch (Exception ex) {
                if (sfLog().isIgnoreEnabled()) {
                    sfLog().ignore(ex);
                }
                // ignore
            }
        }

    }


    /**
     * Sets whether or not the ProcessCompound should terminate the JVM on exit.
     * This is, by default, set to true. It is used if the ProcessCompound is
     * created and managed by other code.
     *
     * @param exit whether or not to exit (true = exit)
     *
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public void systemExitOnTermination(boolean exit) throws RemoteException {
        systemExit = exit;
    }

    /**
     * Detach the process compound from its parent. The process compound will
     * try to become root process compound for this host. This might fail if the
     * root locator can not make this process compound root.
     *
     * @throws SmartFrogException failed detaching process compound
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public void sfDetach() throws SmartFrogException, RemoteException {
        try {
            super.sfDetach();
            SFProcess.getRootLocator().setRootProcessCompound(this);
        } catch (SmartFrogException sfex) {
            // Add the context
            sfex.put("sfDetachFailure", sfContext);
            throw sfex;
        }
    }


    /**
     * {@inheritDoc}
     * @param source caller
     * @throws SmartFrogLivenessException liveness failure
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public void sfPing(Object source) throws SmartFrogLivenessException, RemoteException {
        super.sfPing(source);

        if (source == null) {
            return;
        }

        if (!source.equals(sfLivenessSender)) {
            return;
        }
        // only check for subprocess GC if checking self
        if (gcTimeout == -1) {
            try {
                gcTimeout = ((Integer) sfResolveHere(SmartFrogCoreKeys.SF_SUBPROCESS_GC_TIMEOUT)).intValue();
            } catch (SmartFrogResolutionException ignored) {
                gcTimeout = 0;
            }

            if (sfLog().isTraceEnabled()) sfLog().debug ("SubProcessGC being initialised - " + gcTimeout);
            countdown = gcTimeout;
        }

        if (gcTimeout > 0) {
            if (sfLog().isTraceEnabled()) sfLog().trace ("SPGC lease being checked for " + sfCompleteNameSafe() + " - " + countdown);
            if ((countdown-- >= 0) && (sfChildList().size() == 0) && (sfParent != null)) {
                //Finished countdown
                if (countdown <= 0) {
                    if (sfLog().isDebugEnabled()) sfLog().debug ("SubProcessGC being activated");
                    sfTerminate(TerminationRecord.normal ("SubProcessGC self activated for "+ sfCompleteNameSafe(), sfCompleteNameSafe() , null));
                }
            } else {
                if (sfLog().isTraceEnabled()) sfLog().trace ("SubProcessGC lease being reset " + sfCompleteNameSafe() + " source "+ source );
                countdown = gcTimeout;
            }
        } else {
            // only send warn when debug enabled.
            if (sfLog().isDebugEnabled()) sfLog().warn("SubProcessGC not enabled");
        }
    }

    //
    // ProcessCompound
    //

    /**
     * Returns the processname for this process. Reference is be empty if this
     * compound is the root for the host.
     *
     * @return process name for this process
     */
    @Override
    public String sfProcessName() {
        return sfProcessName;
    }

    /**
     * Returns the complete name for this component from the root of the
     * application. sfCompleteName is cached.
     *
     * @return reference of attribute names to this component
     *
     * @throws RemoteException In case of network/rmi error
     * TODO: clean cache when re-parenting
     */

    @Override
    public Reference sfCompleteName() throws RemoteException {
        if (sfCompleteName == null) {
            Reference r;
            r = new Reference();

            String canonicalHostName = SmartFrogCoreKeys.SF_HOST;

            try {
                // read sfHost attribute. Faster that using sfDeployedHost().
                InetAddress address = ((InetAddress) sfResolveHere(
                        canonicalHostName,
                        false));
                canonicalHostName = address != null ? address.getCanonicalHostName() :
                        sfDeployedHost().getCanonicalHostName();
            } catch (SmartFrogException srex) {
                //if the network is in a complete mess, we can't get a complete name.
                //ignore it and continue
            }

            if (sfParent == null) {
                r.addElement(ReferencePart.host((canonicalHostName)));

                if (sfProcessName() == null) {
                    // Process created when using sfDeployFrom (use by sfStart & sfRun)
                    r.addElement(ReferencePart.here(SmartFrogCoreKeys.SF_RUN_PROCESS));
                } else {
                    r.addElement(ReferencePart.here(sfProcessName()));
                }
            } else {
                //r = sfParent.sfCompleteName(); // Only if you had a hierarchy
                //of processes.
                r.addElement(ReferencePart.host((canonicalHostName)));

                Object key = sfParent.sfAttributeKeyFor(this);

                if (key != null) {
                    r.addElement(ReferencePart.here(key));
                }
            }
            sfCompleteName = r;
        }
        return sfCompleteName;
    }

    /**
     * Register a component under given name. Exception is thrown if the name is
     * already used. If name is null a name is made up for the component.
     * Consisting of the complete name of the component concatenated with the
     * current time.
     *
     * @param name name for component or null for made up name
     * @param comp component to register
     *
     * @return name of component used
     *
     * @throws SmartFrogException In case of resolution failure
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public synchronized Object sfRegister(Object name, Prim comp) throws SmartFrogException, RemoteException {

        if ((name != null) && (sfContext.containsKey(name))) {
            throw new SmartFrogResolutionException(sfCompleteNameSafe(),
                    (Reference) null,
                    "Name '" + name + "' already used",
                    (Object) null,
                    (Throwable) null,
                    comp);
        }

        Object compName = name;

        if (compName == null) {
            // Make up a name for the component first get complete name of
            // component
            // Add a timestamp to the end and convert to string
            compName = SmartFrogCoreKeys.SF_UNNAMED + (new Date()).getTime() + '_' +
                    registrationNumber++;
        }

        // Add as attribute
        sfAddAttribute(compName, comp);

        // Add liveness so we know when to unregister
        if (!sfChildList().contains(comp)) {
            sfAddChild(comp);
        }
        return compName;
    }

    /**
     * DeRegisters a deployed component
     *
     * @param comp component to register
     *
     * @return true if child is removed successfully else false
     *
     * @throws SmartFrogException when component was not registered
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public boolean sfDeRegister(Prim comp) throws SmartFrogException, RemoteException {
        boolean success = false;
        if (sfContext.contains(comp)) {
            sfContext.remove(sfContext.sfAttributeKeyFor(comp));
            success = true;
            //Remove all remaining instances of the same component if any. This is just to guard this corner case but it should no happen
            while (sfContext.contains(comp)){
               sfContext.remove(sfContext.sfAttributeKeyFor(comp)); 
            }
        }
        if (sfContainsChild(comp)) {
            success = sfRemoveChild(comp);
        }
        return success;
    }

    /**
     * Tries to find an attribute in the local context. If the attribute is not
     * found the thread will wait for a notification from
     * sfNotifySubprocessReady or until given timeout expires. Used to wait for
     * a new process compound to appear.
     *
     * @param name    name of attribute to wait for
     * @param timeout max time to wait in millis
     *
     * @return The object found
     *
     * @throws Exception attribute not found after timeout
     * @throws RemoteException if there is any network or remote error
     */
    @SuppressWarnings("ProhibitedExceptionDeclared")
    @Override
    public Object sfResolveHereOrWait(Object name, long timeout) throws Exception {
        long endTime = (new Date()).getTime() + timeout;
        synchronized (processLocks) {
            while (true) {
                try {
                    // try to return the attribute value
                    // if name in locks => process not ready, pretend not found...
                    if (processLocks.contains(name)) {
                        throw SmartFrogResolutionException.notFound(new
                                Reference(name),
                                sfCompleteNameSafe());
                    } else {
                        return sfResolveHere(name);
                    }
                } catch (SmartFrogResolutionException ex) {
                    // not found, wait for leftover timeout
                    long now = (new Date()).getTime();

                    if (now >= endTime) {
                        throw ex;
                    }
                    processLocks.add(name);
                    processLocks.wait(endTime - now);
                }
            }
        }
    }

    /**
     * Allows a sub-process to notify the root process compound that it is now
     * ready to receive deployment requests.
     *
     * @param name the name of the subprocess
     *
     * @throws RemoteException if there is any network or remote error
     */
    @Override
    public void sfNotifySubprocessReady(String name) throws RemoteException {

        // Notify any waiting threads that an attribute was added
        synchronized (processLocks) {
            processLocks.remove(name);
            processLocks.notifyAll();
        }
    }

    /**
     * Find a process for a given name in the root process compound. If the
     * process is not found it is created.
     *
     * @param name name of process
     *
     * @return ProcessCompound associated with the input name
     *
     * @throws Exception failed to deploy process
     */
    @SuppressWarnings("ProhibitedExceptionDeclared")
    @Override
    public ProcessCompound sfResolveProcess(Object name,
                                            ComponentDescription cd)
            throws Exception {
        ProcessCompound pc = null;

        if (sfParent() == null) { // I am the root
            Reference reference = new Reference(new HereReferencePart(
                    name));
            Object resolvedObject;
            try {
                resolvedObject = sfResolve(reference);
            } catch (SmartFrogResolutionException e) {
                //the reference failed to resolve 
                resolvedObject = null;
                if (sfLog().isTraceEnabled()) {
                    sfLog().trace(" Creating a new ProcessCompound: " + name.toString(), e);
                }
                pc = addNewProcessCompound(name, cd);
                pc.sfParentageChanged();
            }
            if (resolvedObject != null) {
                //this branch is only executed if the resolution succeeded. 
                //check it for being a valid type
                if (!(resolvedObject instanceof ProcessCompound)) {
                    //wrong type: throw a relevant exceptions
                    throw SmartFrogResolutionException.illegalClassType(reference,
                            sfCompleteNameSafe(),
                            resolvedObject,
                            resolvedObject.getClass().toString(),
                            "org.smartfrog.sfcore.processcompound.ProcessCompoundImpl");
                }
                pc = (ProcessCompound) resolvedObject;
            }
        } else { // I am a child process - find in the parent
            pc = ((ProcessCompound) sfParent()).sfResolveProcess(name, cd);
        }
        if (sfLog().isTraceEnabled()) {
            sfLog().trace("ProcessCompound '" + name + "' found.");
        }

        return pc;
    }

    // Internal
    //
    //

    /**
     * Checks is sub-processes are allowed through attribute system property
     * sfProcessAllow and checks that it is the root process compound. Uses
     * startProcess to start the actual sub-process. Then uses sfProcessTimeout
     * to wait for the new process compound to appear in attribute table. If
     * this does not happen the process is killed, and an exception is thrown.
     *
     * @param name name of new compound
     * @param cd component to deploy
     * @return ProcessCompound
     *
     * @throws Exception failed to deploy new naming compound
     */
    @SuppressWarnings({"ProhibitedExceptionDeclared", "ProhibitedExceptionThrown"})
    protected ProcessCompound addNewProcessCompound(Object name,
                                                    ComponentDescription cd)
            throws Exception {
        // Check if process creation is allowed
        boolean allowProcess;

        Object ap = sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_ALLOW, false);

        if (ap == null) {
            allowProcess = false;
        } else if (ap instanceof Boolean) {
            allowProcess = ((Boolean) ap).booleanValue() && sfIsRoot;
        } else {
            allowProcess = false;
            if (sfLog().isErrorEnabled()) {
                SmartFrogResolutionException srex =
                        SmartFrogResolutionException.illegalClassType(
                                Reference.fromString(SmartFrogCoreKeys.SF_PROCESS_ALLOW),
                                sfCompleteNameSafe(),
                                ap,
                                ap.getClass().getName(),
                                "java.lang.Boolean");
                sfLog().error(srex,srex);
            }
        }

        if (!allowProcess) {
            throw SmartFrogResolutionException.generic(sfCompleteName(),
                    "Not allowed to create process '" + name.toString() + '\'');
        }

        // Locate timeout
        Object timeoutObj;
        long timeout;
        timeoutObj = sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_TIMEOUT);
        try {
            timeout = 1000 * ((Number) timeoutObj).intValue();
        } catch (ClassCastException ccex) {
            sfLog().debug("Failed to get process timeout as an int  " + timeoutObj + ": "+ ccex, ccex);
            throw SmartFrogResolutionException.illegalClassType(
                    Reference.fromString(SmartFrogCoreKeys.SF_PROCESS_TIMEOUT),
                    sfCompleteNameSafe(),
                    timeoutObj,
                    timeoutObj.getClass().getName(),
                    "java.lang.Integer");
        }

        // Start process
        Process process = startProcess(name, cd);

        if (process != null) {
            // IMPORTANT COMMENT: We loose track of this two threads.
            // May be we should be keep a reference to them and later on
            // clean them nicely.
            // Two gobblers will redirect the System.out and System.err to
            // the System.out of the any error message?
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(),
                    "err");

            // any output?
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(),
                    "out");

            // kick them off
            errorGobbler.setName(name + ".errorGobbler");
            outputGobbler.setName(name + ".outputGobbler");
            errorGobbler.start();
            outputGobbler.start();
        }

        try {
            // Wait for new compound to appear and try to return it
            ProcessCompound newPc = (ProcessCompound) sfResolveHereOrWait(name,
                    timeout);
            if (sfLog().isDebugEnabled()) {
                try {
                    sfLog().debug("New ProcessCompound " + name + " created: " + newPc
                            .sfCompleteName());
                } catch (Throwable thr) {
                    sfLog().error("New ProcessCompound " + name + " create failed " +thr,thr);
                }
            }
            return newPc;
        } catch (Exception ex) {
            // failed to find new compound. Destroy process and re-throw
            // exception
            if (process != null) {
                process.destroy();
            }
            throw ex;
        }
    }

    /**
     * Does the work of starting up a new process. Looks up sfProcessJava,
     * sfProcessClass and sfProcessTimeout in the process compound to find out
     * which java to use, which class to start up and how long to max. wait for
     * it to appear in the compounds attribute table. Classpath is looked up
     * through standard system property java.class.path. The process is started
     * up with a -D option containing a quoted reference string giving the full
     * name for the new process. sfProcessINI attribute is passed as the -i URL
     * option to the sub-process indicating system properties to use for the new
     * process. Any property prefixed by 'org.smartfrog.sfcore.processcompound.jvm.'+NAME+property=value
     * will be added  only to the subprocess named 'NAME' as a parameter for the
     * JVM. The parameter will be "property+value". @see addProcessDefines Every
     * attribute described by cd.sfProcessConfig will be added to the command
     * line as "-Dorg.smartfrog.processcompound.ATTRIBUTE_NAME=ATTRIBUTE_VALUE"
     *
     * @param name name of new process
     * @param cd   component description with extra process configuration (ex.
     *             sfProcessConfig)
     *
     * @return new process
     *
     * @throws SmartFrogException failed to resolve all attributes
     * @throws IOException failed to start the process
     */
    protected Process startProcess(Object name, ComponentDescription cd)
            throws SmartFrogException, IOException {
        Vector<String> runCmd = new Vector<String>();

        addProcessJava(runCmd, cd);
        //addProcessClassName(runCmd,cd);

        addProcessDefines(runCmd, name);

        // addProcessClassPath(runCmd, name, cd); -> use environment variable classpath instead
        addProcessSFCodeBase(runCmd, name, cd);

        addProcessEnvVars(runCmd, cd);
        addProcessAttributes(runCmd, name, cd);
        addProcessClassName(runCmd, cd);

        String[] runCmdArray = new String[runCmd.size()];
        runCmd.copyInto(runCmdArray);
        if (sfLog().isTraceEnabled()) {
            sfLog().trace("startProcess[" + name.toString() + "].runCmd: " + runCmd.toString());
        }

        // build classpath
        String classPath = getProcessClassPath(cd);

        ProcessBuilder processBuilder = new ProcessBuilder(runCmdArray);
        processBuilder.environment().put("CLASSPATH", classPath);

        return processBuilder.start();
    }

    /**
     * Gets the process java path start command. Looks up the sfProcessJavaPath
     * attribute. sfProcessJava could be a String or objects with the right .toString() method.
     *
     * @param cd  component description with extra process configuration (ex. sfProcessConfig)
     * @return the command to run java as resolved or "" if not
     * @throws SmartFrogException failed to construct java command
     */
    protected String addProcessJavaPath(ComponentDescription cd) throws SmartFrogException {
        Object processCmd;
        processCmd = cd.sfResolve(SmartFrogCoreKeys.SF_PROCESS_JAVA_PATH, false);
        if (processCmd == null) {
            try {
                processCmd = sfResolve(SmartFrogCoreKeys.SF_PROCESS_JAVA_PATH, false);
            } catch (RemoteException e) {
                //ignore
            }
        }
        if (processCmd == null ) {
            return "";
        }
        if (processCmd instanceof String) {
            return ((String) processCmd);
        } else {
            return (processCmd.toString());
        }
    }

    /**
     * Gets the process java start command. Looks up the sfProcessJava
     * attribute. sfProcessJava could be a String or a Collection
     *
     * @param cmd cmd to append to
     * @param cd  component description with extra process configuration (ex.
     *            sfProcessConfig)
     *
     * @throws SmartFrogException failed to construct java command
     */
    protected void addProcessJava(Vector<String> cmd, ComponentDescription cd) throws SmartFrogException {
        Object processCmd;
        String processPath = addProcessJavaPath( cd);
        processCmd = cd.sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_JAVA, false);
        if (processCmd == null) {
            processCmd = sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_JAVA, false);
        }
        if (processCmd instanceof String) {
            cmd.addElement(processPath + processCmd);
        } else if (processCmd instanceof Collection) {
            Collection<String> commandList = (Collection<String>) processCmd;
            for(String element: commandList) {
                if(!element.isEmpty()) {
                    cmd.add(element);
                }
            }
            cmd.set(0, processPath + cmd.get(0));
        } else {
            cmd.addElement(processPath + processCmd.toString());
        }
    }

    /**
     * Get the class name for the subprocess. Looks up the sfProcessClass
     * attribute out of the current target
     *
     * @param cmd command to append to
     * @param cd  component description with extra process configuration (ex.
     *            sfProcessClass)
     *
     * @throws SmartFrogException failed to construct classname
     */
    protected void addProcessClassName(Vector<String> cmd, ComponentDescription cd)
            throws SmartFrogException {
        String pClass = (String) cd.sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_CLASS,
                false);
        if (pClass == null) {
            pClass = (String) sfResolveHere(SmartFrogCoreKeys.SF_PROCESS_CLASS);
        }
        cmd.addElement(pClass);
    }

    /**
     * Gets the current class path out of the system properties and returns it
     * as a command line parameter for the subprocess. The class path is created
     * reading one of the following in order selection:
     * <p/>
     * <ol>
     * <li>from a property named sfcore.processcompound.PROCESS_NAME.java.class.path.</li>
     * <li>attribute java.class.path inside sfProcessAttribute
     * componentDescription</li>
     * </ol>
     * <p/>
     * The result if any is added (default) to the system property:  System
     * property java.class.path or replaced if  sfProcessReplaceClassPath=true
     *
     * @param cmd  command to append ro
     * @param name process name
     * @param cd   component description with extra process configuration
     *
     * @throws SmartFrogException failed to construct classpath
     */
    //@todo document how new classpath works for subProcesses.
    protected void addProcessClassPath(Vector<String> cmd,
                                       Object name,
                                       ComponentDescription cd)
            throws SmartFrogException {
        String res = getProcessClassPath(cd);

        if (res != null) {
            cmd.addElement("-classpath");
            cmd.addElement(res);
        }
    }

    /**
     * Returns the classpath to use.
     *
     * @param cd the component description
     * @return null or classpath
     * @throws SmartFrogException failed to construct classpath
     */
    protected String getProcessClassPath(ComponentDescription cd)
            throws SmartFrogException {
        String res = null;
        String replaceBoolKey = SmartFrogCoreKeys.SF_PROCESS_REPLACE_CLASSPATH;
        String attributeKey = SmartFrogCoreKeys.SF_PROCESS_CLASSPATH;
        String sysPropertyKey = "java.class.path";
        String pathSeparator = SFSystem.getProperty("path.separator", ";");

        res = addProcessSpecialSystemVar(cd,
                res,
                replaceBoolKey,
                attributeKey,
                sysPropertyKey,
                pathSeparator);

        return res;
    }

    /**
     * Gets the current org.smartfrog.codebase out of the system properties and
     * returns it as a command line parameter for the subprocess. The class path
     * is created reading one of the following in order selection:
     * <p/>
     * <ol>
     * <li>from a property named sfcore.processcompound.PROCESS_NAME.'org.smartfrog.codebase'.</li>
     * <li>attribute 'org.smartfrog.codebase' inside sfProcessAttribute
     * componentDescription</li>
     * </ol>
     * <p/>
     * The result if any is added (default) to the system property:  System
     * property 'org.smartfrog.codebase' or replaced if
     * sfProcessReplaceCodeBase=true
     *
     * @param cmd  command to append ro
     * @param name process name
     * @param cd   component description with extra process configuration
     *
     * @throws SmartFrogException failed to construct classpath
     */
    //@todo document how new classpath works for subProcesses.
    protected void addProcessSFCodeBase(Vector<String> cmd,
                                        Object name,
                                        ComponentDescription cd)
            throws SmartFrogException {
        String res = null;
        String replaceBoolKey = SmartFrogCoreKeys.SF_PROCESS_REPLACE_SF_CODEBASE;
        String attributeKey = SmartFrogCoreKeys.SF_PROCESS_SF_CODEBASE;
        String sysPropertyKey = SmartFrogCoreProperty.codebase;
        String pathSeparator = " ";

        res = addProcessSpecialSystemVar(cd,
                res,
                replaceBoolKey,
                attributeKey,
                sysPropertyKey,
                pathSeparator);

        if (res != null) {
            cmd.addElement("-D" + sysPropertyKey + '=' + res);
        }
    }


    /**
     * Set the classpath or other path enviroment variable up. Includes the value of the current process
     * @param cd CD to work with
     * @param defVal default value
     * @param replaceBoolKey name of a boolean attribute to control replace/extend policy
     * @param attributeKey attribute
     * @param sysPropertyKey system property to set
     * @param pathSeparator path separator char
     * @return the calculated path
     * @throws SmartFrogResolutionException resolution problems.
     */
    private String addProcessSpecialSystemVar(ComponentDescription cd,
                                              String defVal,
                                              String replaceBoolKey,
                                              String attributeKey,
                                              String sysPropertyKey,
                                              String pathSeparator) throws
            SmartFrogResolutionException {
        Boolean replace;
        // Should we replace or overwrite?
        replace = ((Boolean) cd.sfResolve(replaceBoolKey, false));
        if (replace == null) {
            replace = ((Boolean) sfResolveHere (replaceBoolKey, false));
        }
        //by default add, not replace
        if (replace == null) {
            replace = Boolean.valueOf(false);
        }

        //Deployed description. This only happens during the first deployment of a SubProcess.
        String cdClasspath = convertToClassPath( cd.sfResolve(attributeKey, false));

        //This will read the system property for org.smartfrog.sfcore.processcompound.NAME. + key
        String envPcClasspath = SFSystem.getProperty(SmartFrogCoreProperty.propBaseSFProcess
                + SmartFrogCoreKeys.SF_PROCESS_NAME
                + attributeKey, null);

        //General description for process compound
        String pcClasspath = (String) sfResolveHere (attributeKey, false);

        //Takes previous process classpath (rootProcessClassPath)
        String sysClasspath = SFSystem.getProperty(sysPropertyKey, null);

        String res=defVal;
        if (replace.booleanValue()) {
            if (cdClasspath != null) {
                res = cdClasspath;
            } else if (envPcClasspath != null) {
                res = envPcClasspath;
            } else if (pcClasspath != null) {
                res = pcClasspath;
            } else if (sysClasspath != null) {
                res = sysClasspath;
            }
        } else {
            //   String pathSeparator = SFSystem.getProperty("path.separator",";");
            res = "";
            if (cdClasspath != null) {
                res = res + cdClasspath;
                if (!res.endsWith(pathSeparator)) {
                    res = res + pathSeparator;
                }
            }
            if (envPcClasspath != null) {
                res = res + envPcClasspath;
                if (!res.endsWith(pathSeparator)) {
                    res = res + pathSeparator;
                }
            }
            if (pcClasspath != null) {
                res = res + pcClasspath;
                if (!res.endsWith(pathSeparator)) {
                    res = res + pathSeparator;
                }
            }
            if (sysClasspath != null) {
                res = res + sysClasspath;
                if (!res.endsWith(pathSeparator)) {
                    res = res + pathSeparator;
                }
            }
        }
        return res;
    }

    /**
     * Method that checks object o and returns a String classpath from  Vector, Files ComponentDescription or ComponentDescription.
     * If o is a String, then it is returned withouth further modification.
     * The method uses the platform separator for the classpath.
     * @param o structured data to create the classpath
     * @return  String representing a classpath using the platform's separator.
     * If the wrong data object if found the null is returned and a message is logged
     */
    public String convertToClassPath(Object o) {
        if (o == null) return null;

        if (o instanceof String) {
            return (String)o;
        } else if (o instanceof Vector) {
            String fileSetString = java.util.Arrays.toString(((Vector)o).toArray());
            fileSetString = fileSetString.substring(1,fileSetString.length()-1);
            fileSetString = fileSetString.replace(", ",System.getProperty("path.separator"));
          return (fileSetString);
        } else if (o instanceof ComponentDescription) {
            ComponentDescription cdo = (ComponentDescription) o;
            try {
                Fileset fileset = FilesImpl.resolveFileset(cdo);
                return (fileset.toString());
            } catch (Exception rex){
                sfLog().err( rex );
                return null;
            }
        } else {
            sfLog().err("Wrong object type found in convertToClassPath. Object type: "+ o.getClass().getName());
            return null;
        }
    }

    

    /**
     * Constructs sequence of -D statements for the new sub-process by iterating
     * over the current process' properties and looking for those prefixed by
     * org.smartfrog (and not security properties) and creating an entry for
     * each of these. It modifies the sfProcessName property to be that
     * required. If security is on, you also pass some security related
     * properties. System properties are ordered alfabetically before they are
     * processed. Any property prefixed by 'org.smartfrog.sfcore.processcompound.jvm.'+NAME+.property=value
     * will be added  only to the subprocess named 'NAME' as a parameter for the
     * JVM. The parameter will be "value", "property" is only used to name
     * different properties in the initial command line. The property name is
     * important because all sys properties are ordered before they are
     * processed To change the class path in a SubProcess use:
     * 'org.smartfrog.sfcore.processcompound.java.class.path.NAME' Example:
     * org.smartfrog.sfcore.processcompound.jvm.test.propertyname_A=-Xrunpri
     * will add the property '-Xrunpri' to the processCompound named 'test'.
     *
     * @param cmd  command to append to
     * @param name name for subprocess
     *
     * @throws SmartFrogException failed to construct defines
     */
    protected void addProcessDefines(Vector<String> cmd, Object name)
            throws SmartFrogException {
        Properties props = System.getProperties();
        //Sys properties get ordered
        List<String> keysVector = new Vector<String>(props.size());
        for(String key : props.stringPropertyNames()) {
            keysVector.add(key);
        }
        // Order keys
        Collections.sort(keysVector);
        Properties newprops = new Properties();
        //process keys
        for(String key: keysVector) {
            try {
                if ((key.startsWith(SmartFrogCoreProperty.propBase)) &&
                        (!(key.startsWith(SFSecurityProperties.propBaseSecurity)))) {
                    if (!key.equals(SmartFrogCoreProperty.propBaseSFProcess + SmartFrogCoreKeys.SF_PROCESS_NAME)) {
                        // Special case resolved in addClassPath

                        //Add special parameters to named subprocesses
                        //@todo add Junit test for this feature
                        //@todo test what happens with special characters
                        // prefixed by 'org.smartfrog.sfcore.processcompound.jvm'+NAME+value
                        String specialParameters = SmartFrogCoreProperty.propBaseSFProcess + "jvm." + name + '.';

                        if (key.startsWith(specialParameters)) {
                            Object value = props.get(key);
                            String keyS = key.substring(specialParameters.length());
                            if (value == null) {
                                value = "";
                            }
                            cmd.addElement(value.toString());
                        } else {
                            //Properties to overwrite processcompound.sf attributes
                            Object value = props.get(key);
                            newprops.put(key, value);
                        }
                    } else {
                        //Special - Add property to name ProcessCompound
                        newprops.put(SmartFrogCoreProperty.propBaseSFProcess + SmartFrogCoreKeys.SF_PROCESS_NAME, name);
                    }
                }
            } catch (Exception ex) {
                if (sfLog().isErrorEnabled()) {
                    sfLog().error(ex, ex);
                }
            }
        }

        // Pass java.security.policy if it is defined
/*        String secProp = props.getProperty(JAVA_SECURITY_POLICY);

        if (secProp != null) {
            cmd.addElement("-D"+ JAVA_SECURITY_POLICY + '=' + secProp);
        }

        if (SFSecurity.isSecurityOn()) {

            // org.smartfrog.sfcore.security.propFile
            secProp = props.getProperty(SFSecurityProperties.propPropertiesFileName);

            if (secProp != null) {
                cmd.addElement("-D" +
                        SFSecurityProperties.propPropertiesFileName + '=' + secProp);
            }

            //org.smartfrog.sfcore.security.keyStoreName
            secProp = props.getProperty(SFSecurityProperties.propKeyStoreName);

            if (secProp != null) {
                cmd.addElement("-D" + SFSecurityProperties.propKeyStoreName + '=' + secProp);
            }
        }*/
        //propagate any other values
        for (String key : keysVector) {
            if (newprops.get(key) == null) {
                newprops.put(key, props.get(key));
            }
        }
        //now add to the command list
        for (String key : newprops.stringPropertyNames()) {
            cmd.addElement("-D" + key + "=" + newprops.getProperty(key));
        }

    }

    /**
     * Resolves sfProcessConfig and adds to it all SystemProperties that start
     * with org.smartfrog.processcompound.${name}
     *
     * @param name name to build the system property under
     * @param cd ComponentDescription
     *
     * @return ComponentDescription
     * @throws SmartFrogResolutionException resolution failure
     */
    private ComponentDescription getProcessAttributes(Object name,
                                                      ComponentDescription cd)
            throws SmartFrogResolutionException {
        ComponentDescription sfProcessAttributes = null;
        sfProcessAttributes = (ComponentDescription) cd.sfResolveHere( SmartFrogCoreKeys.SF_PROCESS_CONFIG,false);
        if (sfProcessAttributes == null) {
            sfProcessAttributes = new ComponentDescriptionImpl(null, new ContextImpl(), false);
        }
        ComponentDescriptionImpl.addSystemProperties(SmartFrogCoreProperty.propBaseSFProcess + name, sfProcessAttributes);
        return sfProcessAttributes;
    }

    /**
     * Constructs sequence of -D statements for the new sub-process by iterating
     * over the sfProcessConfig ComponentDescription.
     *
     * @param name name of the process
     * @param cmd command to append to
     * @param cd  component description with extra process configuration (ex.
     *            sfProcessConfig)
     *
     * @throws SmartFrogException failed to construct defines
     */
    protected void addProcessAttributes(Vector<String> cmd,
                                        Object name,
                                        ComponentDescription cd)
            throws SmartFrogException {
        ComponentDescription sfProcessAttributes = getProcessAttributes(name,
                cd);
        Object key = null;
        Object value = null;
        for (Iterator i = sfProcessAttributes.sfAttributes(); i.hasNext();) {
            key = i.next().toString();
            value = sfProcessAttributes.sfResolveHere(key);
            cmd.addElement("-D" +
                    SmartFrogCoreProperty.propBaseSFProcess +  key.toString() + '=' + value.toString());
        }
    }

    /**
     * Constructs sequence of -D statements for the new sub-process by iterating
     * over the sfProcessEnvVars ComponentDescription
     *
     * @param cmd command to append to
     * @param cd  component description with extra process configuration (ex.
     *            sfProcessConfig)
     *
     * @throws SmartFrogException failed to construct defines
     */
    protected void addProcessEnvVars(Vector<String> cmd, ComponentDescription cd)
            throws SmartFrogException {
        ComponentDescription sfProcessEnvVars = (ComponentDescription) cd.sfResolveHere( SmartFrogCoreKeys.SF_PROCESS_ENV_VARS, false);
        if (sfProcessEnvVars == null) {
            return;
        }
        Object key;
        Object value;
        for (Iterator i = sfProcessEnvVars.sfAttributes(); i.hasNext();) {
            key = i.next().toString();
            value = sfProcessEnvVars.sfResolveHere(key);
            cmd.addElement("-D" + key.toString() + '=' + value.toString());
        }
    }

    //Tags - Special case for rootProcess: rootProcess does not have tags.

    /**
     * Set the TAGS for this component. TAGS are simply uninterpreted strings
     * associated with each attribute. rooProcess does not do anything.
     * The rootProcess does not have tags.
     *
     * @param tags a set of tags
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    public void sfSetTags(Set tags)
            throws RemoteException, SmartFrogRuntimeException {
        if (sfParent != null) {
            super.sfSetTags(tags);
        }
    }

    /**
     * Get the TAGS for this process compound. TAGS are simply uninterpreted
     * strings associated with each attribute. rooProcess returns null.
     * The rootProcess does not have tags.
     *
     * @return the set of tags
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    @Override
    public Set sfGetTags() throws RemoteException, SmartFrogRuntimeException {
        if (sfParent != null) {
            return super.sfGetTags();
        } else {
            return null;
        }
    }

    /**
     * add a tag to the tag set of this component. The rootProcess does not have
     * tags.
     *
     * @param tag a tag to add to the set
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    @Override
    public void sfAddTag(String tag)
            throws RemoteException, SmartFrogRuntimeException {
        if (sfParent != null) {
            super.sfAddTag(tag);
        }
    }

    /**
     * remove a tag from the tag set of this component if it exists. The rootProcess
     * does not have tags.
     *
     * @param tag a tag to remove from the set
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    public void sfRemoveTag(String tag)
            throws RemoteException, SmartFrogRuntimeException {
        if (sfParent != null) {
            super.sfRemoveTag(tag);
        }
    }

    /**
     * add a tag to the tag set of this component. The rootProcess does not have
     * tags.
     *
     * @param tags a set of tags to add to the set
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    @Override
    public void sfAddTags(Set tags)
            throws RemoteException, SmartFrogRuntimeException {
        if (sfParent != null) {
            super.sfAddTags(tags);
        }
    }

    /**
     * Remove a tag from the tag set of this component if it exists.
     * The rootProcess does not have tags.
     *
     * @param tags a set of tags to remove from the set
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    @Override
    public void sfRemoveTags(Set tags)
            throws RemoteException, SmartFrogRuntimeException {
        if (sfParent != null) {
            super.sfRemoveTags(tags);
        }
    }

    /**
     * Return whether or not a tag is in the list of tags for this component
     * rootProcess returns false.  rootProcess does not have tags.
     *
     * @param tag the tag to chack
     *
     * @return whether or not the attribute has that tag
     *
     * @throws RemoteException network or RMI problems
     * @throws SmartFrogRuntimeException the attribute does not exist;
     */
    @Override
    public boolean sfContainsTag(String tag)
            throws RemoteException, SmartFrogRuntimeException {
        return sfParent != null && super.sfContainsTag(tag);
    }

}
