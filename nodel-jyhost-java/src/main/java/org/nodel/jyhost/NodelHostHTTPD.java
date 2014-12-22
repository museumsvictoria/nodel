package org.nodel.jyhost;

/* 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. 
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownServiceException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.nodel.SimpleName;
import org.nodel.Strings;
import org.nodel.core.Framework;
import org.nodel.core.NodelClients.NodeURL;
import org.nodel.discovery.AdvertisementInfo;
import org.nodel.discovery.AutoDNS;
import org.nodel.host.NanoHTTPD;
import org.nodel.logging.LogEntry;
import org.nodel.logging.Logging;
import org.nodel.reflection.Param;
import org.nodel.reflection.Serialisation;
import org.nodel.reflection.SerialisationException;
import org.nodel.reflection.Service;
import org.nodel.reflection.Value;
import org.nodel.rest.EndpointNotFoundException;
import org.nodel.rest.REST;

public class NodelHostHTTPD extends NanoHTTPD {

    /**
     * (logging related)
     */
    private static AtomicLong s_instance = new AtomicLong();

    /**
     * (logging related)
     */
    protected long _instance = s_instance.getAndIncrement();

    /**
     * (logging related)
     */
    protected Logger _logger = LogManager.getLogger(this.getClass().getName() + "_" + _instance);
    
    /**
     * The last user agent being used.
     */
    private String _userAgent;

    /**
     * Represents the model exposed to the REST services.
     */
    public class RESTModel {

        @Service(name = "nodes", order = 1, title = "Nodes", desc = "Node lookup by Node name.", genericClassA = SimpleName.class, genericClassB = PyNode.class)
        public Map<SimpleName, PyNode> nodes() {
            return _nodelHost.getNodeMap();
        }
        
        @Value(name = "started", title = "Started", desc = "When the host started.")
        public DateTime __started = DateTime.now();
        
        @Service(name = "allNodes", order = 5, title = "All nodes", desc = "Returns all the advertised nodes.")
        public Collection<AdvertisementInfo> getAllNodes() {
            return _nodelHost.getAdvertisedNodes();
        }
        
        @Service(name = "discovery", order = 6, title = "Discovery service", desc = "Multicast discovery services.")
        public AutoDNS discovery() {
            return AutoDNS.instance();
        }        
        
        @Service(name = "nodeURLs", order = 6, title = "Node URLs", desc = "Returns the addresses of all advertised nodes.")
        public List<NodeURL> nodeURLs(@Param(name = "filter", title = "Filter", desc = "Optional string filter.") String filter) throws IOException {
            return _nodelHost.getNodeURLs(filter);
            }
            
        @Service(name = "logs", title = "Logs", desc = "Detailed program logs.")
        public LogEntry[] getLogs(
                @Param(name = "from", title = "From", desc = "Start inclusion point.") long from, 
                @Param(name = "max", title = "Max", desc = "Results count limit.") int max) {
            List<LogEntry> result = Logging.instance().getLogs(from, max);
            
            return result.toArray(new LogEntry[result.size()]);
        } // (method)

        @Service(name = "warningLogs", title = "Warning logs", desc = "Same as 'logs' except filtered by warning-level.")
        public LogEntry[] getWarningLogs(
                @Param(name = "from", title = "From", desc = "Start inclusion point.") long from, 
                @Param(name = "max", title = "Max", desc = "Results count limit.") int max) {
            List<LogEntry> result = Logging.instance().getWarningLogs(from, max);

            return result.toArray(new LogEntry[result.size()]);
        } // (method)
        
        @Service(name = "framework", order = 6, title = "Framework", desc = "Instrumentation services related to the entire framework.")
        public Framework framework() {
            return Framework.shared();
        }           

    } // (inner class)

    private NodelHost _nodelHost;

    /**
     * Holds the object bound to the REST layer
     */
    private RESTModel _restModel = new RESTModel();

    public NodelHostHTTPD(int port, File directory) throws IOException {
        super(port, directory, false);
    }
    
    /**
     * Sets the host.
     */
    public void setNodeHost(NodelHost value) {
        _nodelHost = value;
    }    

    /**
     * (all headers are stored by lower-case)
     */
    @Override
    public Response serve(String uri, File root, String method, Properties params, Request request) {
        _logger.debug("Serving '" + uri + "'...");

        // if REST being used, the target object
        Object restTarget = _restModel;
        
        // get the user-agent
        String userAgent = request.header.getProperty("user-agent");
        if (userAgent != null)
            _userAgent = userAgent;
        
		// get the parts (avoiding blank first part if necessary)
        String[] parts = (uri.startsWith("/") ? uri.substring(1) : uri).split("/");
        
        // check if we're serving up from a node root
        // 'http://example/nodes/index.htm'
        if (parts.length >= 2 && parts[0].equalsIgnoreCase("nodes")) {
        	// the second part will be the node name
        	SimpleName nodeName = new SimpleName(parts[1]);
        	
        	PyNode node = _nodelHost.getNodeMap().get(nodeName);
        	
        	if (node == null)
        		return prepareNotFoundResponse(uri, "Node");
        	
        	// check if properly formed URI is being used i.e. ends with slash
        	if (parts.length == 2 && !uri.endsWith("/"))
        	    return prepareRedirectResponse(uri + "/");
        	
    		root = new File(node.getRoot(), "content");
    		restTarget = node;
    		
    		// rebuild the 'uri' and 'parts'
    		int OFFSET = 2;
    		
			StringBuilder sb = new StringBuilder();
			String[] newParts = new String[parts.length - OFFSET];

			for (int a = OFFSET; a < parts.length; a++) {
				String path = parts[a];

				sb.append('/');
				sb.append(path);

				newParts[a - OFFSET] = path;
			}

			if (sb.length() == 0)
				sb.append('/');

			uri = sb.toString();
			parts = newParts;
        }

        // check if REST is being used
		if (parts.length > 0 && parts[0].equals("REST")) {
			// drop 'REST' part
			int OFFSET = 1;
			String[] newParts = new String[parts.length - OFFSET];
			for (int a = OFFSET; a < parts.length; a++)
				newParts[a - OFFSET] = parts[a];
			
			parts = newParts;

			try {
				Object target;

				if (method.equalsIgnoreCase("GET"))
					target = REST.resolveRESTcall(restTarget, parts, params, null);

				else if (method.equalsIgnoreCase("POST"))
					target = REST.resolveRESTcall(restTarget, parts, params, request.raw);

				else
					throw new UnknownServiceException("Unexpected method - '" + method + "'");

				String targetAsJSON = Serialisation.serialise(target);

				// adjust the response headers for script compatibility

				Response resp = new Response(HTTP_OK, "application/json; charset=utf-8", targetAsJSON);
				resp.addHeader("Access-Control-Allow-Origin", "*");

				return resp;
				
			} catch (EndpointNotFoundException exc) {
				return prepareExceptionMessageResponse(HTTP_NOTFOUND, exc, false);

			} catch (FileNotFoundException exc) {
				return prepareExceptionMessageResponse(HTTP_NOTFOUND, exc, false);

			} catch (SerialisationException exc) {
				return prepareExceptionMessageResponse(HTTP_INTERNALERROR, exc, params.containsKey("trace"));

			} catch (UnknownServiceException exc) {
				return prepareExceptionMessageResponse(HTTP_INTERNALERROR, exc, false);

			} catch (Exception exc) {
				_logger.warn("Unexpected exception during REST operation.", exc);

				return prepareExceptionMessageResponse(HTTP_INTERNALERROR, exc, params.contains("trace"));
			}
		} else {
			// not a REST call, fall through to other handlers
			return super.serve(uri, root, method, params, request);
		}
    } // (method)
    
    /**
     * An exception message
     */
    public class ExceptionMessage {
    	
    	@Value(name = "code")
    	public String code;

        @Value(name = "error")
        public String error;
        
        @Value(name = "message")
        public String message;
        
        @Value(name = "cause")
        public ExceptionMessage cause;
        
        @Value(name = "stackTrace")
        public String stackTrace;        
        
    } // (class)
    
    /**
     * Prepares a neat exception tree for returning back to the HTTP client.
     */
    private Response prepareExceptionMessageResponse(String httpCode, Exception exc, boolean includeStackTrace) {
        assert exc != null : "Argument should not be null."; 
        
        ExceptionMessage message = new ExceptionMessage();
        
        Throwable currentExc = exc;
        ExceptionMessage currentMessage = message;
        
        while(currentExc != null) {
            currentMessage.error = currentExc.getClass().getSimpleName();
            currentMessage.message = currentExc.getMessage();
            if (Strings.isNullOrEmpty(currentMessage.message))
                currentMessage.message = currentExc.toString();
            
            if (includeStackTrace) {
                currentMessage.stackTrace = captureStackTrace(currentExc);
                
                // only capture it once
                includeStackTrace = false;
            }
            
            if (currentExc.getCause() == null)
                break;
            
            currentExc = currentExc.getCause();
            currentMessage.cause = new ExceptionMessage();
            
            currentMessage = currentMessage.cause;
        } // (while)
        
        Response resp = new Response(httpCode, "application/json", Serialisation.serialise(message));
        resp.addHeader("Access-Control-Allow-Origin", "*");
        
        return resp;
    } // (method)
    
    
    /**
     * Prepares a standard 404 Not Found HTTP response.
     * @type e.g. 'Node' or 'Type' (capitalise first letter)
     */
    private Response prepareNotFoundResponse(String path, String type) {
		ExceptionMessage errorResponse = new ExceptionMessage();
		
		errorResponse.error = "NotFound";
		// e.g. "Path '___' was not found." or
		// "Node '__' was not found."
		errorResponse.message = type + " '" + path + "' was not found.";
		errorResponse.code = "404";
		
		return new Response(HTTP_NOTFOUND, "application/json", Serialisation.serialise(errorResponse));
    }
    
    /**
     * Captures an exception's stack-trace.
     */
    private static String captureStackTrace(Throwable currentExc) {
    	StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        currentExc.printStackTrace(pw);
        
        pw.flush();
        return sw.toString();
    }
    
    /**
     * Returns the last user-agent in use. Could be null if not set yet.
     */
    public String getUserAgent() {
        return _userAgent;
    }
    
} // (class)
