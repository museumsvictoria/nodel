# Copyright (c) 2014Museum Victoria
# This software is released under the MIT license (see license.txt for details)

'''This node provides mac/pc controls.'''

import java.lang.System
import subprocess

system = java.lang.System.getProperty('os.name')
arch = java.lang.System.getProperty('sun.arch.data.model').lower()

def shutdown():
  if((system=="Windows 7") or (system=="Windows 8")):
    # shutdown WIN
    returncode = subprocess.call("shutdown -s -f -t 0", shell=True)
  elif(system=="Mac OS X"):
    # shutdown OSX
    # nodel process must have sudo rights to shutdown command
    returncode = subprocess.call("sudo shutdown -h -u now", shell=True)
  else:
    print 'unknown system: ' + system

def mute():
  if((system=="Windows 7") or (system=="Windows 8")):
    returncode = subprocess.call("nircmd"+arch+".exe mutesysvolume 1", shell=True)
  elif(system=="Mac OS X"):
    returncode = subprocess.call("osascript -e 'set volume output muted true'", shell=True)
  else:
    print 'unknown system: ' + system

def unmute():
  if((system=="Windows 7") or (system=="Windows 8")):
    returncode = subprocess.call("nircmd"+arch+".exe mutesysvolume 0", shell=True)
    print returncode
  elif(system=="Mac OS X"):
    returncode = subprocess.call("osascript -e 'set volume output muted false'", shell=True)
  else:
    print 'unknown system: ' + system

def set_volume(vol):
  if((system=="Windows 7") or (system=="Windows 8")):
    winvol = (65535/100)*vol
    returncode = subprocess.call("nircmd"+arch+".exe setsysvolume "+str(winvol), shell=True)
  elif(system=="Mac OS X"):
    returncode = subprocess.call("osascript -e 'set volume output volume "+str(vol)+"'", shell=True)
    # raspberry pi volume: "amixer cset numid=1 -- 20%"
    # returncode = subprocess.call("amixer cset numid=1 -- "+str(vol)+"%", shell=True)
  else:
    print 'unknown system: ' + system

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
