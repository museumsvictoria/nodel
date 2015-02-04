'''Exhibit Node'''

### Libraries required by this Node
from time import sleep



### Local actions this Node provides
def local_action_Enable(arg = None):
  """{"title":"Enable","desc":"Enable"}"""
  print 'Action Enable requested.'
  remote_action_PowerOnRP.call()
  remote_action_PowerOnPC.call()
  remote_action_PowerOnSD.call()

def local_action_Disable(arg = None):
  """{"title":"Disable","desc":"Disable"}"""
  print 'Action Disable requested.'
  remote_action_PowerOffSD.call()
  remote_action_PowerOffPC.call()
  sleep(20)
  remote_action_PowerOffRP.call()

def local_action_MuteOn(arg = None):
  """{"title":"MuteOn","desc":"MuteOn"}"""
  print 'Action MuteOn requested.'
  remote_action_MuteOnPC.call()
  remote_action_MuteOnSD.call()

def local_action_MuteOff(arg = None):
  """{"title":"MuteOff","desc":"MuteOff"}"""
  print 'Action MuteOff requested.'
  remote_action_MuteOffPC.call()
  remote_action_MuteOffSD.call()

def local_action_Restart(arg = None):
  """{"title":"Restart","desc":"Restart"}"""
  print 'Action Restart requested.'
  remote_action_RestartPC.call()



### Remote actions this Node requires
remote_action_MuteOnSD = RemoteAction()
remote_action_MuteOffSD = RemoteAction()

remote_action_MuteOnPC = RemoteAction()
remote_action_MuteOffPC = RemoteAction()

remote_action_PowerOnRP = RemoteAction()
remote_action_PowerOffRP = RemoteAction()

remote_action_PowerOnPC = RemoteAction()
remote_action_PowerOffPC = RemoteAction()

remote_action_RestartPC = RemoteAction()

remote_action_PowerOnSD = RemoteAction()
remote_action_PowerOffSD = RemoteAction()

remote_action_PowerOnPJ = RemoteAction()
remote_action_PowerOffPJ = RemoteAction()



### Remote events this Node requires
def remote_event_Enable(arg = None):
  """{"title":"Enable","desc":"Enable","group":"General"}"""
  print 'Remote event Enable arrived.'
  local_action_Enable()

def remote_event_Disable(arg = None):
  """{"title":"Disable","desc":"Disable","group":"General"}"""
  print 'Remote event Disable arrived.'
  local_action_Disable()

def remote_event_MuteOn(arg = None):
  """{"title":"MuteOn","desc":"MuteOn","group":"General"}"""
  print 'Remote event MuteOn arrived.'
  local_action_MuteOn()

def remote_event_MuteOff(arg = None):
  """{"title":"MuteOff","desc":"MuteOff","group":"General"}"""
  print 'Remote event MuteOff arrived.'
  local_action_MuteOff()



### Main
def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'