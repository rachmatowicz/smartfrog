package org.smartfrog.examples.recipes;

import java.rmi.*;
import org.smartfrog.sfcore.common.*;
import org.smartfrog.sfcore.prim.*;
import org.smartfrog.sfcore.compound.*;
import org.smartfrog.sfcore.componentdescription.*;

/**
 * An example of dynamically creating a Child component based on its parsed ComponentDescription
 */

public class Parent extends CompoundImpl implements Compound {
    ComponentDescriptionImpl child = null;
    public Parent() throws RemoteException {
    }
    public void sfDeploy() throws RemoteException,SmartFrogException{
        super.sfDeploy();
        try {
            sfLog().out("SmartFrog "+sfCompleteName()+" deploying");
            child = (ComponentDescriptionImpl)sfResolve("myChild");
            sfLog().out("Child configuration reference found");
            sfLog().out("SmartFrog parent process deployed");
        } catch (Throwable t) {
            t.printStackTrace();
            throw SmartFrogException.forward(t);
        }
    }

    public void sfStart() throws RemoteException,SmartFrogException{
        try {
            super.sfStart();
            sfLog().out("SmartFrog parent process starting");
            sfCreateNewChild("Child", child, null);
            sfLog().out("SmartFrog parent process started");
        } catch (Throwable t) {
            t.printStackTrace();
            throw SmartFrogException.forward(t);
        }
    }

    public void sfTerminateWith(TerminationRecord tr) {
        try {
            // close down everything
            sfLog().out("SmartFrog parent process terminating");
        } catch (Exception e) {}
        super.sfTerminateWith(tr);
    }
}