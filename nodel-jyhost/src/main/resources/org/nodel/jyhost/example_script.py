# Nodel auto-generated example Python script that applies to version %VERSION% or later.
'''This node demonstrates a simple PyNode.'''

# Local actions this Node provides
def local_action_AdjustLevel(arg = None):
  """{"title":"Adjust level","desc":"Adjusts a level.","schema":{"type":"integer","title":"Level"},"group":"General","order":3}"""
  print 'Action AdjustLevel requested'

def local_action_TurnOn(arg = None):
  """{"title":"Turns on","desc":"Turns this node on.","group":"Power","caution":"Ensure hardware is in a state to be turned on.","order":2}"""
  print 'Action TurnOn requested'

# Local events this Node provides
local_event_Triggered = LocalEvent('{"title":"Sensor is triggered","desc":"When this sensor is triggered.","group":"General","order":1}')
# local_event_Triggered.emit(arg)

# Remote actions this Node requires
remote_action_TurnProjectorOn = RemoteAction('{"title":"Turn on","desc":"Turns on the device.","group":"Power","order":4}')
# remote_action_TurnProjectorOn.call(arg)

# Remote events this Node requires
def remote_event_SensorTriggered(arg = None):
  """{"title":"Sensor triggered","desc":"Occurs when the sensor is triggered.","group":"Sensing","order":5}"""
  print 'Remote event SensorTriggered arrived'

# Parameters used by this Node
param_ipAddress = Parameter('{"desc":"The IP address to connect to.","schema":{"type":"string"},"value":"192.168.100.1","title":"IP address","order":0}')

def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'

