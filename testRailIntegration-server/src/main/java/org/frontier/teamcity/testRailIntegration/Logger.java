package org.frontier.teamcity.testRailIntegration;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

class Logger {
    private final com.intellij.openapi.diagnostic.Logger logger;
    private boolean isTracing = false;
    private static ServerPaths paths;

    private final Path filePath;
    private final File file;

    private final static SimpleDateFormat datefmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    private static final int RECENT_CAPACITY = 200;
    private final Queue<String> recentMessages = new ArrayBlockingQueue<>(RECENT_CAPACITY);

    Logger(String name, ServerPaths sPaths) throws IOException{

        filePath = Paths.get(sPaths.getSystemDir(), "pluginData", "TRIntegration", "TRIntegration.log");
        this.logger = com.intellij.openapi.diagnostic.Logger.getInstance(name);
        this.file =filePath.toFile();

        Loggers.SERVER.info(String.format("Initialising logger at %s",filePath.toString()));
    }

    <T> Logger(Class<T> _class, ServerPaths sPaths)throws IOException{
        this(_class.getName(), sPaths);
    }

    void error(String format, Object ...args){
        String message = String.format(format,args);
        String tsMessage = String.format("[%s] [ERROR]", getTimestamp()) + message;
        logger.error(message);

        try {
            FileUtils.writeStringToFile(file,tsMessage);
        }catch(IOException ex){
            Loggers.SERVER.warn("TRIntegration failed to log message:" + tsMessage);
        }
        addToRecent(tsMessage); //the logfile's not working so keep our own ringbuffer of recent log messages
    }
    void warn(String format, Object ...args){
        String message = String.format(format,args);
        String tsMessage = String.format("[%s] [WARN]", getTimestamp()) + message;
        logger.warn(message);
        try {
            FileUtils.writeStringToFile(file,tsMessage);
        }catch(IOException ex){
            Loggers.SERVER.warn("TRIntegration failed to log message:" + tsMessage);
        }
        addToRecent(tsMessage); //the logfile's not working so keep our own ringbuffer of recent log messages
    }
    void info(String format, Object ...args){
        String message = String.format(format,args);
        String tsMessage = String.format("[%s] [INFO]", getTimestamp()) + message;
        logger.info(message);
        try {
            FileUtils.writeStringToFile(file,tsMessage);
        }catch(IOException ex){
            Loggers.SERVER.warn("TRIntegration failed to log message:" + tsMessage);
        }
        addToRecent(tsMessage); //the logfile's not working so keep our own ringbuffer of recent log messages
    }
    void debug(String format, Object ...args){
        if(logger.isDebugEnabled()){
            String message = String.format(format,args);
            String tsMessage = String.format("[%s] [INFO]", getTimestamp()) + message;
            logger.debug(message);
            try {
                FileUtils.writeStringToFile(file,tsMessage);
            }catch(IOException ex){}
            addToRecent(tsMessage); //the logfile's not working so keep our own ringbuffer of recent log messages
        }
    }
    void trace(String format, Object ...args){
        if(isTracing && logger.isDebugEnabled()){
            logger.debug("TRACE: " + String.format(format, args));
        }
    }
    void setTrace(boolean trace){
        this.isTracing = trace;
    }
    void startTrace(){this.setTrace(true);}
    void endTrace(){this.setTrace(false);}

    void setServerPaths(ServerPaths sPaths){
            paths = sPaths;
    }

    private String getTimestamp(){
        return datefmt.format(new Date());
    }

    private void addToRecent(String message){
        if(!recentMessages.offer(message)){
            recentMessages.poll(); //remove the last one
            recentMessages.offer(message); //if we've got issues here there are Bigger Problems
        }
    }

    public List<String> getRecentMessages(){
        return new ArrayList<>(recentMessages);
    }
}
