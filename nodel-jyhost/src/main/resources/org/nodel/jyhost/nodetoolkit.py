from sys import nodetoolkit

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

# nodetoolkit: Native toolkit (injected)
_toolkit = nodetoolkit

# A general console with:
# .log(...)    - light verbose text
# .info(...)   - blue text
# .error(...)  - red text
# .warn(...)   - orange text
console = nodetoolkit.getConsole()


# Simple JSON encoder
def json_encode(obj):
  return nodetoolkit.jsonEncode(obj)

# Simple JSON decoder
def json_decode(json):
  return nodetoolkit.jsonDecode(json)

# Tests whether two objects are effectively the same value (safely deeply inspects both objects)
# (collections, arrays, maps, dicts sets, etc. are all normalised and made "comparable" where possible)
def same_value(obj1, obj2):
  return nodetoolkit.sameValue(obj1, obj2)

# Schedules a function to be called immediately or delayed
def call(func, delay=0, complete=None, error=None):
  nodetoolkit.call(False, func, long(delay*1000), complete, error)

# DEPRECATED (use 'call' and optional args)
def call_delayed(delay, func, complete=None, error=None):
  nodetoolkit.call(False, func, long(delay*1000), complete, error)

# Schedules a function to be called in a thread-safe manner
def call_safe(func, delay=0, complete=None, error=None):
  nodetoolkit.call(True, func, long(delay*1000), complete, error)

# Returns an atomically incrementing long integer.  
def next_seq():
    return nodetoolkit.nextSequenceNumber()

# Returns a high-precision atomically incrementing clock in milliseconds
def system_clock():
    return nodetoolkit.systemClockInMillis();

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
    return nodetoolkit.dateNow()

# a timestamp at another time (based on excellent JODATIME library)
def date_at(year, month, day, hour, minute, second=0, millisecond=0):
    return nodetoolkit.dateAt(year, month, day, hour, minute, second, millisecond)
   
# a timestamp based on a millisecond offset (JODATIME library)
def date_instant(millis):
    return nodetoolkit.dateAtInstant(millis)

# parses a date string e.g. '2016-06-13T08:17:11.836-04:00'
def date_parse(s):
    return nodetoolkit.parseDate(s)

# Simple URL retriever (supports POST) where 'query' and 'headers' are dictionaries. 
# If 'fullResponse', result is an object which includes 'statusCode', 'reason', 'content' and attributes made up of the response HTTP headers
def get_url(url, method=None, query=None, username=None, password=None, headers=None, contentType=None, post=None, connectTimeout=10, readTimeout=15, fullResponse=False):
  if fullResponse:
    return nodetoolkit.getHttpClient().makeRequest(url, method, query, username, password, headers, contentType, post, long(connectTimeout*1000), long(readTimeout*1000))
  else:
    return nodetoolkit.getHttpClient().makeSimpleRequest(url, method, query, username, password, headers, contentType, post, long(connectTimeout*1000), long(readTimeout*1000))

# DEPRECATED (same as above)
def getURL(url, method=None, query=None, username=None, password=None, headers=None, contentType=None, post=None, connectTimeout=10, readTimeout=15):
  return nodetoolkit.getHttpClient().makeSimpleRequest(url, method, query, username, password, headers, contentType, post, long(connectTimeout*1000), long(readTimeout*1000))

# A managed TCP connection that attempts to stay open (includes instrumentation)
def TCP(dest=None, connected=None, received=None, sent=None, disconnected=None, timeout=None, sendDelimiters='\n', receiveDelimiters='\r\n', binaryStartStopFlags=None):
  return nodetoolkit.createTCP(dest, connected, received, sent, disconnected, timeout, sendDelimiters, receiveDelimiters, binaryStartStopFlags)

# A managed UDP connection for sending or receiving UDP (includes instrumentation)
def UDP(source='0.0.0.0:0', dest=None, ready=None, received=None, sent=None, intf=None):
  return nodetoolkit.createUDP(source, dest, ready, received, sent, intf)

