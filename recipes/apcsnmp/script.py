# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This is an APC controller node'''
import socket
import re
from time import sleep

ON = 1
OFF = 2
OID = "2b06010401823e01010404020103"
SET_PACKET_HEAD = "3030020100040770726976617465a3220202000102010002010030163014060f" + OID
GET_PACKET_HEAD = "302f020100040770726976617465a0210202000102010002010030153013060f" + OID
GET_TAIL = "0500"
VAR_INT = "0201"
UDP_PORT = 161

local_event_Outlet1On = LocalEvent('{ "title": "Outlet 1 On", "desc": "Outlet 1 On.", "group": "Outlet 1" }')
local_event_Outlet1Off = LocalEvent('{ "title": "Outlet 1 Off", "desc": "Outlet 1 Off.", "group": "Outlet 1" }')
local_event_Outlet2On = LocalEvent('{ "title": "Outlet 2 On", "desc": "Outlet 2 On.", "group": "Outlet 2" }')
local_event_Outlet2Off = LocalEvent('{ "title": "Outlet 2 Off", "desc": "Outlet 2 Off.", "group": "Outlet 2" }')
local_event_Outlet3On = LocalEvent('{ "title": "Outlet 3 On", "desc": "Outlet 3 On.", "group": "Outlet 3" }')
local_event_Outlet3Off = LocalEvent('{ "title": "Outlet 3 Off", "desc": "Outlet 3 Off.", "group": "Outlet 3" }')
local_event_Outlet4On = LocalEvent('{ "title": "Outlet 4 On", "desc": "Outlet 4 On.", "group": "Outlet 4" }')
local_event_Outlet4Off = LocalEvent('{ "title": "Outlet 4 Off", "desc": "Outlet 4 Off.", "group": "Outlet 4" }')
local_event_Outlet5On = LocalEvent('{ "title": "Outlet 5 On", "desc": "Outlet 5 On.", "group": "Outlet 5" }')
local_event_Outlet5Off = LocalEvent('{ "title": "Outlet 5 Off", "desc": "Outlet 5 Off.", "group": "Outlet 5" }')
local_event_Outlet6On = LocalEvent('{ "title": "Outlet 6 On", "desc": "Outlet 6 On.", "group": "Outlet 6" }')
local_event_Outlet6Off = LocalEvent('{ "title": "Outlet 6 Off", "desc": "Outlet 6 Off.", "group": "Outlet 6" }')
local_event_Outlet7On = LocalEvent('{ "title": "Outlet 7 On", "desc": "Outlet 7 On.", "group": "Outlet 7" }')
local_event_Outlet7Off = LocalEvent('{ "title": "Outlet 7 Off", "desc": "Outlet 7 Off.", "group": "Outlet 7" }')
local_event_Outlet8On = LocalEvent('{ "title": "Outlet 8 On", "desc": "Outlet 8 On.", "group": "Outlet 8" }')
local_event_Outlet8Off = LocalEvent('{ "title": "Outlet 8 Off", "desc": "Outlet 8 Off.", "group": "Outlet 8" }')
local_event_Error = LocalEvent('{ "title": "Error", "desc": "Error sending command." }')

param_ipAddress = Parameter('{ "name": "ipAddress", "schema": {"type": "string"} }')

def set_status(outlet, state):
  sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  try:
    sock.settimeout(3)
    sock.bind(('', 0))
    packet = SET_PACKET_HEAD + '%02x' % outlet + VAR_INT + '%02x' % state
    sock.sendto(packet.decode('hex'), (param_ipAddress, UDP_PORT))
    data, addr = sock.recvfrom(1024)
    buffer = data
    while(not read_result(buffer)):
      data, addr = sock.recvfrom(1024)
      buffer+=data
  except:
    print 'error setting status for outlet %02x' % outlet
    local_event_Error.emit('error setting status for outlet %02x' % outlet)
  finally:
    if sock:
      sock.close()

def get_status(outlet):
  sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  try:
    sock.settimeout(3)
    sock.bind(('', 0))
    packet = GET_PACKET_HEAD + '%02x' % outlet + GET_TAIL
    sock.sendto(packet.decode('hex'), (param_ipAddress, UDP_PORT))
    data, addr = sock.recvfrom(1024)
    buffer = data
    while(not read_result(buffer)):
      data, addr = sock.recvfrom(1024)
      buffer+=data
  except:
    print 'error getting status for outlet %02x' % outlet
    local_event_Error.emit('error getting status for outlet %02x' % outlet)
  finally:
    if sock:
      sock.close()

def read_result(data):
  match = re.search(OID+r"(\d{2})"+VAR_INT+r"(\d{2})", data.encode('hex'), re.MULTILINE | re.VERBOSE)
  if match:
    globals()["local_event_Outlet"+str(int(match.group(1)))+('On' if match.group(2)=="01" else 'Off')].emit()
    return True
  else:
    return False

def local_action_Turn1On(arg = None):
  '''{ "title" = "Turn 1 on", "desc" = "Turn 1 on", "group" = "Outlet 1" }'''
  print 'Action Turn1On requested.'
  set_status(1,ON)

