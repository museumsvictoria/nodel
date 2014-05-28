# Copyright (c) 2014Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node provides raspberry pi controls.'''

import subprocess

def shutdown():
  returncode = subprocess.call("sudo shutdown -h now", shell=True)

def mute():
  returncode = subprocess.call("sudo amixer cset numid=2 0", shell=True)

def unmute():
  returncode = subprocess.call("sudo amixer cset numid=2 1", shell=True)

def set_volume(vol):
  returncode = subprocess.call("sudo amixer cset numid=1 "+str(vol)+"%", shell=True)

# Local actions this Node provides
def local_action_TurnOff(arg = None):
  """{"title":"Turn off","desc":"Turns this computer off.","group":"Power"}"""
  print 'Action TurnOff requested'
  shutdown()

def local_action_mute(arg = None):
  """{"title":"Mute","desc":"Mute this computer.","group":"Volume"}"""
  print 'Action Mute requested'
  mute()

def local_action_unmute(arg = None):
  """{"title":"Unmute","desc":"Un-mute this computer.","group":"Volume"}"""
  print 'Action Unmute requested'
  unmute()

def local_action_SetVolume(arg = None):
  """{"title":"Set volume","desc":"Set volume.","schema":{"title":"Level","type":"integer","required":"true"},"group":"Volume"}"""
  print 'Action SetVolume requested - '+str(arg)
  set_volume(arg)

def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'
