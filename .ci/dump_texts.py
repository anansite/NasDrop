import xml.etree.ElementTree as ET
import sys

filename = sys.argv[1] if len(sys.argv) > 1 else 'ui_v187_home.xml'
tree = ET.parse(filename)
for node in tree.iter():
    text = node.attrib.get('text', '')
    desc = node.attrib.get('content-desc', '')
    bounds = node.attrib.get('bounds', '')
    if text or desc:
        print(f"{bounds}: text='{text}' desc='{desc}'")
