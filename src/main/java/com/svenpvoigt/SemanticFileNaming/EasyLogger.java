/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.svenpvoigt.SemanticFileNaming;

/**
 *
 * @author sven
 */
public class EasyLogger {
    public int filterLevel;
    
    public EasyLogger() {
        this.filterLevel = -1;
    }
    
    public void log(String msg) {
        System.out.println(msg);
    }
    
    public void log(String msg, int filterVal) {
        if (this.filterLevel>=filterVal) System.out.println(msg);
    }
    
    public void debug(String msg) {
        this.log("DEBUG: "+msg,3);
    }
    
    public void info(String msg) {
        this.log("INFO: "+msg,2);
    }
    
    public void warn(String msg) {
        this.log("WARNING: "+msg,1);
    }
    
    public void status(String msg) {
        this.log("STATUS: "+msg,0);
    }
}
