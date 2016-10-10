# This scripting toolkit is injected into the 
# scripting environment.
# 
# It contains convenience utilities and classes.

# Represents a template for a local event
def LocalEvent(schemaDictOrJSONorTitle = None):
    return schemaDictOrJSONorTitle;

# Represents a template for a local action
def RemoteAction(schemaDictOrJSONorTitle = None):
    return schemaDictOrJSONorTitle;

# Represents a template for a parameter
def Parameter(schemaDictOrJSONorTitle = None):
    return schemaDictOrJSONorTitle;

# _toolkit: Native toolkit (injected)

# A general console with:
# .log(...)    - light verbose text
# .info(...)   - blue text
# .error(...)  - red text
# .warn(...)   - orange text
console = _toolkit.getConsole()


# Simple JSON encoder
def json_encode(obj):
  return _toolkit.jsonEncode(obj)

# Simple JSON decoder
def json_decode(json):
  return _toolkit.jsonDecode(json)

# Tests whether two objects are effectively the same value (safely deeply inspects both objects)
# (collections, arrays, maps, dicts sets, etc. are all normalised and made "comparable" where possible)
def same_value(obj1, obj2):
  return _toolkit.sameValue(obj1, obj2)

# Schedules a function to be called immediately or delayed
def call(func, delay=0, complete=None, error=None):
  _toolkit.call(False, func, long(delay*1000), complete, error)

# DEPRECATED (use 'call' and optional args)
def call_delayed(delay, func, complete=None, error=None):
  _toolkit.call(False, func, long(delay*1000), complete, error)

# Schedules a function to be called in a thread-safe manner
def call_safe(func, delay=0, complete=None, error=None):
  _toolkit.call(True, func, long(delay*1000), complete, error)

# Returns an atomically incrementing long integer.  
def next_seq():
    return _toolkit.nextSequenceNumber()

# Returns a high-precision atomically incrementing clock in milliseconds
def system_clock():
    return _toolkit.systemClockInMillis();

# Note: for DateTime functions:
#
#   now = date_now()
#   now2 = date_at(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth(), now.getHourOfDay(), now.getMinuteOfHour(), now.getSecondOfMinute(), now.getMillisOfSecond())
#
#   now == now2 (is True)
#
# (for instant.toString(pattern), see http://www.joda.org/joda-time/apidocs/org/joda/time/format/DateTimeFormat.html)
 
# 'now' timestamp (based on excellent JODATIME library)
def date_now():
    return _toolkit.dateNow()

# a timestamp at another time (based on excellent JODATIME library)
def date_at(year, month, day, hour, minute, second=0, millisecond=0):
    return _toolkit.dateAt(year, month, day, hour, minute, second, millisecond)
   
# a timestamp based on a millisecond offset (JODATIME library)
def date_instant(millis):
    return _toolkit.dateAtInstant(millis)

# parses a date string e.g. '2016-06-13T08:17:11.836-04:00'
def date_parse(s):
    return _toolkit.parseDate(s)

# Simple URL retriever (supports POST)
def get_url(url, query=None, headers=None, reference=None, contentType=None, post=None, connectTimeout=10, readTimeout=15):
  return _toolkit.getURL(url, query, headers, reference, contentType, post, long(connectTimeout*1000), long(readTimeout*1000))

# DEPRECATED (same as above)
def getURL(url, query=None, headers=None, reference=None, contentType=None, post=None, connectTimeout=10, readTimeout=15):
  return _toolkit.getURL(url, query, headers, reference, contentType, post, long(connectTimeout*1000), long(readTimeout*1000))

# A managed TCP connection that attempts to stay open (includes instrumentation)
def TCP(dest=None, connected=None, received=None, sent=None, disconnected=None, timeout=None, sendDelimiters='\n', receiveDelimiters='\r\n', binaryStartStopFlags=None):
  return _toolkit.createTCP(dest, connected, received, sent, disconnected, timeout, sendDelimiters, receiveDelimiters, binaryStartStopFlags);

# A managed UDP connection for sending or receiving UDP (includes instrumentation)
def UDP(source='0.0.0.0:0', dest=None, ready=None, received=None, sent=None, intf=None):
  return _toolkit.createUDP(source, dest, ready, received, sent, intf);
  
