/* (C) Copyright 2008 Hewlett-Packard Development Company, LP

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
package org.smartfrog.services.deploydir;

import org.smartfrog.sfcore.common.SmartFrogException;

import java.io.File;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Created 07-Mar-2008 16:56:45
 */


public interface DirectoryWatcherEvent {

    /**
     * Notify of a directory changed
     *
     * @param current the current directory
     * @param added   added files
     * @param removed removed files
     * @throws SmartFrogException SmartFrog problems
     * @throws RemoteException    network problems
     */
    void directoryChanged(List<File> current, List<File> added, List<File> removed) throws SmartFrogException,
            RemoteException;
}
