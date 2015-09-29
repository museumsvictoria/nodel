# holds special 'reserved' words that
# the bindings and parameter extractor 
# uses   

def LocalEvent(schemaDictOrJSONorTitle = None):
    return schemaDictOrJSONorTitle;

def RemoteAction(schemaDictOrJSONorTitle = None):
    return schemaDictOrJSONorTitle;

def Parameter(schemaDictOrJSONorTitle = None):
    return schemaDictOrJSONorTitle;

# Toolkit related
# ('_toolkit' is injected by PyNode.java)

# provide a console object
console = _toolkit.getConsole()

# Simple JSON encoder
def json_encode(obj):
  return _toolkit.jsonEncode(obj)

# Simple JSON decoder
def json_decode(json):
  return _toolkit.jsonDecode(json)

def call(func, complete=None, error=None):
  """Schedules a function to be called immediately"""
  _toolkit.callDelayed(0, func, complete, error)

def call_delayed(delayInSeconds, func, complete=None, error=None):
  _toolkit.callDelayed(delayInSeconds, func, complete, error)

# Returns an atomically incrementing long integer.  
def next_seq():
    return _toolkit.nextSequenceNumber()

# Returns a high-precision atomically incrementing clock in milliseconds (can wrap)
def system_clock():
    return _toolkit.systemClockInMillis();

# Simple URL retriever (supports POST)
def getURL(url, query=None, reference=None, contentType=None, post=None):
  "Retrieves the contents of a plain URL."
  return _toolkit.getURL(url, query, reference, contentType, post)

def TCP(dest=None, connected=None, received=None, sent=None, disconnected=None, timeout=None, sendDelimiters='\n', receiveDelimiters='\r\n', binaryStartStopFlags=None):
  """Creates a simple TCP connection that attempts to stay open"""
  return _toolkit.createTCP(dest, connected, received, sent, disconnected, timeout, sendDelimiters, receiveDelimiters, binaryStartStopFlags);
  
def UDP(source='0.0.0.0:0', dest=None, ready=None, received=None, sent=None, intf=None):
  """Creates a permanent UDP socket used for sending and receiving."""
  return _toolkit.createUDP(source, dest, ready, received, sent, intf);  
  
class Timer:
  def __init__(self, func, intervalInSeconds, firstDelayInSeconds=0, stopped=False):
      """Creates a repeating timer"""
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
  
def Node(nodeName):
    """Creates a managed node"""
    return _toolkit.createNode(nodeName)

def Subnode(baseName):
    """Creates a managed node based on the name of an existing node"""
    return _toolkit.createSubnode(baseName)
  
def Event(name, metadata = None):
    return _toolkit.createEvent(name, metadata)

def Signal(name, metadata = None):
    return _toolkit.createEvent(name, metadata)
    
def Action(name, handler, metadata = None):
	return _toolkit.createAction(name, handler, metadata)

def ImpromptuSignal(name, metadata):
    signal = _toolkit.createEvent(name, metadata)
    action = _toolkit.createAction("Emit signal '%s'" % name, lambda arg: signal.emit(arg), metadata)
    return (signal, action)

def releaseNode(node):
  """Permanently destroys a node (releases all resources)"""
  return _toolkit.releaseNode(node)