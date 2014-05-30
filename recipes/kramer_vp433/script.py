# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node controls input switching on a Kramer VP-433'''

# Libraries required by this node
import socket
import re
import asyncore
from time import sleep
import threading
import atexit

client = None
loop_thread = threading.Thread(target=asyncore.loop, name="Asyncore Loop", args=(1,False))

@atexit.register
def cleanup():
  global client
  print 'shutdown'
  client.shutdown=True
  client.close()

class TCPClient(asyncore.dispatcher_with_send):
  def __init__(self, host, port):
    asyncore.dispatcher.__init__(self)
    self.host = host
    self.port = port
    self.out_buffer = ""
    self.in_buffer = ""
    self.retry = 1
    self.shutdown = False
    self.connected = False
    self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
    self.ping = PingThread(self)
    self.ping.start()
    print "initialising connection with the switcher."
    self.connect((self.host,self.port))
  def sendCommand(self, command):
    self.out_buffer += command
  def handle_connect(self):
    print "connected"
    self.connected = True
  def handle_expt(self):
    if not(self.shutdown):
      print "unable to connect"
      local_event_Error.emit('unable to connec')
      self.reconnect()
  def handle_error(self):
    if not (self.shutdown):
      print "python error"
      local_event_Error.emit('python error')
      self.reconnect()
  def handle_close(self):
    print "connection closed"
    if not (self.shutdown):
      local_event_Error.emit('connection closed')
      self.reconnect()
  def reconnect(self):
    self.connected = False
    self.close()
    sleep(self.retry)
    self.retry = 120 if self.retry > 120 else self.retry * 2
    self.create_socket(socket.AF_INET, socket.SOCK_STREAM)
    print "initialising connection with the switcher."
    self.connect((self.host,self.port))
  def handle_read(self):
    newdata = self.recv(8192)
    #if newdata == "": print "no data"
    self.in_buffer += newdata
    while True:
      new_buffer = self.in_buffer.split('\r',1)
      if len(new_buffer) > 1:
        if(new_buffer[0]!=""):
          handleResult(new_buffer[0])
        self.in_buffer = new_buffer[1]
      else: break

class PingThread(threading.Thread):
  def __init__(self, client):
    super(PingThread,self).__init__()
    self.client = client
  def run(self):
    counter = 0
    while self.client.shutdown == False:
      sleep(1)
      if self.client.connected == True:
        counter += 1
        if counter == 10:
          print "ping"
          self.client.sendCommand("\r")
          counter = 0

def handleResult(result):
  match_input = re.search(r"Z\s(?:3|4)\s0\s(\d{1,2})", result, re.MULTILINE | re.VERBOSE)
  if match_input:
    print 'Current input: '+str(match_input.group(1))
    local_event_CurrentInput.emit(input_list[int(match_input.group(1))-1])
  match_freeze = re.search(r"Z\s6\s1\s(\d{0,1})", result, re.MULTILINE | re.VERBOSE)
  if match_freeze:
    print 'Freeze state: '+str(match_freeze.group(1))
    if(match_freeze.group(1)=="0"): local_event_FreezeOff.emit()
    if(match_freeze.group(1)=="1"): local_event_FreezeOn.emit()
  match_blank = re.search(r"Z\s6\s2\s(\d{0,1})", result, re.MULTILINE | re.VERBOSE)
  if match_blank:
    print 'Blanking state: '+str(match_blank.group(1))
    if(match_blank.group(1)=="0"): local_event_BlankOff.emit()
    if(match_blank.group(1)=="1"): local_event_BlankOn.emit()
  match_mute = re.search(r"Z\s6\s3\s(\d{0,1})", result, re.MULTILINE | re.VERBOSE)
  if match_mute:
    print 'Mute state: '+str(match_mute.group(1))
    if(match_mute.group(1)=="0"): local_event_MuteOff.emit()
    if(match_mute.group(1)=="1"): local_event_MuteOn.emit()

