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
package org.smartfrog.services.filesystem.files;

import org.smartfrog.sfcore.common.SmartFrogException;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created 04-Feb-2008 14:09:45
 */

public interface Files extends Remote {

    /**
     * A LAZY reference to another source of files:  {@value}
     */
    String ATTR_FILES = "files";

    /**
     * set at runtime a List of files as strings: {@value}
     */
    String ATTR_FILELIST = "fileList";

    /**
     * Fileset attribute:  {@value}
     */
    String ATTR_FILE_SET = "fileSet";

    /**
     * FileSetString attribute:  {@value}
     */
    String ATTR_FILE_SET_STRING = "fileSetString";

    /**
     * SF component attribute {@value}
     */
    String ATTR_DIR = "dir";

    /**
     * SF component attribute {@value}
     */
    String ATTR_PATTERN = "pattern";
    /**
     * SF component attribute {@value}
     */
    String ATTR_CASESENSITIVE = "caseSensitive";
    /**
     * SF component attribute {@value}
     */
    String ATTR_INCLUDEHIDDENFILES = "includeHiddenFiles";


    /**
     * This is a count of files. If set, it asserts how many files are expected. If clear, it is set on deployment as a
     * log of how many files were found.
     */
    String ATTR_FILECOUNT = "fileCount";

    /**
     * This is a count of files. If set, it asserts how many files are expected.
     * If clear, it is set on deployment as a log of how many files were found.
     */
    String ATTR_MINFILECOUNT = "minFileCount";

    /**
     * This is a count of the max number of files that the pattern should match. If <0, there is no limit.
     */
    String ATTR_MAXFILECOUNT = "minFileCount";

    /**
     * Return a list of files that match the current pattern. This may be a compute-intensive
     * operation, so cache the result.
     * Note that filesystem race conditions do not guarantee all the files listed still exist...check before acting
     * @return a list of files that match the pattern, or an empty list for no match
     * @throws RemoteException when the network plays up
     * @throws SmartFrogException if something else went wrong
     */

    File[] listFiles() throws RemoteException, SmartFrogException;

    /**
     * Get the base directory of these files (may be null)
     * @return the base directory
     * @throws RemoteException when the network plays up
     * @throws SmartFrogException if something else went wrong
     */
    File getBaseDir() throws RemoteException, SmartFrogException;
}
