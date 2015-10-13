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

# _toolkit: Native toolkit

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

# Schedules a function to be called immediately
def call(func, complete=None, error=None):
  _toolkit.callDelayed(0, func, complete, error)

# Schedules a function to be called at a slighter later time
def call_delayed(delayInSeconds, func, complete=None, error=None):
  _toolkit.callDelayed(delayInSeconds, func, complete, error)

# Returns an atomically incrementing long integer.  
def next_seq():
    return _toolkit.nextSequenceNumber()

# Returns a high-precision atomically incrementing clock in milliseconds
def system_clock():
    return _toolkit.systemClockInMillis();
 
# 'now' timestamp (based on excellent JODATIME library)
def date_now():
    return _toolkit.dateNow()
 
# a timestamp at another time (based on excellent JODATIME library)
def date_at(year, month, day, hour, minute, second=0, millisecond=0):
    return _toolkit.dateAt(year, month, day, hour, minute, second, millisecond)
   
# a timestamp based on a millisecond offset (JODATIME library)
def date_instant(millis):
    return _toolkit.dateAtInstant(millis)

# Simple URL retriever (supports POST)
def get_url(url, query=None, reference=None, contentType=None, post=None):  
  return _toolkit.getURL(url, query, reference, contentType, post)

# DEPRECATED (same as above)
def getURL(url, query=None, reference=None, contentType=None, post=None):  
  return _toolkit.getURL(url, query, reference, contentType, post)

# A managed TCP connection that attempts to stay open (includes instrumentation)
def TCP(dest=None, connected=None, received=None, sent=None, disconnected=None, timeout=None, sendDelimiters='\n', receiveDelimiters='\r\n', binaryStartStopFlags=None):
  return _toolkit.createTCP(dest, connected, received, sent, disconnected, timeout, sendDelimiters, receiveDelimiters, binaryStartStopFlags);

# A managed UDP connection for sending or receiving UDP (includes instrumentation)
def UDP(source='0.0.0.0:0', dest=None, ready=None, received=None, sent=None, intf=None):
  return _toolkit.createUDP(source, dest, ready, received, sent, intf);  

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

