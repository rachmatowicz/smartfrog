package org.smartfrog.examples.recipes;

import java.rmi.*;
import org.smartfrog.sfcore.common.*;
import org.smartfrog.sfcore.prim.*;

public class Child extends PrimImpl implements Prim {

    public Child() throws RemoteException {
    }
    public void sfDeploy() throws RemoteException,SmartFrogException{
        try {
            super.sfDeploy();
            sfLog().out("SmartFrog "+sfCompleteName()+" deployed");
            sfLog().out("SmartFrog child process deployed");
        } catch (Throwable t) {
            t.printStackTrace();
            throw SmartFrogException.forward(t);
        }
    }

    public void sfStart() throws RemoteException,SmartFrogException{
        try {
            super.sfStart();
            sfLog().out("SmartFrog child process started");
        } catch (Throwable t) {
            t.printStackTrace();
            throw SmartFrogException.forward(t);
        }
    }

    public void sfTerminateWith(TerminationRecord tr) {
        try {
            // close down everything
            sfLog().out("SmartFrog child process terminating");
        } catch (Exception e) {}
        super.sfTerminateWith(tr);
    }
}