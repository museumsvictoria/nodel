# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

import socket
import struct

param_ipAddress = Parameter('{"title":"IP Address", "desc":"The IP address to connect to.", "schema":{"type":"string"}}')
param_port = Parameter('{"title":"Port", "desc":"The port to connect to (default 80).", "schema":{"type":"integer"}, "value": 80}}')
param_password = Parameter('{"title":"Password", "desc":"The password of the device (default is PASS).", "schema":{"type":"string"}}')

local_event_Error = LocalEvent('{"title":"Error","desc":"Error state."}')
local_event_PowerOn = LocalEvent('{"title":"PowerOn","desc":"Power is on."}')
local_event_PowerOff = LocalEvent('{"title":"PowerOff","desc":"Power is off."}')

def main():
  print 'started'

def cmd(control):
  try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(10)
    s.connect((param_ipAddress, param_port))
    s.sendall('\x1b'+param_password+'\x1b'+control+'\x0d')
    data = s.recv(16)
    if(data=="OFF"):
      local_event_PowerOff.emit()
    elif(data=="ON"):
      local_event_PowerOn.emit()
    else:
      raise Exception("Error: unexpected response")
  except Exception, e:
    local_event_Error.emit(e)
  finally:
    s.close()

# Local actions this Node provides
def local_action_Activate(arg):
  '''{"title":"Turn on","desc":"Turns iboot on."}'''
  cmd("n")
  print 'Action TurnOn requested.'

def local_action_Deactivate(arg):
  '''{"title":"Turn off","desc":"Turns iboot off."}'''
  cmd("f")
  print 'Action TurnOff requested.'

def local_action_GetPower(arg):
  '''{"title":"Get Power","desc":"Get iboot power state."}'''
  cmd("q")
  print 'Action GetPower requested.'