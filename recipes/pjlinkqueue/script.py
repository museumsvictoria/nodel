# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

from Queue import *
import threading
import atexit

remote_action_PowerOn = RemoteAction()
remote_action_PowerOff = RemoteAction()
remote_action_SetInput = RemoteAction()

def local_action_activate(x = None):
  '''{ "title": "Turn on", "desc": "Turn on." }'''
  queue.put({'function': 'remote_action_PowerOn', 'delay': 120})
  queue.put({'function': 'remote_action_SetInput', 'arg':{"source":"DIGITAL", "number":1}, 'delay': 5})
  print 'Activated'
  
def local_action_deactivate(x = None):
  '''{ "title": "Turn off", "desc": "Turn off." }'''
  queue.put({'function': 'remote_action_PowerOff', 'delay': 120})
  print 'Deactivated'

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
          arg = job['args'] if 'args' in job else ''
          func.call(arg)
          self.event.wait(job['delay'])
          queue.task_done()
        except Exception, e:
          print e
          print "Failed to call command " + job['function']
      else:
        self.event.wait(1)
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