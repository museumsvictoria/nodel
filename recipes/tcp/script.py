# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node demonstrates a simple tcp controller.'''

# Libraries required by this node
import socket

# Functions used by this node
def send_tcp_string(msg):
  #open socket
  sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
  sock.settimeout(10)
  try:
    sock.connect((param_ipAddress, param_port))
    sock.send(msg)
    data = sock.recv(1024)
    assert msg in data
  except socket.error, e:
    print "socket error: %s\n" % e
    local_event_Error.emit(e)
  except AssertionError, e:
    print "command error: %s\n" % e
    local_event_Error.emit(e)
  finally:
    sock.close()

# Local actions this Node provides
def local_action_Start(arg = None):
  """{"title":"Turns on","desc":"Turns this node on.","group":"General"}"""
  print 'Action TurnOn requested'
  send_tcp_string('start\n')

def local_action_Stop(arg = None):
  """{"title":"Turns off","desc":"Turns this node off.","group":"General"}"""
  print 'Action TurnOff requested'
  send_tcp_string('stop\n')

def local_action_SetLogging(arg = None):
  """{"title":"Set logging","desc":"Set logging level.","schema":{"title":"Level","type":"string","enum":["file","normal"],"required":"true"},"group":"General"}"""
  print 'Action SetLogging requested - '+arg
  send_tcp_string('logging:'+arg+'\n')

def local_action_SetVolume(arg = None):
  """{"title":"Set volume","desc":"Set volume.","schema":{"title":"Level","type":"integer","required":"true"},"group":"General"}"""
  print 'Action SetVolume requested - '+str(arg)
  send_tcp_string('volume:'+str(arg)+'\n')

# Local events this Node provides
local_event_Error = LocalEvent('{"title":"Error","desc":"An error has occured while communicating with the device.","group":"General"}')
# local_event_Error.emit(arg)

# Parameters used by this Node
param_ipAddress = Parameter('{"desc":"The IP address to connect to.","schema":{"type":"string"},"value":"192.168.100.1"}')

param_port = Parameter('{"desc":"The Port to connect to.","schema":{"type":"integer"},"value":"80"}')

def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'

