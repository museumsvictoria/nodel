# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This is an APC controller node'''

import sys
import telnetlib
import re

ON = 1
OFF = 2

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

param_ipAddress = Parameter('{ "name": "ipAddress", "schema": {"type": "string"}, "value": "192.168.1.20" }')

def main():
  return True

def controlAPC(args):
  timeout = 2
  try:
    con = telnetlib.Telnet(args[0])
    con.read_until('User Name :', timeout)
    con.write('apc\r\n')
    con.read_until('Password  :', timeout)
    con.write('apc\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('1\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('2\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('1\n\r')
    con.read_until('\r\n> ', timeout)
    con.write(str(args[1])+'\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('1\n\r')
    con.read_until('\r\n> ', timeout)
    con.write(str(args[2])+'\n\r')
    con.read_until('to cancel : ', timeout)
    con.write('YES\n\r')
    con.read_until('...', timeout)
    con.close()
    return True
  except Exception, e:
    local_event_Error.emit(e)
    return False

def statusAPC(ip):
  _status_re = re.compile("([1-9])-.*(ON|OFF)", re.VERBOSE)
  timeout = 2
  try:
    con = telnetlib.Telnet(ip)
    con.read_until('User Name :', timeout)
    con.write('apc\r\n')
    con.read_until('Password  :', timeout)
    con.write('apc\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('1\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('2\n\r')
    con.read_until('\r\n> ', timeout)
    con.write('1\n\r')
    out = con.read_until('\r\n> ', timeout)
    result = []
    con.close()
    return [match.group(2) for match in _status_re.finditer(out)]
  except:
    return False

def local_action_Turn1On(x):
  '''{ "title" = "Turn 1 on", "desc" = "Turn 1 on", "group" = "Outlet 1" }'''
  print 'Action Turn1On requested.'
  if(controlAPC([param_ipAddress,1,ON])==True):
    local_event_Outlet1On.emit(x)

def local_action_Turn1Off(x):
  '''{ "title" = "Turn 1 off", "desc" = "Turn 1 off", "group" = "Outlet 1" }'''
  print 'Action Turn1Off requested.'
  if(controlAPC([param_ipAddress,1,OFF])==True):
    local_event_Outlet1Off.emit(x)

def local_action_Turn2On(x):
  '''{ "title" = "Turn 2 on", "desc" = "Turn 2 on", "group" = "Outlet 2" }'''
  print 'Action Turn2On requested.'
  if(controlAPC([param_ipAddress,2,ON])==True):
    local_event_Outlet2On.emit(x)

def local_action_Turn2Off(x):
  '''{ "title" = "Turn 2 off", "desc" = "Turn 2 off", "group" = "Outlet 2" }'''
  print 'Action Turn2Off requested.'
  if(controlAPC([param_ipAddress,2,OFF])==True):
    local_event_Outlet2Off.emit(x)

def local_action_Turn3On(x):
  '''{ "title" = "Turn 3 on", "desc" = "Turn 3 on", "group" = "Outlet 3" }'''
  print 'Action Turn3On requested.'
  if(controlAPC([param_ipAddress,3,ON])==True):
    local_event_Outlet3On.emit(x)

def local_action_Turn3Off(x):
  '''{ "title" = "Turn 3 off", "desc" = "Turn 3 off", "group" = "Outlet 3" }'''
  print 'Action Turn3Off requested.'
  if(controlAPC([param_ipAddress,3,OFF])==True):
    local_event_Outlet3Off.emit(x)

def local_action_Turn4On(x):
  '''{ "title" = "Turn 4 on", "desc" = "Turn 4 on", "group" = "Outlet 4" }'''
  print 'Action Turn4On requested.'
  if(controlAPC([param_ipAddress,4,ON])==True):
    local_event_Outlet4On.emit(x)

def local_action_Turn4Off(x):
  '''{ "title" = "Turn 4 off", "desc" = "Turn 4 off", "group" = "Outlet 4" }'''
  print 'Action Turn4Off requested.'
  if(controlAPC([param_ipAddress,4,OFF])==True):
    local_event_Outlet4Off.emit(x)

def local_action_Turn5On(x):
  '''{ "title" = "Turn 5 on", "desc" = "Turn 5 on", "group" = "Outlet 5" }'''
  print 'Action Turn5On requested.'
  if(controlAPC([param_ipAddress,5,ON])==True):
    local_event_Outlet5On.emit(x)

def local_action_Turn5Off(x):
  '''{ "title" = "Turn 5 off", "desc" = "Turn 5 off", "group" = "Outlet 5" }'''
  print 'Action Turn5Off requested.'
  if(controlAPC([param_ipAddress,5,OFF])==True):
    local_event_Outlet5Off.emit(x)

def local_action_Turn6On(x):
  '''{ "title" = "Turn 6 on", "desc" = "Turn 6 on", "group" = "Outlet 6" }'''
  print 'Action Turn6On requested.'
  if(controlAPC([param_ipAddress,6,ON])==True):
    local_event_Outlet6On.emit(x)

def local_action_Turn6Off(x):
  '''{ "title" = "Turn 6 off", "desc" = "Turn 6 off", "group" = "Outlet 6" }'''
  print 'Action Turn6Off requested.'
  if(controlAPC([param_ipAddress,6,OFF])==True):
    local_event_Outlet6Off.emit(x)

def local_action_Turn7On(x):
  '''{ "title" = "Turn 7 on", "desc" = "Turn 7 on", "group" = "Outlet 7" }'''
  print 'Action Turn7On requested.'
  if(controlAPC([param_ipAddress,7,ON])==True):
    local_event_Outlet7On.emit(x)

def local_action_Turn7Off(x):
  '''{ "title" = "Turn 7 off", "desc" = "Turn 7 off", "group" = "Outlet 7" }'''
  print 'Action Turn7Off requested.'
  if(controlAPC([param_ipAddress,7,OFF])==True):
    local_event_Outlet7Off.emit(x)

def local_action_Turn8On(x):
  '''{ "title" = "Turn 8 on", "desc" = "Turn 8 on", "group" = "Outlet 8" }'''
  print 'Action Turn8On requested.'
  if(controlAPC([param_ipAddress,8,ON])==True):
    local_event_Outlet8On.emit(x)

def local_action_Turn8Off(x):
  '''{ "title" = "Turn 8 off", "desc" = "Turn 8 off", "group" = "Outlet 8" }'''
  print 'Action Turn8Off requested.'
  if(controlAPC([param_ipAddress,8,OFF])==True):
    local_event_Outlet8Off.emit(x)