# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This is ADAM IO node.'''

from pymodbus.client.sync import ModbusTcpClient
import threading
import atexit

param_ipAddress = Parameter('{"title":"IP Address", "desc":"The IP address to connect to.", "schema":{"type":"string"}}')
POLL = 100
# old firmware uses unit=0, new firmware unit=1. Change this if you are receiving a connection error
UNIT = 1

local_event_Input1On = LocalEvent('{"title":"Input 1 On","desc":"Input 1 On", "group":"Input 1"}')
local_event_Input2On = LocalEvent('{"title":"Input 2 On","desc":"Input 2 On", "group":"Input 2"}')
local_event_Input3On = LocalEvent('{"title":"Input 3 On","desc":"Input 3 On", "group":"Input 3"}')
local_event_Input4On = LocalEvent('{"title":"Input 4 On","desc":"Input 4 On", "group":"Input 4"}')
local_event_Input5On = LocalEvent('{"title":"Input 5 On","desc":"Input 5 On", "group":"Input 5"}')
local_event_Input6On = LocalEvent('{"title":"Input 6 On","desc":"Input 6 On", "group":"Input 6"}')
local_event_Input1Off = LocalEvent('{"title":"Input 1 Off","desc":"Input 1 Off", "group":"Input 1"}')
local_event_Input2Off = LocalEvent('{"title":"Input 2 Off","desc":"Input 2 Off", "group":"Input 2"}')
local_event_Input3Off = LocalEvent('{"title":"Input 3 Off","desc":"Input 3 Off", "group":"Input 3"}')
local_event_Input4Off = LocalEvent('{"title":"Input 4 Off","desc":"Input 4 Off", "group":"Input 4"}')
local_event_Input5Off = LocalEvent('{"title":"Input 5 Off","desc":"Input 5 Off", "group":"Input 5"}')
local_event_Input6Off = LocalEvent('{"title":"Input 6 Off","desc":"Input 6 Off", "group":"Input 6"}')
local_event_Error = LocalEvent('{"title":"Error","desc":"Error state."}')

lock = threading.Lock()

class ModbusPoll(threading.Thread):
  def __init__(self):
    threading.Thread.__init__(self)
    self.event = threading.Event()
    self.client = None
    self.current = [True,True,True,True,True,True]
  def run(self):
    self.client = ModbusTcpClient(param_ipAddress)
    while not self.event.isSet():
      lock.acquire()
      try:
        result = self.client.read_coils(0,6, unit=UNIT)
        for num in range(0,6):
          if (result.bits[num] != self.current[num]):
            func = globals()['local_event_Input'+str(num+1)+('On' if self.current[num] else 'Off')]
            func.emit()
          self.current[num] = result.bits[num]
      except AttributeError:
        local_event_Error.emit('Could not connect to ADAM')
      except Exception, e:
        local_event_Error.emit(e)
      finally:
        lock.release()
    self.event.wait(POLL/1000.0)
    self.client.close()
  def on(self, num):
    if not self.event.isSet():
      lock.acquire()
      try:
        self.client.write_coil(num, True, unit=UNIT)
      except Exception, e:
        local_event_Error.emit(e)
      finally:
        lock.release()
  def off(self, num):
    if not self.event.isSet():
      lock.acquire()
      try:
        self.client.write_coil(num, False, unit=UNIT)
      except Exception, e:
        local_event_Error.emit(e)
      finally:
        lock.release()
  def stop(self):
    self.event.set()

th = ModbusPoll()

@atexit.register
def cleanup():
  print 'shutdown'
  th.stop()

def main():
  if(param_ipAddress):
    th.start()
  else:
    local_event_Error.emit('configuration not set')
    print 'configuration not set'

# Local actions this Node provides
def local_action_Relay1On(arg):
  '''{"title":"Relay 1 On","desc":"Turns Relay 1 On.", "group":"Relay 1"}'''
  th.on(16)
  print 'Action Relay1On requested.'

def local_action_Relay1Off(arg):
  '''{"title":"Relay 1 Off","desc":"Turns Relay 1 Off.", "group":"Relay 1"}'''
  th.off(16)
  print 'Action Relay1Off requested.'

def local_action_Relay2On(arg):
  '''{"title":"Relay 2 On","desc":"Turns Relay 2 On.", "group":"Relay 2"}'''
  th.on(17)
  print 'Action Relay2On requested.'

def local_action_Relay2Off(arg):
  '''{"title":"Relay 2 Off","desc":"Turns Relay 2 Off.", "group":"Relay 2"}'''
  th.off(17)
  print 'Action Relay2Off requested.'

def local_action_Relay3On(arg):
  '''{"title":"Relay 3 On","desc":"Turns Relay 3 On.", "group":"Relay 3"}'''
  th.on(17)
  print 'Action Relay3On requested.'

def local_action_Relay3Off(arg):
  '''{"title":"Relay 3 Off","desc":"Turns Relay 3 Off.", "group":"Relay 3"}'''
  th.off(17)
  print 'Action Relay3Off requested.'

def local_action_Relay4On(arg):
  '''{"title":"Relay 4 On","desc":"Turns Relay 4 On.", "group":"Relay 4"}'''
  th.on(17)
  print 'Action Relay4On requested.'

def local_action_Relay4Off(arg):
  '''{"title":"Relay 4 Off","desc":"Turns Relay 4 Off.", "group":"Relay 4"}'''
  th.off(17)
  print 'Action Relay4Off requested.'

def local_action_Relay5On(arg):
  '''{"title":"Relay 5 On","desc":"Turns Relay 5 On.", "group":"Relay 5"}'''
  th.on(17)
  print 'Action Relay5On requested.'

def local_action_Relay5Off(arg):
  '''{"title":"Relay 5 Off","desc":"Turns Relay 5 Off.", "group":"Relay 5"}'''
  th.off(17)
  print 'Action Relay5Off requested.'

def local_action_Relay6On(arg):
  '''{"title":"Relay 6 On","desc":"Turns Relay 6 On.", "group":"Relay 6"}'''
  th.on(17)
  print 'Action Relay6On requested.'

def local_action_Relay6Off(arg):
  '''{"title":"Relay 6 Off","desc":"Turns Relay 6 Off.", "group":"Relay 6"}'''
  th.off(17)
  print 'Action Relay6Off requested.'