def local_action_Turn1Off(arg = None):
  '''{ "title" = "Turn 1 off", "desc" = "Turn 1 off", "group" = "Outlet 1" }'''
  print 'Action Turn1Off requested.'
  set_status(1,OFF)

def local_action_Turn2On(arg = None):
  '''{ "title" = "Turn 2 on", "desc" = "Turn 2 on", "group" = "Outlet 2" }'''
  print 'Action Turn2On requested.'
  set_status(2,ON)

def local_action_Turn2Off(arg = None):
  '''{ "title" = "Turn 2 off", "desc" = "Turn 2 off", "group" = "Outlet 2" }'''
  print 'Action Turn2Off requested.'
  set_status(2,OFF)

def local_action_Turn3On(arg = None):
  '''{ "title" = "Turn 3 on", "desc" = "Turn 3 on", "group" = "Outlet 3" }'''
  print 'Action Turn3On requested.'
  set_status(3,ON)

def local_action_Turn3Off(arg = None):
  '''{ "title" = "Turn 3 off", "desc" = "Turn 3 off", "group" = "Outlet 3" }'''
  print 'Action Turn3Off requested.'
  set_status(3,OFF)

def local_action_Turn4On(arg = None):
  '''{ "title" = "Turn 4 on", "desc" = "Turn 4 on", "group" = "Outlet 4" }'''
  print 'Action Turn4On requested.'
  set_status(4,ON)

def local_action_Turn4Off(arg = None):
  '''{ "title" = "Turn 4 off", "desc" = "Turn 4 off", "group" = "Outlet 4" }'''
  print 'Action Turn4Off requested.'
  set_status(4,OFF)

def local_action_Turn5On(arg = None):
  '''{ "title" = "Turn 5 on", "desc" = "Turn 5 on", "group" = "Outlet 5" }'''
  print 'Action Turn5On requested.'
  set_status(5,ON)

def local_action_Turn5Off(arg = None):
  '''{ "title" = "Turn 5 off", "desc" = "Turn 5 off", "group" = "Outlet 5" }'''
  print 'Action Turn5Off requested.'
  set_status(5,OFF)

def local_action_Turn6On(arg = None):
  '''{ "title" = "Turn 6 on", "desc" = "Turn 6 on", "group" = "Outlet 6" }'''
  print 'Action Turn6On requested.'
  set_status(6,ON)

def local_action_Turn6Off(arg = None):
  '''{ "title" = "Turn 6 off", "desc" = "Turn 6 off", "group" = "Outlet 6" }'''
  print 'Action Turn6Off requested.'
  set_status(6,OFF)

def local_action_Turn7On(arg = None):
  '''{ "title" = "Turn 7 on", "desc" = "Turn 7 on", "group" = "Outlet 7" }'''
  print 'Action Turn7On requested.'
  set_status(7,ON)

def local_action_Turn7Off(arg = None):
  '''{ "title" = "Turn 7 off", "desc" = "Turn 7 off", "group" = "Outlet 7" }'''
  print 'Action Turn7Off requested.'
  set_status(7,OFF)

def local_action_Turn8On(arg = None):
  '''{ "title" = "Turn 8 on", "desc" = "Turn 8 on", "group" = "Outlet 8" }'''
  print 'Action Turn8On requested.'
  set_status(8,ON)

def local_action_Turn8Off(arg = None):
  '''{ "title" = "Turn 8 off", "desc" = "Turn 8 off", "group" = "Outlet 8" }'''
  print 'Action Turn8Off requested.'
  set_status(8,OFF)

def local_action_TurnAllOn(arg = None):
  '''{ "title" = "Turn all on", "desc" = "Turn all on", "group" = "General" }'''
  print 'Action TurnAllOn requested.'
  for i in range(1,9):
    set_status(i, ON)

def local_action_TurnAllOff(arg = None):
  '''{ "title" = "Turn all off", "desc" = "Turn all off", "group" = "General" }'''
  print 'Action TurnAllOff requested.'
  for i in range(1,9):
    set_status(i, OFF)

def local_action_TurnAllOnDelayed(arg = None):
  '''{ "title" = "Turn all on delayed", "desc" = "Turn all on delayed", "group" = "General" }'''
  print 'Action TurnAllOnDelayed requested.'
  for i in range(1,9):
    set_status(i, ON)
    sleep(3)

def local_action_TurnAllOffDelayed(arg = None):
  '''{ "title" = "Turn all off delayed", "desc" = "Turn all off delayed", "group" = "General" }'''
  print 'Action TurnAllOffDelayed requested.'
  for i in range(1,9):
    set_status(i, OFF)
    sleep(3)

def local_action_GetAllStatus(arg = None):
  '''{ "title" = "Get All Status", "desc" = "Get status of all outlets", "group" = "General" }'''
  print 'Action GetAllStatus requested.'
  for i in range(1,9):
    get_status(i)

def main():
  print "Node started"