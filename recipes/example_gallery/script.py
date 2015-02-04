'''Gallery Node'''

### Libraries required by this Node
from time import sleep



### Local events this Node provides
local_event_AllOn = LocalEvent('{"title":"AllOn","desc":"AllOn","group":"General"}')
local_event_AllOff = LocalEvent('{"title":"AllOff","desc":"AllOff","group":"General","Caution":"Are you sure?"}')
local_event_MuteOn = LocalEvent('{"title":"MuteOn","desc":"MuteOn","group":"General"}')
local_event_MuteOff = LocalEvent('{"title":"MuteOff","desc":"MuteOff","group":"General"}')



### Remote events this Node requires
def remote_event_AllOn(arg = None):
  """{"title":"AllOn","desc":"AllOn","group":"General"}"""
  print 'Remote event AllOn arrived.'
  local_event_AllOn.emit()

def remote_event_AllOff(arg = None):
  """{"title":"AllOff","desc":"AllOff","group":"General"}"""
  print 'Remote event AllOff arrived.'
  local_event_AllOff.emit()

def remote_event_MuteOn(arg = None):
  """{"title":"MuteOn","desc":"MuteOn","group":"General"}"""
  print 'Remote event MuteOn arrived.'
  local_event_MuteOn.emit()

def remote_event_MuteOff(arg = None):
  """{"title":"MuteOff","desc":"MuteOff","group":"General"}"""
  print 'Remote event MuteOff arrived.'
  local_event_MuteOff.emit()



### Main
def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'