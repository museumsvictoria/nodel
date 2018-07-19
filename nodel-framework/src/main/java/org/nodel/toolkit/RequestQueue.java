package org.nodel.toolkit;

import java.io.Closeable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.nodel.Handler;
import org.nodel.Handler.H0;
import org.nodel.Handler.H1;
import org.nodel.host.BaseNode;
import org.nodel.threading.CallbackQueue;
import org.nodel.threading.ThreadPool;
import org.nodel.threading.TimerTask;
import org.nodel.threading.Timers;
import org.nodel.Threads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestQueue implements Closeable {
    
    private static AtomicLong s_instanceCounter = new AtomicLong();
    
    /**
     * (used by 'logger' and thread name)
     */
    private long _instance = s_instanceCounter.getAndIncrement();
    
    /**
     * (logging related)
     */
    private Logger _logger = LoggerFactory.getLogger(String.format("%s.instance%d", this.getClass().getName(), _instance));
    
    /**
     * (synchronisation / locking)
     */
    private Object _lock = new Object();
    
    /**
     * The shared thread-pool
     */
    private ThreadPool _threadPool;
    
    /**
     * The safe queue as provided by a host
     */
    private CallbackQueue _callbackHandler;    
    
    /**
     * (see setter)
     */
    private H1<Object> _receivedCallback;

    /**
     * (see setter)
     */
    private H0 _sentCallback;

    /**
     * (see setter)
     */
    private H0 _timeoutCallback;

    /**
     * When errors occur during callbacks.
     */
    private H1<Exception> _callbackErrorHandler;

    /**
     * Permanently shut down?
     */
    private boolean _shutdown;
    
    /**
     * Shared timer framework to use.
     */
    private Timers _timers;
    
    /**
     * The start timer.
     */
    private TimerTask _timeoutTimer;
    
    /**
     * The default request timeout value (timed from respective 'send')
     */
    private int _requestTimeout = 10000;
    
    /**
     * The request timeout value (timed from respective 'queue')
     */
    private int _longTermRequestTimeout = 60000;
    
    /**
     * (Response for handling thread-state)
     */
    private H0 _threadStateHandler;

    /**
     * The request queue
     */
    private ConcurrentLinkedQueue<QueuedRequest> _requestQueue = new ConcurrentLinkedQueue<QueuedRequest>();
    
    /**
     * Holds the queue length ('ConcurrentLinkedQueue' not designed to handle 'size()' efficiently)
     */
    private int _queueLength = 0;
    
    /**
     * The active request
     */
    private QueuedRequest _activeRequest;

    /**
     * (constructor)
     */
    public RequestQueue(BaseNode node, H0 threadStateHandler, H1<Exception> callbackExceptionHandler, CallbackQueue callbackQueue, ThreadPool threadPool, Timers timers) {
        _threadStateHandler = threadStateHandler;
        _callbackErrorHandler = callbackExceptionHandler;
        _callbackHandler = callbackQueue;
        _threadPool = threadPool;
        _timers = timers;
    }
    
    /**
     * When a data segment arrives.
     */
    public void setReceivedHandler(H1<Object> handler) {
        _receivedCallback = handler;
    }
    
    /**
     * When a data segment is sent
     */
    public void setSentHandler(H0 handler) {
        _sentCallback = handler;
    }    
    
    /**
     * When a a connector a request timeout occurs.
     */
    public void setTimeoutHandler(H0 handler) {
        _timeoutCallback = handler;
    }
    
    /**
     * The request timeout (millis)
     */
    public int getRequestTimeout() {
        return _requestTimeout;
    }
    
    /**
     * The request timeout (millis)
     */
    public void setRequestTimeout(int value) {
        _requestTimeout = value;
    }
    
    /**
     * Returns the current queue length size.
     */
    public int getQueueLength() {
        synchronized (_lock) {
            return _queueLength;
        }
    }
    
    /**
     * When an actual data segment was received; deals with request callbacks if necessary
     */
    public void handle(Object data) {
        // deal with any queued callbacks first
        QueuedRequest request = null;
        
        // then check for any requests
        // (should release lock as soon as possible)
        synchronized (_lock) {
            if (_activeRequest != null) {
                request = _activeRequest;
                _activeRequest = null;
            }
        }

        if (request != null && request.timeout > 0) {
            // make sure it hasn't been too long i.e. timeout
            if (request.isExpired()) {
                _logger.debug("Active request has expired");
                
                // fire the timeout handler
                _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);
            } else {
                // fire the response request's response handler
                request.setResponse(data);
                
                _callbackHandler.handle(request.responseHandler, data, _callbackErrorHandler);
            }            
        }
        
        // ...then fire the 'received' callback next
        _callbackHandler.handle(_receivedCallback, data, _callbackErrorHandler);
        
        processQueue();
    }
    
    private class QueuedRequest {
        
        /**
         * The original request/send data (only need 'sent' callback)
         * (can be null)
         */
        public H0 requestHandler;
        
        /**
         * For long term expiry detection.
         * 
         * (based on 'nanoTime')
         */
        public long timeQueued = System.nanoTime();
        
        /**
         * (millis)
         */
        public int timeout;
        
        /**
         * (based on System.nanoTime)
         */
        public long timeStarted;
        
        /**
         * The (optional) callback 
         */
        public H1<Object> responseHandler;
        
        /**
         * Stores the response itself (for synchronous operation)
         */
        public Object response;
        
        public QueuedRequest(H0 requestHandler, int timeout, H1<Object> responseHandler) {
            this.requestHandler = requestHandler;
            this.timeout = timeout;
            this.responseHandler = responseHandler;
        }
        
        /**
         * Sets response value and fire any callbacks
         */
        public void setResponse(Object data) {
            synchronized (_lock) {
                if (_timeoutTimer != null)
                    _timeoutTimer.cancel();
            }
            
            synchronized (this) {
                this.response = data;

                // (for synchronous responses)
                this.notify();
            }
        }

        /**
         * Starts the timeout timer.
         * (assumes outer synced)
         */
        public void startTimeout() {
            this.timeStarted = System.nanoTime();
            
            if (_timeoutTimer == null) {
                _timeoutTimer = _timers.schedule(_threadPool,  new TimerTask() {

                    @Override
                    public void run() {
                        handleTimeout();
                    }
                    
                }, this.timeout);
            }
        }
        
        /**
         * (timer entry-point)
         */
        private void handleTimeout() {
            // force expiry
            synchronized (this) {
                this.timeStarted = 0;
            }

            processQueue();
        }
        
        /**
         * Checks whether request has expired.
         */
        public boolean isExpired() {
            long timeDiff = (System.nanoTime() - this.timeStarted) / 1000000L;
            return timeDiff > this.timeout;
        }
        
        /**
         * For detecting long term delayed requests. 
         */
        public boolean isLongTermExpired() {
            long timeDiff = (System.nanoTime() - this.timeQueued) / 1000000L;
            return timeDiff > _longTermRequestTimeout;
        }
    }
    
    /**
     * For complete control of a request
     */
    public void queueRequest(H0 requestHandler, int timeout, H1<Object> responseHandler) {
        QueuedRequest request = new QueuedRequest(requestHandler, timeout, responseHandler);
        
        doQueueRequest(request);
    }

    public void doQueueRequest(QueuedRequest request) {
        // whether or not this entry had to be queued
        boolean queued = false; 
        
        synchronized (_lock) {
            if (_activeRequest == null) {
                _logger.debug("Active request made.");
                
                // make it the active request and don't queue it
                _activeRequest = request;
                
                // will be sent next, so start timing
                _activeRequest.startTimeout();
                
            } else {
                _logger.debug("Queued a request.");
                
                // a request is active, so queue this new one
                _requestQueue.add(request);
                _queueLength++;
                queued = true;
            }
        }
        
        if (!queued && request.requestHandler != null) {
            sendBufferNow(request.requestHandler, true);
        }
        
        // without a timer, the queue needs to serviced on both send and receive
        processQueue();
    }
    
    /**
     * (Overloaded) (uses default timeout value)
     */
    public void request(H0 requestHandler, H1<Object> responseHandler) {
        // don't bother doing anything if empty or missing
        if (requestHandler == null)
            return;
        
        queueRequest(requestHandler, _requestTimeout, responseHandler);
    }

    /**
     * (synchronous version)
     */
    public Object requestWaitAndReceive(H0 requestHandler) {
        int recvTimeout = getRequestTimeout();

        final Object[] response = new Object[1];

        synchronized (response) {
            queueRequest(requestHandler, recvTimeout, new H1<Object>() {

                @Override
                public void handle(Object value) {
                    synchronized (response) {
                        response[0] = value;

                        response.notify();
                    }
                }

            });

            // wait for a while or until notified when a response is received
            Threads.waitOnSync(response, recvTimeout);
        }

        return response[0];
    }

    /**
     * Receives data and invokes a callback
     */
    public void receive(H1<Object> responseHandler) {
        queueRequest(null, _requestTimeout, responseHandler);
    }
    
    /**
     * Synchronous version.
     */
    public Object waitAndReceive() {
        int recvTimeout = getRequestTimeout();
        QueuedRequest request = new QueuedRequest(null, recvTimeout, null);
        
        synchronized(request) {
            queueRequest(null, _requestTimeout, null);
            
            // wait for a while or until notified when a response is received
            Threads.waitOnSync(request, recvTimeout);
        }
        
        return request.response;
    }
    
    /**
     * Clears the active request and any queue requests.
     */
    public void clearQueue() {
        synchronized (_lock) {
            boolean activeRequestCleared = false;
            
            // clear the active request
            if(_activeRequest != null){
                _activeRequest = null;
                activeRequestCleared = true;
            }

            int count = 0;

            // clear the queue
            while (_requestQueue.poll() != null) {
                count++;
                _queueLength--;
            }
            
            _logger.debug("Cleared queue. activeRequest={}, queueCount={}", activeRequestCleared, count);
        }
    }
    
    /**
     * Expires and initiates requests in the queue
     * (assumes not synced)
     */
    private void processQueue() {
        // if any new requests are found
        QueuedRequest nextRequest = null;
        
        // if a timeout callback needs to be fired
        boolean callTimeout = false;

        synchronized (_lock) {
            // check if any active requests need expiring
            if (_activeRequest != null) {
                if (_activeRequest.responseHandler == null || _activeRequest.timeout <= 0) {
                    // was a blind request or 
                    _activeRequest = null;
                    
                } else if (_activeRequest.isExpired()) {
                    _logger.debug("Active request has expired");
                    
                    // timeout callback must be fired
                    callTimeout = true;

                    // clear active request
                    _activeRequest = null;
                    
                } else if (_activeRequest.isLongTermExpired()) {
                    _logger.debug("Active request has long term expired");
                    
                    callTimeout = true;
                    
                    _activeRequest = null;
                } else {
                    // there is still an valid active request,
                    // so just get out of here
                    return;
                }
            }
        }

        // call the timeout callback, (there's an opportunity for request queue to be cleared by callback handler)
        if (callTimeout)
            _callbackHandler.handle(_timeoutCallback, _callbackErrorHandler);

        // an active request might have come in
        synchronized (_lock) {
            if (_activeRequest != null) {
                // there's an active request, so leave it to play out
                return;
            }
            // record this for logging
            int longTermDropped = 0;

            try {
                for (;;) {
                    // no active request, check for queued ones
                    nextRequest = _requestQueue.poll();

                    if (nextRequest == null) {
                        // no more requests either, so nothing more to do
                        _logger.debug("No new requests in queue.");

                        return;
                    }

                    _queueLength--;

                    if (!nextRequest.isLongTermExpired())
                        break;

                    // otherwise, continue to expire the long term queued
                    longTermDropped++;
                }
            } finally {
                if (longTermDropped > 0)
                    _logger.debug("(dropped {} long term queued requests.)", longTermDropped);
            }
            
            // set active request and start timeout *before* sending
            _activeRequest = nextRequest;
            nextRequest.startTimeout();
        }

        // if the request has send data 'data' send now
        if (nextRequest.requestHandler != null)
            sendBufferNow(nextRequest.requestHandler, false);
    }
    
    /**
     * Safely sends data without overlapping any existing requests 
     */
    public void send(H0 requestHandler) {
        QueuedRequest request = new QueuedRequest(requestHandler, 0, null);

        doQueueRequest(request);
    }
    
    /**
     * Sends data. Returns immediately. Will not throw any exceptions.
     */
    public void sendNow(H0 requestHandler) {
        if (requestHandler != null)
            sendBufferNow(requestHandler, true);
    }
    
    /**
     * Sends a prepared buffer immediately, optionally using a thread-pool
     */
    private void sendBufferNow(final H0 handler, boolean onThreadPool) {
        if (onThreadPool) {
            _threadPool.execute(new Runnable() {

                @Override
                public void run() {
                    _threadStateHandler.handle();
                    sendBufferNow0(handler);
                }

            });
        } else {
            sendBufferNow0(handler);
        }
    }

    /**
     * (convenience method)
     */
    private void sendBufferNow0(H0 handler) {
        _callbackHandler.handle(handler, _callbackErrorHandler);
        
        Handler.tryHandle(_sentCallback, _callbackErrorHandler);
    }
    
    /**
     * Permanently shuts down this managed TCP connection.
     */
    @Override
    public void close() {
        synchronized(_lock) {
            if (_shutdown)
                return;
            
            _shutdown = true;
            
            if (_timeoutTimer != null)
                _timeoutTimer.cancel();

            // notify the connection and receive thread if it happens to be waiting
            _lock.notify();
        }
    }
    
}
