# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node demonstrates a simple projetion designs controller.'''

# Libraries required by this node
import socket
import struct

# Functions used by this node
def send_cmd(cmd, arg=None):
  #open socket
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  sock.settimeout(10)
  try:
    sock.connect((param_ipAddress, param_port))
    packet = ":"+cmd
    if(arg): packet += " "+arg
    packet+="\r"
    sock.send(packet)
    data = sock.recv(1024)
    rcvpack = struct.unpack("<xxxxx4sx6sx", data[0:17])
    assert cmd in rcvpack[0]
    if(arg): assert arg in rcvpack[1]
    return rcvpack[1]
  except socket.error, e:
    print "socket error: %s\n" % e
    local_event_Error.emit(e)
  except AssertionError, e:
    print "command error: %s\n" % e
    local_event_Error.emit(e)
  finally:
    sock.close()

# Local actions this Node provides
def local_action_PowerOn(arg = None):
  """{"title":"Power on","desc":"Turns this node on.","group":"Power"}"""
  print 'Action PowerOn requested'
  send_cmd('POWR', '1')

def local_action_PowerOff(arg = None):
  """{"title":"Power off","desc":"Turns this node off.","group":"Power"}"""
  print 'Action PowerOff requested'
  send_cmd('POWR', '0')

def local_action_GetPower(arg = None):
  """{"title":"Get Power","desc":"Get current power state","group":"Power"}"""
  print 'Action GetPower requested'
  result = send_cmd('POST')
  if(result=='!00005' or result=='!00006'): 
    print 'critical power off'
    local_event_PowerOff.emit()
  if(result=='!00004'): print 'powering down'
  if(result=='!00003'): 
    print 'power is on'
    local_event_PowerOn.emit()
  if(result=='!00002'): print 'powering up'
  if(result=='!00000' or result=='!00001'): 
    print 'power is off'
    local_event_PowerOff.emit()

def local_action_SetInput(arg):
  '''{ "title": "Set input", "desc": "Set projector input.", "group": "Input", "schema": { "type":"string", "title": "Source", "required":true, "enum":["IVGA", "IHDM", "IDVI", "IYPP", "IRGS"] } }'''
  print 'Action SetInput requested: '+arg
  send_cmd(arg)

# Local events this Node provides
local_event_Error = LocalEvent('{"title":"Error","desc":"An error has occured while communicating with the device.","group":"General"}')
local_event_PowerOn = LocalEvent('{"title":"PowerOn","desc":"Power is On","group":"Power"}')
local_event_PowerOff = LocalEvent('{"title":"PowerOff","desc":"Power is Off","group":"Power"}')
# local_event_Error.emit(arg)

# Parameters used by this Node
param_ipAddress = Parameter('{"desc":"The IP address to connect to.","schema":{"type":"string"},"value":"192.168.100.1"}')

param_port = Parameter('{"desc":"The Port to connect to.","schema":{"type":"integer"},"value":"1025"}')

def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'