# A managed processes that attempts to stay executed (includes instrumentation)
def Process(command, # the command line and arguments
           # callbacks
           started=None, # everytime the process is started
           stdout=None, # stdout handler
           stdin=None, # feedback when .send is called (for convenience) 
           stderr=None, # stderr handler
           stopped=None, # when the process is stops / stopped
           timeout=None,  # timeout when a request is issued but not response
           # arguments
           sendDelimiters='\n', receiveDelimiters='\r\n', # default delimiters
           working=None, # working directory
           mergeErr=False):
  return _toolkit.createProcess(command, 
                                started, stdout, stdin, stderr, stopped, timeout, sendDelimiters, receiveDelimiters,
                                working, mergeErr)

# Creates a short-living process (still managed)
def quick_process(command,
                  stdinPush=None, # text to push to stdin
                  started=None,   # a callback where arg is OS process ID
                  finished=None,  # callback with argument with these properties:
                                  #   'exit': The exit code (or null of timed out)
                                  #   'error': Any launch errors e.g. program not found
                                  #   'stdout': The complete stdout capture
                                  #   'stderr': The complete stderr capture (if not merged)
                  timeoutInSeconds=0, # if positive, kills the process on timeout
                  working=None,   # the working directory
                  mergeErr=False):# merge  stderr into the stdout for convenience
    return _toolkit.createQuickProcess(command, stdinPush, 
                                       started, finished, 
                                       long(timeoutInSeconds * 1000), working, mergeErr)

# create a safe request queue for mixing asynchronous and synchronous programming.
# e.g. 
# queue = request_queue()
#
# def udp_received(source, data):
#     queue.handle((source, data))
#
# queue.request(lambda: udp.send('?'), lambda arg: console.info('RECV UDP %s' % arg)) 
def request_queue(received=None, sent=None, timeout=None):
    return _toolkit.createRequestQueue(received, sent, timeout)

# A general purpose timer class for repeating timers
class Timer:
  def __init__(self, func, intervalInSeconds, firstDelayInSeconds=0, stopped=False):
      self.wrapper = _toolkit.createTimer(func, long(firstDelayInSeconds * 1000), long(intervalInSeconds * 1000), stopped)
      
  def setDelayAndInterval(self, delayInSeconds, intervalInSeconds):
      self.wrapper.setDelayAndInterval(long(delayInSeconds * 1000), long(intervalInSeconds * 1000))
      
  def setInterval(self, seconds):
      self.wrapper.setInterval(long(seconds * 1000))
      
  def setDelay(self, seconds):
      self.wrapper.setDelay(long(seconds * 1000))
      
  def reset(self):
      self.wrapper.reset()
      
  def start(self):
      self.wrapper.start()
      
  def stop(self):
      self.wrapper.stop()      
      
  def getDelay(self):
      return self.wrapper.getDelay() / 1000.0
  
  def getInterval(self):
      return self.wrapper.getInterval() / 1000.0
  
  def isStarted(self):
      return self.wrapper.isStarted()
  
  def isStopped(self):
      return self.wrapper.isStopped()

# Create a node (on-the-fly)
def Node(nodeName):
    return _toolkit.createNode(nodeName)

# Creates a node based on the name of an existing node (on-the-fly)
def Subnode(baseName):
    return _toolkit.createSubnode(baseName)
  
# Releases a node created with Node() or Subnode() and related resources.
def release_node(node):
  return _toolkit.releaseNode(node)

# DEPRECATED (see above)  
def releaseNode(node):
  return _toolkit.releaseNode(node)

# Creates a local signal (on-the-fly)
def Signal(name, metadata=None):
    return _toolkit.createEvent(name, metadata)

# (see Signal)
def Event(name, metadata=None):
    return _toolkit.createEvent(name, metadata)
  
# Creates a local action (on-the-fly)    
def Action(name, handler, metadata=None):
	return _toolkit.createAction(name, handler, metadata)
	
# Creates remote action
def create_remote_action(name, metadata=None, suggestedNode=None, suggestedAction=None):
    return _toolkit.createRemoteAction(name, metadata, suggestedNode, suggestedAction)
    
# Creates a remote event
def create_remote_event(name, handler, metadata=None, suggestedNode=None, suggestedEvent=None):
    return _toolkit.createRemoteEvent(name, handler, metadata, suggestedNode, suggestedEvent)

# Looks up a local action by simple name
def lookup_local_action(name):
    return _toolkit.getLocalAction(name)

# Looks up a local event by simple name
def lookup_local_event(name):
    return _toolkit.getLocalEvent(name)
    
# Looks up a remote action by simple name
def lookup_remote_action(name):
    return _toolkit.getRemoteAction(name)

# Looks up a remote event by simple name
def lookup_remote_event(name):
    return _toolkit.getRemoteEvent(name)
