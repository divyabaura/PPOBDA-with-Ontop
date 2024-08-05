package ppmappingcompiler;

import ppmappingcompiler.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static ppmappingcompiler.util.Utils.appendSlashIfMissing;
import static ppmappingcompiler.util.Utils.getTimestamp;

public class Logger {

    // log levels
    public static final int ERROR = 3;
    public static final int WARN = 2;
    public static final int INFO = 1;
    public static final int DEBUG = 0;

    // log colors
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";

    private static String logFilePath = null;
    private static boolean emptyLog = true;
    private static int logLevel = DEBUG;

    /*
     * in outputPath directory will eventually be created a new subdirectory "logs" where all log files will be put in
     */
    public static void setLogPath(String outputPath) {
        // if outputPath exists, create "logs" directory inside it
        File outDirectory = new File(outputPath);
        if (outDirectory.exists() && outDirectory.isDirectory()) {
            String logDirPath = appendSlashIfMissing(outputPath + "logs");
            logFilePath = logDirPath + getTimestamp(false) + ".txt";

            File logDirectory = new File(logDirPath);
            try {
                if (!(logDirectory.exists() && logDirectory.isDirectory())) {
                    boolean dirCreated = logDirectory.mkdir();
                    if (!dirCreated) throw new IOException();
                }

                boolean fileCreated = new File(logFilePath).createNewFile();
                if (!fileCreated) throw new IOException();
            } catch (IOException e) {
                error("Cannot create log file (" + logFilePath + ").");
                logFilePath = null;
            }
        } else {
            error("Directory" + outputPath + "not found.");
        }

        if (logFilePath == null) {
            error("Logs cannot be stored in external file.");
        }
    }

    public static void setLogLevel(int logLevel) {
        Logger.logLevel = logLevel;
    }

    public static void info(Object s) {
        info(s, 0);
    }

    public static void info(Object s, int indentNum) {
        log(s, indentNum, "INFO", ANSI_BLUE, INFO);
    }

    public static void debug(Object s) {
        debug(s, 0);
    }

    public static void debug(Object s, int indentNum) {
        log(s, indentNum, "DEBUG", ANSI_GREEN, DEBUG);
    }

    public static void warn(Object s) {
        warn(s, 0);
    }

    public static void warn(Object s, int indentNum) {
        log(s, indentNum, "WARN", ANSI_YELLOW, WARN);
    }

    public static void error(Object s) {
        error(s, 0);
    }

    public static void error(Object s, int indentNum) {
        log(s, indentNum, "ERROR", ANSI_RED, ERROR);
    }

    private static void log(Object s, int indentNum, String logType, String color, int logLevel) {
        if (logLevel < Logger.logLevel) return;

        StringBuilder line = new StringBuilder();
        if (indentNum > 0) {
            line.append("\t".repeat(indentNum));
        } else {
            line.append(String.format(
                    "[*] %s [%s] ",
                    getTimestamp(true),
                    logType
            ));
        }
        line.append(s);

        // log to console
        if (color != null) System.out.println(addColor(line.toString(), color));
        else System.out.println(line);

        // log to file
        if (logFilePath != null) {
            try {
                if (emptyLog) {
                    IOUtils.writeFile(logFilePath, line.toString());
                    emptyLog = false;
                } else {
                    IOUtils.writeLine(logFilePath, line.toString());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void drawLine() {
        if (Logger.logLevel < WARN) {
            System.out.println(String.join("", Collections.nCopies(100, "-")) + "\n");
        }
    }

    private static String addColor(String s, String color) {
        return color + s + ANSI_RESET;
    }

}
