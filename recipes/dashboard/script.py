# Nodel auto-generated example Python script that applies to version v2.0.4 or later.
'''The dashboard will display any node with an alert or watched value.'''

# Parameters used by this Node
param_alert = Parameter('{"title": "Actions/events to be alerted to", "schema":{"type":"array","items":{"type":"object","properties":{"alias":{"title":"Alias","type":"string","required":true,"description":"The error actions/events to watch for"}}}}}')
param_watch = Parameter('{"title": "Actions/events to watch", "schema":{"type":"array","items":{"type":"object","properties":{"alias":{"title":"Alias","type":"string","required":true,"description":"The conditions to report on"}}}}}')
param_filter = Parameter('{"title": "Node Filter", "schema":{"type":"array","items":{"type":"object","title":"Group","properties":{"title":{"type":"string","title":"Title","description":"The title of the group."},"filter":{"type":"string","title":"Filter","description":"Filter nodes that begin with this value."}}}}}}')

def main(arg = None):
  # Start your script here.
  print 'Nodel script started.'

