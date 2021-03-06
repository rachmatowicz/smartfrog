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

package org.smartfrog.services.utils.logtofile;

import org.smartfrog.services.display.PrintErrMsgInt;
import org.smartfrog.services.display.PrintMsgInt;
import org.smartfrog.sfcore.common.SmartFrogCoreKeys;
import org.smartfrog.sfcore.common.SmartFrogException;
import org.smartfrog.sfcore.common.SmartFrogLifecycleException;
import org.smartfrog.sfcore.common.TerminatorThread;
import org.smartfrog.sfcore.prim.Prim;
import org.smartfrog.sfcore.prim.PrimImpl;
import org.smartfrog.sfcore.prim.TerminationRecord;
import org.smartfrog.sfcore.processcompound.SFProcess;
import org.smartfrog.sfcore.reference.Reference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Class used to log system.out and system.err messages into a file.
 *
 */
public class SFLogToFile extends PrimImpl implements Prim, PrintMsgInt, PrintErrMsgInt {

    /*
      Date format
     */
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS yyyy/MM/dd");

    /**
     * Filename attribute: {@value}
     */
    public static final String ATTR_FILE_NAME = "fileName";
    /**
     * Path attribute: {@value}
     */
    public static final String ATTR_PATH = "path";
    /**
     * File extension attribute: {@value}
     */
    public static final String ATTR_FILE_EXTENSION = "fileExtension";
    /**
     *  flag to say only log in the root process attribute: {@value}
     */
    public static final String ATTR_LOG_ONLY_IN_ROOT_PROCESS = "logOnlyInRootProcess";
    /**
     * flag to say use the date in the filename : {@value}
     */
    public static final String ATTR_USE_DATE = "useDate";
    /**
     *  flag to use the time attribute: {@value}
     */
    public static final String ATTR_USE_TIME = "useTime";

    /**
     * Default filename: {@value}
     */
    public static final String LOG_FILE = "logFile";
    /**
     * Default extension: {@value}
     */
    public static final String LOG_FILE_EXTENSION = ".log";
    /** Reference for filename. */
    private static final Reference fileNameRef = new Reference(ATTR_FILE_NAME);
    /** Reference for path. */
    private static final Reference pathRef = new Reference(ATTR_PATH);
    /** Reference for file extension. */
    private static final Reference fileExtensionRef = new Reference(
            ATTR_FILE_EXTENSION);
    /** Reference for logOnlyInRootProcess. */
    private static final Reference logOnlyInRootProcessRef = new Reference(
            ATTR_LOG_ONLY_IN_ROOT_PROCESS);
    /** Reference for processName. */
    private static final Reference processNameRef = new Reference(SmartFrogCoreKeys.SF_PROCESS);
    /** Reference for useDate. */
    private static final Reference useDateRef = new Reference(ATTR_USE_DATE);
    /** Reference for useTime. */
    private static final Reference useTimeRef = new Reference(ATTR_USE_TIME);


    /** String name for file name. */
    private String fileName;
    /** String name for file extension. */
    private String fileExtension;
    /** String name for path. */
    private String path;
    /** String name for full filename. */
    private String fullFileName;
    /** Flag indicating whether logOnlyInRootProcess or not. */
    private boolean logOnlyInRootProcess;
    /** String name for processName. */
    private String processName;

    /** cache the standard output, we'll need to put them back on termination */
    private final OutputStream dout = System.out;

    //   InputStream din = System.in;
    /** Output stream. */
    private PrintStream originalSysOut = System.out;
    /** Error stream. */
    private PrintStream originalSysErr = System.err;

    /** Direct brand new outputs to a file. */
    private PrintStream newOut = null;

    /** The file itself. */
    private File logFile;
    /** PrintMsgInt object. */
    private PrintMsgInt printMsgImp = null;
    /** Flag indicating whether to redirect to std or not. */
    private boolean redirectStd = false;
    /** Flag indicating whether to use time or not. */
    private boolean useTime = false;
    /** Flag indicating whether to useDate or not. */
    private boolean useDate = false;

    /** Will terminate the component if it is in a subProcess during sfStart. */
    private boolean detachAndTerminate = false;
    private String logFilePath;

    /**
     * Constructor.
     *
     * @throws RemoteException in case of metwork/rmi error.
     */
    public SFLogToFile() throws RemoteException {
    }

