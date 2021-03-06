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

package org.smartfrog.sfcore.common;


import org.smartfrog.SFSystem;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Vector;


/**
 * Parses the SFSystem arguments into an option set. Options are seperated by optionFlagIndicator characters.
 */
public class ParseOptionSet {

    /**
     * Character indicating the start of each option.
     */
    protected char optionFlagIndicator = '-';

    /**
     * Length of each option.
     */
    protected byte optionLength = 1;

    /**
     * Usage string for SFParse.
     */
    public String usage = "\n" +
            "* Usage: java org.smartfrog.SFParse [-v] [-q] [-d] [-r] [-R] [-c] [-f] filename [-D] \n" +
            "   or: java org.smartfrog.SFParse -?";

    /**
     * Help string for SFSystem.
     */
    public String helpTxt = "\n* Parameters: " + "\n" +
            "    -v:             verbose, print every parser phase\n" +
            "    -q:             quiet, no phase printed (overwrites verbose)\n" +
            "    -d:             show 'description' parser phase \n" +
            "    -r:             show status parsing report \n" +
            "    -R:             show status parsing report in html file named \n" +
            "                     <filename>_report.html (only works with -f)\n" +
            "    -f filename:    file with a list of SmartFrog descriptions to parse\n" +
            "                    if -f is not present then filename is directly parsed\n" +
            "    -D:             show diagnostics report \n" +
            "    -c:             show console\n" +
            "    -?:             this help text.\n" +
            " \n" +
            "   Examples: SFParse -r org/smartfrog/examples/counter/example.sf \n";

    /**
     * Error string for SFParse.
     */
    public String errorString = null;

    /**
     * ExitCode for SFParse.
     */
    public int exitCode = ExitCodes.EXIT_ERROR_CODE_BAD_ARGS;

    /**
     * Flag indicating the help option.
     */
    public boolean help = false;

    /**
     * Flag indicating the verbose option.
     */
    public boolean verbose = false;

    /**
     * Flag indicating the quite option.
     */
    public boolean quiet = false;

    /**
     * Flag indicating to show the description parser phase or not.
     */
    public boolean description = false;

    /**
     * Flag indicating to load description from file or not.
     */
    public boolean loadDescriptionsFromFile = false;

    /**
     * File to be parsed.
     */
    public String fileName = null;

    /**
     * List of files to be parsed.
     */
    public Vector<String> filesList = null;

    /**
     * Flag indicating to show status parsing report or not.
     */
    public boolean statusReport = false;

    /**
     * Flag indicating to show status parsing report in html formal or not.
     */
    public boolean statusReportHTML = false;

    /**
     * Flag indicating if diagnostics was requested.
     */
    public boolean diagnostics = false;
    /**
     * Show console.
     */
    public boolean showConsole = false;

    /**
     * Create an empty option set (for programmatic work)
     */
    public ParseOptionSet() {

    }

    /**
     * Creates an OptionSet from an array of arguments.
     *
     * @param args arguments to create from
     */
    public ParseOptionSet(String[] args) {
        try {
            int i = 0;

            while ((i < args.length) & (errorString == null)) {
                try {
                    if ((args[i].charAt(0) == optionFlagIndicator) && (args[i].length() > 1)) {
                        switch (args[i].charAt(1)) {
                            case '?':
                                errorString = helpTxt;
                                exitCode = ExitCodes.EXIT_CODE_SUCCESS;
                                help = true;
                                break;

                            case 'v':
                                verbose = true;
                                break;

                            case 'q':
                                quiet = true;
                                break;

                            case 'd':
                                description = true;
                                break;

                            case 'r':
                                statusReport = true;
                                break;

                            case 'R':
                                statusReportHTML = true;
                                break;

                            case 'D':
                                diagnostics = true;
                                break;

                            case 'f':
                                loadDescriptionsFromFile = true;
                                break;

                            case 'c':
                                showConsole = true;
                                break;

                            default:
                                errorString = "unknown option " + args[i].charAt(1);
                        }
                    } else {
                        if (i == args.length - 1) {
                            //last element
                            fileName = args[i];
                        } else {
                            errorString = "unknown option " + args[i];
                        }
                    }
                    i++;
                } catch (Exception e) {
                    errorString = "illegal format for options ";
                    exitCode = ExitCodes.EXIT_ERROR_CODE_BAD_ARGS;
                    SFSystem.sfLog().error("Error " + e, e);

                }
            }

            if (loadDescriptionsFromFile) {
                filesList = loadListOfFiles(fileName);
            }

            if ((errorString != null) || fileName == null) {
                if ((fileName == null) && (!help)) {
                    errorString = "no file to parse";
                }
                errorString += usage;
            }
        } catch (Throwable t) {
            SFSystem.sfLog().error(t, t);
            exitCode = ExitCodes.EXIT_ERROR_CODE_BAD_ARGS;
        }
    }

    /**
     * Loads list from file.
     *
     * @param url String url
     * @return Vector of parsed lines
     */
    private synchronized Vector<String> loadListOfFiles(String url) {
        String thisLine;
        Vector<String> list = new Vector<String>();
        LineNumberReader file = null;
        try {
            //Do not allow other threads to read from the input
            //or write to the output while this is taking place
            file = new LineNumberReader(
                    new FileReader(url));
            //Loop through each line and add non-blank
            //lines to the Vector
            while ((thisLine = file.readLine()) != null) {
                String line = thisLine.trim();
                if (line.length() > 0 && line.charAt(0) != '#') {
                    list.add(line);
                }
            }
        } catch (IOException ex) {
            errorString = ex.getMessage();
            SFSystem.sfLog().error(errorString, ex);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    SFSystem.sfLog().error(e, e);
                }
            }
        }
        return list;
    }


    /**
     * Return string representation of context.
     *
     * @return string representation
     */
    public String toString() {
        StringBuilder strb = new StringBuilder();
        strb.append("SFParse options:");
        strb.append("\n - Verbose:       ").append(verbose);
        strb.append("\n - Quiet:         ").append(quiet);
        strb.append("\n - Description:   ").append(description);
        strb.append("\n - File:          ").append(fileName);
        strb.append("\n   * load from file:").append(loadDescriptionsFromFile);
        strb.append("\n   * filesList:   ");
        for (String file : filesList) {
            strb.append("\n     ").append(file);
        }
        strb.append("\n - Help:          ").append(help);
        strb.append("\n - Status report: ").append(statusReport);
        return strb.toString();
    }
}