# A managed SSH connection ('shell' mode) for executing commands (includes instrumentation)
# (see https://github.com/museumsvictoria/nodel/wiki/Scripting-Toolkit:-SSH-usage)
def SSH(dest=None, connected=None, received=None, sent=None, disconnected=None, timeout=None, sendDelimiters='\n', receiveDelimiters='\r\n',
        username=None, password=None, echoDisabled=False):
  return nodetoolkit.createSSH(dest, connected, received, sent, disconnected, timeout, sendDelimiters, receiveDelimiters, username, password, echoDisabled)
  
# A managed processes that attempts to stay executed (includes instrumentation)
def Process(command, # the command line and arguments
           # callbacks
           started=None,   # everytime the process is started
           stdout=None,    # stdout handler
           stdin=None,     # feedback when .send is called (for convenience) 
           stderr=None,    # stderr handler
           stopped=None,   # when the process is stops / stopped
           timeout=None,   # timeout when a request is issued but not response
           # arguments
           sendDelimiters='\n', receiveDelimiters='\r\n', # default delimiters
           working=None,   # working directory
           mergeErr=False, # merge  stderr into the stdout for convenience
           env=None):      # add/set environment variables (dict)
  return nodetoolkit.createProcess(command, 
                                started, stdout, stdin, stderr, stopped, timeout, sendDelimiters, receiveDelimiters,
                                working, mergeErr, env)

# Creates a short-living process (still managed)
def quick_process(command,
                  stdinPush=None, # text to push to stdin
                  started=None,   # a callback where arg is OS process ID
                  finished=None,  # single callback argument with these properties:
                                  #   'code': The exit code (or null in timed out)
                                  #   'stdout': The complete stdout capture
                                  #   'stderr': The complete stderr capture (if not merged)
                  timeoutInSeconds=0, # if positive, kills the process on timeout
                  working=None,   # the working directory
                  mergeErr=False, # merge  stderr into the stdout for convenience
                  env=None):      # add/set environment variables (dict)
    return nodetoolkit.createQuickProcess(command, stdinPush, 
                                       started, finished, 
                                       long(timeoutInSeconds * 1000), working, mergeErr, env)

# create a safe request queue for mixing asynchronous and synchronous programming.
# e.g. 
# queue = request_queue()
#
# def udp_received(source, data):
#     queue.handle((source, data))
#
# queue.request(lambda: udp.send('?'), lambda arg: console.info('RECV UDP %s' % arg)) 
def request_queue(received=None, sent=None, timeout=None):
    return nodetoolkit.createRequestQueue(received, sent, timeout)

# A general purpose timer class for repeating timers
class Timer:
  def __init__(self, func, intervalInSeconds, firstDelayInSeconds=0, stopped=False):
      self.wrapper = nodetoolkit.createTimer(func, long(firstDelayInSeconds * 1000), long(intervalInSeconds * 1000), stopped)
      
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
    return nodetoolkit.createNode(nodeName)

# Creates a node based on the name of an existing node (on-the-fly)
def Subnode(baseName):
    return nodetoolkit.createSubnode(baseName)
  
# Releases a node created with Node() or Subnode() and related resources.
def release_node(node):
  return nodetoolkit.releaseNode(node)

# DEPRECATED (see above)  
def releaseNode(node):
  return nodetoolkit.releaseNode(node)

# Creates a local signal (on-the-fly)
# RESERVED FOR FUTURE DIFFERENTIATION FROM EVENT
def Signal(name, metadata=None):
    return nodetoolkit.createEvent(name, metadata)

# (see Signal)
# DEPRECATED - use 'create_local_event' or '@local_event'
def Event(name, metadata=None):
    return nodetoolkit.createEvent(name, metadata)

# create a local event
def create_local_event(name, metadata=None):
  return nodetoolkit.createEvent(name, metadata)
  
