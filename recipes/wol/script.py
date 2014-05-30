# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node demonstrates a simple PyNode.'''

import struct, socket, re

# Functions used by this node
def sendMagicPacket(dst_mac_addr):
    if not re.match("[0-9a-f]{2}([:])[0-9a-f]{2}(\\1[0-9a-f]{2}){4}$", dst_mac_addr.lower()):
      raise ValueError('Incorrect MAC address format')
    addr_byte = dst_mac_addr.upper().split(':')
    hw_addr = struct.pack('BBBBBB', int(addr_byte[0], 16), int(addr_byte[1], 16), int(addr_byte[2], 16), int(addr_byte[3], 16), int(addr_byte[4], 16), int(addr_byte[5], 16))
    macpck = '\xff' * 6 + hw_addr * 16
    scks = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    scks.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    scks.sendto(macpck, ('<broadcast>', 9))

# Local actions this Node provides
def local_action_TurnOn(arg = None):
  """{"title":"Turns on","desc":"Turns this node on.","group":"Power"}"""
  print 'Action TurnOn requested'
  sendMagicPacket(param_macAddress)

# Parameters used by this Node ...
param_macAddress = Parameter('{"desc":"The MAC address to wake up.","schema":{"type":"string"},"value":"ff:ff:ff:ff:ff:ff"}')

def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'