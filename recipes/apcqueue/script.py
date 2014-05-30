# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This is an APC command queueing node'''

from Queue import *
import threading
import atexit

#remote actions
remote_action_Turn1On = RemoteAction()
remote_action_Turn1Off = RemoteAction()
remote_action_Turn2On = RemoteAction()
remote_action_Turn2Off = RemoteAction()
remote_action_Turn3On = RemoteAction()
remote_action_Turn3Off = RemoteAction()
remote_action_Turn4On = RemoteAction()
remote_action_Turn4Off = RemoteAction()
remote_action_Turn5On = RemoteAction()
remote_action_Turn5Off = RemoteAction()
remote_action_Turn6On = RemoteAction()
remote_action_Turn6Off = RemoteAction()
remote_action_Turn7On = RemoteAction()
remote_action_Turn7Off = RemoteAction()
remote_action_Turn8On = RemoteAction()
remote_action_Turn8Off = RemoteAction()

local_event_Error = LocalEvent('{"title":"Error","desc":"Error state."}')

def local_action_Activate(x):
  '''{ "title" = "Turn all on", "desc" = "Turn all on" }'''
  for int in range(1,9):
    queue.put({'function': 'remote_action_Turn'+str(int)+'On'})
  print 'Action TurnAllOn requested.'

def local_action_Deactivate(x):
  '''{ "title" = "Turn all off", "desc" = "Turn all off" }'''
  for int in range(1,9):
    queue.put({'function': 'remote_action_Turn'+str(int)+'Off'})
  print 'Action TurnAllOff requested.'

class TimerClass(threading.Thread):
  def __init__(self):
    threading.Thread.__init__(self)
    self.event = threading.Event()
  def run(self):
    while not self.event.isSet():
      if queue.empty() != True:
        job = queue.get()
        try:
          print "Calling command " + job['function']
          func = globals()[job['function']]
          func.call('x')
          queue.task_done()
        except Exception, e:
          print e
          print "Failed to call command " + job['function']
        self.event.wait(3)
      else:
        self.event.wait(3)
  def stop(self):
      self.event.set()

queue = Queue()
th = TimerClass()

@atexit.register
def cleanup():
  print 'shutdown'
  th.stop()

def main():
  th.start()
  print 'Nodel script started.'