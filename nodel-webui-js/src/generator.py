from xml.etree.ElementTree import Element, SubElement, tostring
from xml.dom.minidom import parseString
import json
import urllib2
url = "http://192.168.178.49:8087/REST/nodes/lumicomofficedashboard/events/controls"
result = json.load(urllib2.urlopen(url))

root = Element('pages',{'title':'Status Page'})
for location in result['arg']:
  loc = SubElement(root, "page", {'title':location['title']})
  row = SubElement(loc, "row")
  for group in location['groups']:
    col = SubElement(row, "column", {"sm":"6","md":"4"})
    ttlt = SubElement(col, "title")
    ttlt.text = group['title']
    for control in group['controls']:
      stac = SubElement(col, "status", {"event":control['control']})
      stac.text = control['title']

rough_string = tostring(root, 'utf-8')
reparsed = parseString(rough_string)
pi = reparsed.createProcessingInstruction('xml-stylesheet', 'type="text/xsl" href="index.xsl"')
reparsed.insertBefore(pi, reparsed.firstChild)

target = open('status.xml', 'w')
target.truncate()
target.write(reparsed.toprettyxml(indent="  ", encoding="utf-8"))
target.close()