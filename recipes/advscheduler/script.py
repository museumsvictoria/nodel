'''Scheduler Node'''

### Libraries required by this Node
import logging
import atexit
from apscheduler.scheduler import Scheduler
import re
import itertools
from datetime import date


### Parameters used by this Node
param_schedule = Parameter('{ "title" : "Schedule", "group" : "Schedule", "schema": { "type": "array", "title": "Schedule", "required": false, "items": { "type": "object", "required": false, "properties": { "cron": { "type": "string", "format": "cron", "required": true, "title": "Cron", "desc": "Format: <minute> <hour> <day> <month> <day of week>" }, "signal": { "type": "string", "required": true, "title": "Signal", "format": "event" }, "except": { "type":"array", "required":false, "title":"Except", "items": { "type":"object", "required":false, "properties":{ "date": { "type":"string", "format":"date", "title":"Date", "required":false } } } }, "note": { "type": "string", "format": "long", "required": false, "title": "Notes", "desc": "Schedule notes" } } } } }')



logging.basicConfig()
# 10 second grace time on jobs
sched = Scheduler(misfire_grace_time=10)

def cleanup():
  sched.shutdown()

atexit.register(cleanup)

_split_re  = re.compile("\s+")
_cron_re = re.compile(r"^(?:[0-9-,*/]+\s){4}[0-9-,*/]+$")
_sched_seq = ('minute', 'hour', 'day', 'month', 'day_of_week')



### Local events this Node provides
local_event_AllOn = LocalEvent('{ "title" : "AllOn", "group" : "Input" }')
local_event_AllOff = LocalEvent('{ "title" : "AllOff", "group" : "Input" }')
local_event_MuteOn = LocalEvent('{ "title" : "MuteOn", "group" : "Input" }')
local_event_MuteOff = LocalEvent('{ "title" : "MuteOff", "group" : "Input" }')
local_event_Error = LocalEvent('{ "title" : "Error", "group" : "General" }')

def callevent(event, dateexcept):
  dateexcept = dateexcept.split(",")
  dates = [date(*[int(y) for y in x.split('-')]) for x in dateexcept if x]
  if(not date.today() in dates):
    print 'calling: '+event
    try:
      globals()["local_event_"+event].emit()
    except:
      print 'error calling: '+event
      local_event_Error.emit('error calling: '+event)
  else:
    print 'excluding: '+event



### Main
def main():
  sched.start()
  if(param_schedule):
    for schedule in param_schedule:
      if not _cron_re.match(schedule['cron']):
        local_event_Error.emit('Invalid cron string')
        print 'Invalid cron string'
      else:
        split = _split_re.split(schedule['cron'])
        cron = dict(itertools.izip(_sched_seq, split))
        event = schedule['signal']
        if('except' in schedule):
          dates = ",".join([x['date'] for x in schedule['except'] if 'date' in x])
        else:
          dates = ''
        #print dates
        expr = "sched.add_cron_job(callevent, args=['"+event+"','"+dates+"'], **cron)"
        #print 'expr:', expr
        eval(expr)
  print 'Nodel script started.'