# Creates a local action (on-the-fly)    
# DEPRECATED - use 'create_local_action' or '@local_action'
def Action(name, handler, metadata=None):
  return nodetoolkit.createAction(name, handler, metadata)

# creates a local action
def create_local_action(name, handler, metadata=None):
  return nodetoolkit.createAction(name, handler, metadata)
  
# Creates remote action
def create_remote_action(name, metadata=None, suggestedNode=None, suggestedAction=None):
    return nodetoolkit.createRemoteAction(name, metadata, suggestedNode, suggestedAction)
    
# Creates a remote event
def create_remote_event(name, handler, metadata=None, suggestedNode=None, suggestedEvent=None):
    return nodetoolkit.createRemoteEvent(name, handler, metadata, suggestedNode, suggestedEvent)

# Function decorators for convenience
# (Tag functions with '@___')
def local_action(metadata):
  def wrap(handler):
    if handler.func_code.co_argcount == 0:
      return nodetoolkit.createAction(handler.func_name, lambda arg: handler(), metadata)
    else:
      return nodetoolkit.createAction(handler.func_name, handler, metadata)

  return wrap

def remote_event(metadata, suggestedNode=None, suggestedEvent=None):
  def wrap(handler):
    if handler.func_code.co_argcount == 0:
      return nodetoolkit.createRemoteEvent(handler.func_name, lambda arg: handler(), metadata, suggestedNode, suggestedEvent)
    else:
      return nodetoolkit.createRemoteEvent(handler.func_name, handler, metadata, suggestedNode, suggestedEvent)

  return wrap


# <!-- Look up functions

# Looks up a local action by simple name
def lookup_local_action(name):
    return nodetoolkit.getLocalAction(name)

# Looks up a local event by simple name
def lookup_local_event(name):
    return nodetoolkit.getLocalEvent(name)
    
# Looks up a remote action by simple name
def lookup_remote_action(name):
    return nodetoolkit.getRemoteAction(name)

# Looks up a remote event by simple name
def lookup_remote_event(name):
    return nodetoolkit.getRemoteEvent(name)

# Looks up a parameter by simple name
def lookup_parameter(name):
    return nodetoolkit.lookupParameter(name)

    
# NODE LIFE-CYCLE FUNCTIONS:

# for functions to be called before main has executed
_nodel_beforeMainFunctions = []

def processBeforeMainFunctions():
    for f in _nodel_beforeMainFunctions:
        f()
        
    return len(_nodel_beforeMainFunctions)

# decorates functions that should be called after 'main' completes
def before_main(f):
    _nodel_beforeMainFunctions.append(f)

    return f

  
# for functions to be called after main has executed
_nodel_afterMainFunctions = []

def processAfterMainFunctions():
    for f in _nodel_afterMainFunctions:
        f()
        
    return len(_nodel_afterMainFunctions)

# decorates functions that should be called after 'main' completes
def after_main(f):
    _nodel_afterMainFunctions.append(f)

    return f

# for functions to be called at cleanup (shutdown, restart, etc.)
_nodel_atCleanupFunctions = []

def processCleanupFunctions():
    for f in _nodel_atCleanupFunctions:
      try:
        f()
      except:
        # ignore
        pass
        
    return len(_nodel_atCleanupFunctions)

# decorates functions that should be called at cleanup (shutdown, restart, etc.)
def at_cleanup(f):
    _nodel_atCleanupFunctions.append(f)

    return f

# CONVENIENCE FUNCTIONS

# a convenient constant that can be used against most objects (arrays, dicts, sets, strings, etc.)
from org.nodel.jyhost.PyToolkit import EmptyDict as EMPTY

# Returns true if a string is blank (null, empty or all simple white-space incl. tab, CR, LN)
from org.nodel.Strings import isBlank as is_blank

# Returns false if there is at least one item within 'o', true otherwise
def is_empty(obj):
  return obj == None or len(obj) == 0
