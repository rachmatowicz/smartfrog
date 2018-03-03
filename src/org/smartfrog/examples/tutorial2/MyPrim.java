package org.smartfrog.examples.tutorial2;

import org.smartfrog.sfcore.common.SmartFrogResolutionException;
import org.smartfrog.sfcore.prim.Prim;
import org.smartfrog.sfcore.prim.PrimImpl;
import org.smartfrog.sfcore.prim.TerminationRecord;
import org.smartfrog.sfcore.common.SmartFrogException;

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
public class MyPrim extends PrimImpl implements Prim {
    /* any component specific declarations */
    public MyPrim() throws RemoteException {
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
        sleep(5);
    }

    public synchronized void sfTerminateWith(TerminationRecord tr) {
        sfLog().out("SmartFrog " + sfCompleteNameSafe() + " terminating");

        /* any component specific termination code */
        super.sfTerminateWith(tr);
    }

    /* any component specific methods go here*/

    void sleep(long secs) throws RemoteException {

        sfLog().out("SmartFrog " + sfCompleteName() + " starting to feel sleepy ...");

        try {
            Thread.sleep(secs * 1000);
        }
        catch(InterruptedException ie) {
        }

        sfLog().out("SmartFrog " + sfCompleteName() + " ... waking up!");
    }

    void getConfig(){
        try {
            Boolean debug = (Boolean) sfResolve("debug");
            Integer retryCount = (Integer) sfResolve("retryCount");
            String databaseRef = (String) sfResolve("databaseRef");
            String myComponentName = sfCompleteNameSafe().toString();

            sfLog().out("My name is " + myComponentName + " and here is my config:\n debug = " + debug + ", retryCount = " + retryCount + ", databaseRef = " + databaseRef);
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