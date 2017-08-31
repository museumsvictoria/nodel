package org.nodel.nodelhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.nodel.jyhost.Launch;

/**
 * For NT / Unix daemons.
 * 
 * NT would normally use a service wrapper that calls the 'start' and 'stop' static methods.
 * 
 * Unix would use the Daemon interface and the 'start' and 'stop' instance methods.
 * 
 * Disappointingly, a non-daemon thread has to be used up.
 */
public class Service implements Daemon {
    
    /**
     * (static singleton)
     */
    private static Service s_service = new Service();
    
    /**
     * Shutdown flag
     */
    private boolean _shouldShutdown;
    
    /**
     * A non-daemon thread.
     */
    private Thread _thread;
    
    /**
     * Reference to the main nodel host.
     */
    private Launch _nodelLaunch;
    
    /**
     * The Daemon context as provided during 'init'
     */
    private DaemonContext _context;
    
    /**
     * The process args captured during 'init'.
     */
    private String[] _processArgs;
    
    @Override
    public void destroy() {
        // only need to rely on non-Daemon threads.
    } // (method)

    /**
     * On initialisation (or pre-start), captures the process args.
     */
    @Override
    public void init(DaemonContext context) throws DaemonInitException, Exception {
        _context = context;
        _processArgs = context.getArguments();
    } // (method)

    @Override
    public void start() throws Exception {
        System.out.println("Nodel [Jython] (v" + Launch.VERSION + ")...");
        
        _nodelLaunch = _processArgs == null ? new Launch() : new Launch(_processArgs);
        
        // start a non-daemon thread
        _thread = new Thread(new Runnable() {
            
            @Override
            public void run() {
                threadMain();
            }
            
        });
        _thread.setPriority(Thread.MIN_PRIORITY);
        _thread.start();
    } // (method)
    
    /**
     * (thread entry-point)
     */
    private void threadMain() {
        while (!_shouldShutdown) {
            try {
                Thread.sleep(300000);
            } catch (InterruptedException e) {
            }
        } // (while)
    } // (method)
    
    /**
     * Stops the service
     */
    @Override
    public void stop() throws Exception {
        System.out.println("Stopping Nodel...");
        
        _shouldShutdown = true;
        
        if (_thread != null)
            _thread.interrupt();
        
        // this is reserved for future use and avoids warnings
        // without manually using 'suppress'
        _nodelLaunch.shutdown();
        
        if (_context != null)
            _context.toString();
        
    } // (method)
    
    /**
     * Static start method normally used by Windows service wrapper.
     */
    public static void start(String[] args) throws Exception {
        s_service._processArgs = args;
        s_service.start();
    }  
    
    /**
     * Static start method normally used by Windows service wrapper.
     */
    public static void stop(String[] args) throws Exception {
        s_service.stop();
    }     

} // (class)
