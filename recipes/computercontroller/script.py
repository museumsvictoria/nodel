# Copyright (c) 2014 Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node provides mac/pc controls.'''

import java.lang.System
import subprocess

system = java.lang.System.getProperty('os.name')

def shutdown():
  if(system=="Windows 7"):
    # shutdown pc
    returncode = subprocess.call("shutdown -s -f -t 0", shell=True)
  elif(system=="Mac OS X"):
    # sleep osx
    # nodel process must have sudo rights to shutdown command
    returncode = subprocess.call("sudo shutdown -h now", shell=True)

def mute():
  if(system=="Windows 7"):
    returncode = subprocess.call("nircmd.exe mutesysvolume 1", shell=True)
  elif(system=="Mac OS X"):
    returncode = subprocess.call("osascript -e 'set volume output muted true'", shell=True)

def unmute():
  if(system=="Windows 7"):
    returncode = subprocess.call("nircmd.exe mutesysvolume 0", shell=True)
    print returncode
  elif(system=="Mac OS X"):
    returncode = subprocess.call("osascript -e 'set volume output muted false'", shell=True)

def set_volume(vol):
  if(system=="Windows 7"):
    winvol = (65535/100)*vol
    returncode = subprocess.call("nircmd.exe setsysvolume "+str(winvol), shell=True)
  elif(system=="Mac OS X"):
    returncode = subprocess.call("osascript -e 'set volume output volume "+str(vol)+"'", shell=True)
    
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