# Local actions this Node provides
def local_action_SetInput(arg = None):
  '''{"title":"Set input","desc":"Sets input.","group":"Input","schema":{"title":"Input","required":true,"type":"string", "enum":["CV1","CV2","COMP1","COMP2","PC1","PC2","HDMI1","HDMI2","HDMI3","HDMI4"]}}'''
  print 'Action setinput requested: '+arg
  client.sendCommand('Y 3 0 '+str(input_list.index(arg)+1)+'\r')

def local_action_GetInput(arg = None):
  '''{"title":"Get current input","desc":"Gets current input.","group":"Input"}'''
  print 'Action getinput requested'
  client.sendCommand('Y 4 0\r')

def local_action_FreezeOn(arg = None):
  '''{"title":"Freeze On","desc":"Freeze on.","group":"General"}}'''
  print 'Action Freezeon requested'
  client.sendCommand('Y 6 3 1\r')

def local_action_FreezeOff(arg = None):
  '''{"title":"Freeze Off","desc":"Freeze off.","group":"General"}}'''
  print 'Action Freezeoff requested'
  client.sendCommand('Y 6 3 0\r')

def local_action_BlankOn(arg = None):
  '''{"title":"Blank On","desc":"Blank on.","group":"General"}}'''
  print 'Action blankon requested'
  client.sendCommand('Y 6 2 1\r')

def local_action_BlankOff(arg = None):
  '''{"title":"Blank Off","desc":"Blank off.","group":"General"}}'''
  print 'Action blankoff requested'
  client.sendCommand('Y 6 2 0\r')

def local_action_MuteOn(arg = None):
  '''{"title":"Mute On","desc":"Mute on.","group":"General"}}'''
  print 'Action Muteon requested'
  client.sendCommand('Y 6 3 1\r')

def local_action_MuteOff(arg = None):
  '''{"title":"Mute Off","desc":"Mute off.","group":"General"}}'''
  print 'Action Muteoff requested'
  client.sendCommand('Y 6 3 0\r')

# Local events this Node provides
local_event_Error = LocalEvent('{"title":"Error","desc":"An error has occured while communicating with the device.","group":"General"}')
local_event_CurrentInput = LocalEvent('{"title":"CurrentInput","desc":"Current Input.","group":"General","schema":{"title":"Input","type":"string"}}')
local_event_FreezeOn = LocalEvent('{"title":"FreezeOn","desc":"Freeze is on.","group":"General"}')
local_event_FreezeOff = LocalEvent('{"title":"FreezeOff","desc":"Freeze is off.","group":"General"}')
local_event_BlankOn = LocalEvent('{"title":"BlankOn","desc":"Blank is on.","group":"General"}')
local_event_BlankOff = LocalEvent('{"title":"BlankOff","desc":"Blank is off.","group":"General"}')
local_event_MuteOn = LocalEvent('{"title":"MuteOn","desc":"Mute is on.","group":"General"}')
local_event_MuteOff = LocalEvent('{"title":"MuteOff","desc":"Mute is off.","group":"General"}')
# eg. local_event_Error.emit(arg)

# Parameters used by this Node
param_ipAddress = Parameter('{"desc":"The IP address to connect to.","schema":{"type":"string","required":true},"value":"192.168.1.39"}')
param_port = Parameter('{"desc":"The Port to connect to.","schema":{"type":"integer","required":true},"value":"10001"}')
input_list = ["CV1","CV2","COMP1","COMP2","PC1","PC2","HDMI1","HDMI2","HDMI3","HDMI4"]

def main(arg = None):
  global client
  print 'Nodel script started.'
  if(param_ipAddress and param_port):
    client = TCPClient(param_ipAddress, param_port)
    loop_thread.start()
  else:
    local_event_Error.emit('No IPAddress or Port specified')
    print 'No IPAddress or Port specified'


