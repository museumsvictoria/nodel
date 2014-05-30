# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''PIVoD MVMS Gen II Show Control Lite Node'''

### Libraries required by this Node
import socket



### Parameters used by this Node
PORT = 9099
param_ipAddress = Parameter('{ "desc" : "IP address of Show Control Lite process", "schema" : { "type" : "string" } }')



### Functions used by this Node
def callOutcome(outcome):
  try:
    # initialise a UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    # bind to arbitrary socket
    sock.bind(('0.0.0.0', 0))
    # must not block forever waiting for response
    sock.settimeout(5)
    # local interface and port
    interface, returnPort = sock.getsockname()
    # get the local address (NOTE, this could be wrong on multihomed hosts)
    returnAddress = socket.gethostbyname(socket.gethostname())
    message = 'executeOutcome %s %s %s' % (outcome, returnAddress, returnPort)
    # send the message
    sock.sendto(message, (param_ipAddress, PORT))
    # receive a response
    data, addr = sock.recvfrom(512)
    print 'Received acknowledgement:', data
    if 'OK' in data:
      local_event_Success.emit()
    elif 'NOT_FOUND' in data:
      local_event_Error.emit('Outcome not found')
    else:
      local_event_Error.emit(('Unexpected response', data))
  except Exception, e:
    # probably a timeout but pass on exception regardless
    local_event_Error.emit(e)
  finally:
    sock.close()



### Local actions this Node provides
def local_action_AllOn(arg = None):
  '''{ "title" : "AllOn", "desc" : "Calls an AllOn outcome" }'''
  callOutcome('AllOn')

def local_action_AllOff(arg = None):
  '''{ "title" : "AllOff", "desc" : "Calls an AllOff outcome" }'''
  callOutcome('AllOff')

def local_action_CallOutcome(arg):
  '''{ "title" : "Call outcome", "desc" : "Calls an outcome", "schema" : { "type" : "string", "title"  : "Outcome" } }'''
  callOutcome(arg)



### Local events this Node provides
local_event_Error = LocalEvent('{ "title" : "Error", "desc" : "Request failed" }')
local_event_Success = LocalEvent('{ "title" : "Success", "desc" : "Request was successful" }')



def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'