    /**
     * Reads attributes for the log file and deploys and component.
     * @throws SmartFrogException if there is any error while reading the
     * attributes or deploying the component
     *
     * @throws SmartFrogException in case of error while deploying
     * @throws RemoteException if there is any newwork or remote error
     */
    @Override
    public synchronized void sfDeploy() throws SmartFrogException,
            RemoteException {
        super.sfDeploy();

        // get path , filename & fileExtension
        path = sfResolve(pathRef, "." + File.pathSeparator, false);
        fileName = sfResolve(fileNameRef, LOG_FILE, false);
        fileExtension = sfResolve(fileExtensionRef, LOG_FILE_EXTENSION, false);
        useDate = sfResolve(useDateRef, false, false);
        useTime = sfResolve(useTimeRef, false, false);
        logOnlyInRootProcess = sfResolve(logOnlyInRootProcessRef, true,
                                         false);
        processName = sfResolve(processNameRef, "", false);

        // create the file & redirect the outputs
        try {
            if (processName.equals(SmartFrogCoreKeys.SF_ROOT_PROCESS) || !logOnlyInRootProcess) {
                createFile();
                redirectOutputs();
            } else {
                //detatch and terminate component during sfStart
                detachAndTerminate = true;
            }
        } catch (Throwable t) { // catch all throwable ??
            SmartFrogLifecycleException.sfDeploy(t.getMessage(), t, this);
        }
    }

    /**
     * Life cycle method to start the component.
     * @throws SmartFrogException if there is any error while reading the
     * attributes or deploying the component
     *
     * @throws SmartFrogException in case of error while starting
     * @throws RemoteException if there is any newwork or remote error
     */
    @Override
    public synchronized void sfStart() throws SmartFrogException,
            RemoteException {
        super.sfStart();
        if (detachAndTerminate) {
            TerminationRecord termR =
                    TerminationRecord.normal("Not deployed in rootProcess", this.sfCompleteName());
            TerminatorThread terminator = new TerminatorThread(this, termR).detach();
            terminator.start();
        }
    }

    /**
     * Creates the file using attributes.
     *
     * @throws Exception error while creating file
     */
    public void createFile() throws Exception {
        // check the path ends correctly
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
        fullFileName = path;
        fullFileName += fileName;
        fullFileName += "_";

        // add hostname and date to file name
        try {
            String hostName = SFProcess.sfDeployedHost().getHostName();
            fullFileName += hostName;
        } catch (Exception e) {
            // log the exception
            // ignore
        }

        // FORMAT THE DATE
        Date now = new Date(System.currentTimeMillis());
        String formatDateString = "yyyy-MM-dd";
        String formatTimeString = "HH-mm";

        String timeFileName = "";

        if (useTime) {
            timeFileName = "_" + new SimpleDateFormat(formatTimeString).format(now);
        }

        String dateFileName = "";

        if (useDate) {
            dateFileName = "_" + new SimpleDateFormat(formatDateString).format(now);
        }

        // add the extension
        fullFileName += (dateFileName + timeFileName + fileExtension);
        logFile = new File(fullFileName);
        logFilePath = logFile.getAbsolutePath();
        System.out.println("Log File created at " + logFilePath);
    }

    /**
     * Redirects the outputs to a file.
     * @throws Exception if any io error
     */
    public void redirectOutputs() throws Exception {
        if (logFile != null) {
            FileOutputStream fos = new FileOutputStream(logFile);
            newOut = new PrintStream(fos);

            try {
                // Redirecting standard output
                System.setOut(newOut);
                System.setErr(newOut);

                //      System.setIn(din);
            } catch (Exception e) {
                System.setOut(originalSysOut);
                System.setErr(originalSysErr);
                if (sfLog().isErrorEnabled()) {
                    sfLog().error("Error in SFDisplay.sfDeploy():" + e, e);
                }
            }
        }
    }

    /**
     *  Terminates the component and put the out and err streams in their
     *  original statee.
     *
     *  @param t TerminationRecord object
     */
    @Override
    public synchronized void sfTerminateWith(TerminationRecord t) {
        if (processName.equals(SmartFrogCoreKeys.SF_ROOT_PROCESS)) {
            try {
                System.setErr(originalSysErr);
                System.setOut(originalSysOut);
            } catch (Exception e) {
            }
        }

        super.sfTerminateWith(t);
    }

    /**
     * Method of interface PrintMsgInt
     *
     *@param  msg  message
     */
    @Override
    public synchronized void printMsg(String msg) {
    }

    /**
     * Method of interface PrintErrMsgInt
     *@param  msg  error message
     */
    @Override
    public synchronized void printErrMsg(String msg) {
    }

    /**
     * Formats the message by prefixing it with timestamp in
     * HH:mm:ss.SSS dd/MM/yy format.
     *
     *@param  msg  The message to be logged
     *@return The formatted message
     */
    private String formatMsg(String msg) {
        msg = "[" + (dateFormat.format(new Date(System.currentTimeMillis()))) + "] " + msg;
        return msg;
    }

    // Implementing StreamIntf

    /**
     *  Gets the outputStream attribute of the SFDisplay object
     *
     *@return The outputStream value
     */
    public OutputStream getOutputStream() {
        if (this.logFile != null) {
            return newOut;
        } else {
            return null;
        }
    }

    /**
     *  Gets the errorStream attribute of the SFDisplay object
     *
     *@return The errorStream value
     */
    public OutputStream getErrorStream() {
        return newOut;
    }

    /**
     *  Gets the inputStream attribute of the SFDisplay object
     *
     *@return The inputStream value
     */
    public InputStream getInputStream() {
        return null;
    }

    public String getLogFilePath() {
        return logFilePath;
    }
}
