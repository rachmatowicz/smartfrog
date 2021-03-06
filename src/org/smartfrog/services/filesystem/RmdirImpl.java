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
package org.smartfrog.services.filesystem;

import org.smartfrog.sfcore.common.SmartFrogDeploymentException;
import org.smartfrog.sfcore.common.SmartFrogException;
import org.smartfrog.sfcore.prim.TerminationRecord;

import java.io.File;
import java.rmi.RemoteException;

/**
 * Component to delete directoreis
 */

public class RmdirImpl extends FileUsingComponentImpl implements Mkdir {

    /**
     * Constructor.
     * @throws RemoteException  In case of network/rmi error
     */
    public RmdirImpl() throws RemoteException {
    }

    /**
     * read in the directory settings and bind the file attributes.
     *
     * @throws SmartFrogException failure while starting
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public synchronized void sfDeploy() throws SmartFrogException, RemoteException {
        super.sfDeploy();

        String dir;
        File parentDir = null;
        String parent;
        parent = FileSystem.lookupAbsolutePath(this,
                ATTR_PARENT,
                null,
                null,
                false,
                null);
        if (parent != null) {
            parentDir = new File(parent);
        }

        dir = FileSystem.lookupAbsolutePath(this, Mkdir.ATTR_DIR, null, parentDir, true, null);
        File directory = new File(dir);
        bind(directory);
    }

    /**
     * we only create the directory at startup time, even though we bond at deploy time.
     * @throws SmartFrogException  failure in starting
     * @throws RemoteException In case of network/rmi error
     */
    @Override
    public synchronized void sfStart() throws SmartFrogException,
            RemoteException {
        super.sfStart();
        File directory = getFile();
        try {
            if (directory.exists()) {
                FileSystem.recursiveDelete(directory);
            }
        } catch (SecurityException e) {
            //failure is turned into a security problem; we catch it and make it meaningful
            throw new SmartFrogDeploymentException(
                    "Security blocked the deletion of the directory " + directory,
                    e,
                    this);
        }

        maybeStartTerminator("Rmdir");
    }
}
