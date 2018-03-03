package org.smartfrog.examples.tutorial2.clientserver;

import org.smartfrog.sfcore.common.SmartFrogException;
import org.smartfrog.sfcore.common.SmartFrogResolutionException;
import org.smartfrog.sfcore.prim.Prim;
import org.smartfrog.sfcore.prim.PrimImpl;
import org.smartfrog.sfcore.prim.TerminationRecord;
import org.smartfrog.sfcore.reference.Reference;

import java.rmi.RemoteException;

/**
 * A first experience with a smartfrog component!
 *
 * A reminer from the docs: "the code and Smartfrog configuration snapshots provided so far provided almost everything you need to:
 *   create a SmartFrog component
 *   supply it with configuration parameters
 *   deploy it, and then
 *   access the configuration parameters from within the running component"
 *
 * This example tries to highlight these features
 */
public class Client extends PrimImpl implements Prim, Runnable {
    /* any component specific declarations */
    Thread terminator ;

    public Client() throws RemoteException {
    }

    public synchronized void sfDeploy() throws RemoteException, SmartFrogException {
        super.sfDeploy();

        /* any component specific initialization code */
        sfLog().out("SmartFrog " + sfCompleteName() + " deployed");

        /* calli to get the config */
        getConfig();
    }

    public synchronized void sfStart() throws RemoteException, SmartFrogException {
        super.sfStart();

        /* any component specific start-up code */
        sfLog().out("SmartFrog " + sfCompleteName() + " started");

        Boolean simulateTermination = (Boolean) sfResolve("simulateTermination");
        if (simulateTermination.booleanValue()) {
            terminator = new Thread(this);
            terminator.start();
        }
    }

    public void run() {

        // get the (possibly remote) component that we think has terminated
        Reference serverName = new Reference("server");
        Reference failedName = null;
        // how to reference the component?
        Server server = null;
        try {
            server = (Server) sfResolve(serverName, true);
            failedName = ((Prim) server).sfCompleteName();
        } catch (Exception ex) {
            // not handled very well ...
            sfLog().out("background thread got exception: " + ex.getMessage()) ;
        }

        // pretend to sleep for 10 seconds and then call terminate
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException iex) {
            //
        }

        sfLog().out("SmartFrog " + sfCompleteNameSafe() + " simulateTermination = true, calling sfTerminate with failed name " + failedName);
        // call sfTermiante()
        TerminationRecord tr = TerminationRecord.abnormal("server has terminated!" , failedName);
        this.sfTerminate(tr);
    }

    public synchronized void sfTerminateWith(TerminationRecord tr) {
        sfLog().out("SmartFrog " + sfCompleteNameSafe() + " terminating");

        /* any component specific termination code */
        super.sfTerminateWith(tr);
    }

    /* any component specific methods go here*/

    void getConfig(){
        try {
            Boolean debug = (Boolean) sfResolve("debug");
            Integer port = (Integer) sfResolve("port");
            Boolean simulateTermination = (Boolean) sfResolve("simulateTermination");
            String myComponentName = sfCompleteNameSafe().toString();

            sfLog().out("My name is " + myComponentName + " and here is my config:\n" +
                    " debug = " + debug + ", port = " + port + ", simulateTermination = " + simulateTermination);
        } catch (ClassCastException ccex) {
            // handle class cast exception (when casting values from Object to value type)
            String message = "Casting error" + ccex.getMessage();
            if (sfLog().isErrorEnabled()) {
                sfLog().error(message, ccex);
            }
        } catch (SmartFrogResolutionException sfrex) {
            // handle resolution exception; there may not be a value available!
            String message = "Problem resolving configuration element";
            if (sfLog().isErrorEnabled()) {
                sfLog().error(message, sfrex);
            }

        } catch (RemoteException rex) {
            // handle RMI remote exception; the configuration context may be spread over other components

        }
    }
}