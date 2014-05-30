# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

import pjlink
import socket
import sys

def main():
  return True

param_ipAddress = Parameter('{ "name": "ipAddress", "schema": {"type": "string"}, "value": "192.168.1.21" }')
param_port = Parameter('{ "name": "port", "schema": {"type": "integer"}, "value": 4352 }')
param_password = Parameter('{ "name": "password", "schema": {"type": "string"}, "value": "mitsubishi" }')

local_event_PowerState = LocalEvent('{ "title": "Power state", "desc": "Current power state." }')
local_event_InputState = LocalEvent('{ "title": "Input state", "desc": "Current input state." }')
local_event_Error = LocalEvent('{ "title": "Error", "desc": "Error returned." }')

def local_action_PowerOn(x = None):
  '''{ "title": "Turn on", "desc": "Turns projector on.", "group": "Power" }'''
  p = get_projector()
  if(p):
    try:
      p.set_power('on')
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_PowerOff(x = None):
  '''{ "title": "Turn off", "desc": "Turns the projector off.", "group": "Power" }'''
  p = get_projector()
  if(p):
    try:
      p.set_power('off')
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_GetPower(x = None):
  '''{ "title": "Get power", "desc": "Get power state of projector.", "group": "Power" }'''
  p = get_projector()
  if(p):
    try:
      pwr = p.get_power()
      local_event_PowerState.emit(pwr)
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_SetInput(arg):
  '''{ "title": "Set input", "desc": "Set projector input.", "group": "Inputs", "schema": { "type":"object", "required":true, "title": "Input", "properties":{ "number": { "type":"integer", "title": "Number", "required":true }, "source": { "type":"string", "title": "Source", "required":true } } } }'''
  p = get_projector()
  if(p):
    try:
      p.set_input(arg['source'], arg['number'])
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_GetInput(x = None):
  '''{ "title": "Get input", "desc": "Get current input.", "group": "Inputs" }'''
  p = get_projector()
  if(p):
    try:
      inp = p.get_input()
      local_event_InputState.emit(inp)
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_Mute(what):
  '''{ "title": "Mute", "desc": "Mute.", "schema": { "title": "What", "type": "string", "required": true, "enum" : ["video", "audio", "all"] }, "group": "Mute" }'''
  p = get_projector()
  if(p):
    try:
      what = { 'video': 1, 'audio': 2, 'all': 3, }[what]
      p.set_mute(what, True)
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_Unmute(what):
  '''{ "title": "Unmute", "desc": "Unmute.", "schema": { "title": "What", "type": "string", "required": true, "enum" : ["video", "audio", "all"] }, "group": "Mute" }'''
  p = get_projector()
  if(p):
    try:
      what = { 'video': 1, 'audio': 2, 'all': 3, }[what]
      p.set_mute(what, False)
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_Lamps(x = None):
  '''{ "title": "Lamp info", "desc": "Get lamp info.", "group": "Information" }'''
  p = get_projector()
  if(p):
    try:
      for i, (time, state) in enumerate(p.get_lamps()):
          print 'Lamp %d: %s (%d hours)' % (i+1, 'on' if state else 'off', time)
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def local_action_Errors(x = None):
  '''{ "title": "Error info", "desc": "Get projector error info.", "group": "Information" }'''
  p = get_projector()
  if(p):
    try:
      for what, state in p.get_errors().items():
          print '%s: %s' % (what, state)
    except Exception, e:
      local_event_Error.emit(e)
    finally:
      p.f.close()

def get_projector():
  try:
    sock = socket.socket()
    sock.connect((param_ipAddress, param_port))
    f = sock.makefile()
    proj = pjlink.Projector(f)
    rv = proj.authenticate(lambda: param_password)
    if(rv or rv is None):
      return proj
    else:
      local_event_Error.emit('authentication error')
      return False
  except:
    local_event_Error.emit('connection error')
    return False
  finally:
    sock.close()
