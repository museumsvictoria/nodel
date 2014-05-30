# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''iBoot Node'''

### Libraries required by this node
import socket
import struct



### Parameters used by this Node
PORT = 9100
param_ipAddress = Parameter('{"desc":"IP address","schema":{"type":"string"}}')



### Functions used by this node
def set_status(control):
  try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(15)
    s.connect((param_ipAddress, PORT))
    s.sendall('hello-000')
    data = s.recv(16)
    seq = struct.unpack("<H",data)[0]
    seq = seq+1
    desc = 1
    data = struct.pack('<B21s21sBBHBB',3,'','',desc,0,seq,1,control)
    s.sendall(data)
    data = s.recv(16)
    if(struct.unpack('<B',data)[0]==0):
      print 'Success'
      if(control==0):
        local_event_PowerOff.emit()
      if(control==1):
        local_event_PowerOn.emit()
    else:
      raise Exception("Error sending command")
  except Exception, e:
    local_event_Error.emit(e)
  finally:
    s.close()

def get_status():
  try:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(15)
    s.connect((param_ipAddress, PORT))
    s.sendall('hello-000')
    data = s.recv(16)
    seq = struct.unpack("<H",data)[0]
    seq = seq+1
    desc = 4
    data = struct.pack('<B21s21sBBH',3,'','',desc,0,seq)
    s.sendall(data)
    data = s.recv(16)
    state = struct.unpack('<BBBBBBBB',data)[0]
    if(state==0):
      local_event_PowerOff.emit()
    if(state==1):
      local_event_PowerOn.emit()
    s.close()
  except Exception, e:
    local_event_Error.emit(e)
  finally:
    s.close()



### Local actions this Node provides
def local_action_PowerOn(arg):
  '''{"title":"PowerOn","desc":"Power on iBoot"}'''
  set_status(1)
  print 'Action PowerOn requested.'

def local_action_PowerOff(arg):
  '''{"title":"PowerOff","desc":"Power off iBoot"}'''
  set_status(0)
  print 'Action PowerOff requested.'

def local_action_GetPower(arg):
  '''{"title":"GetPower","desc":"Get iBoot power state"}'''
  get_status()
  print 'Action GetPower requested.'



### Local events this Node provides
local_event_Error = LocalEvent('{"title":"Error","desc":"Error state"}')
local_event_PowerOn = LocalEvent('{"title":"PowerOn","desc":"Power is on"}')
local_event_PowerOff = LocalEvent('{"title":"PowerOff","desc":"Power is off"}')



### Main
def main():
  print 'started'