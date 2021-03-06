/** (C) Copyright 2011 Hewlett-Packard Development Company, LP

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
package org.smartfrog.services.utils.echo;

import org.smartfrog.sfcore.common.SmartFrogException;
import org.smartfrog.sfcore.prim.Prim;
import org.smartfrog.sfcore.prim.PrimImpl;
import org.smartfrog.sfcore.utils.ComponentHelper;

import java.rmi.RemoteException;

public class EchoImpl extends PrimImpl implements Prim {
    public static final String ATTR_MESSAGE = "message";


    /**
     *  Standard remotable constructor - must be provided.
     *
     *  @throws RemoteException In case of network/rmi error
     */
    public EchoImpl() throws RemoteException {
    }

    /**
     *  Initialization template method sfDeploy.
     *  Reads the string attribute "name" as the id of the printer, defaulting 
     *  to the sfCompleteNameSafe.
     *  Overrides PrimImpl.sfDeploy.  
     * @exception SmartFrogException In case of error while deploying
     * @exception RemoteException In case of network/rmi error
     */
    public synchronized void sfDeploy() throws SmartFrogException,
            RemoteException{
        super.sfDeploy();
    }

    @Override
    public void sfStart() throws SmartFrogException, RemoteException {
        super.sfStart();
        String message = sfResolve(ATTR_MESSAGE, "", true);
        sfLog().info(message);
        new ComponentHelper(this).sfSelfDetachAndOrTerminate(null, message, null, null);
    }
}

