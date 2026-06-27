import zipfile
import xml.etree.ElementTree as ET
import re

# docx is a zip file containing XML
with zipfile.ZipFile(r'I:\佛经\个人精选\佛说圣佛母般若波罗蜜多经.docx', 'r') as z:
    with z.open('word/document.xml') as f:
        tree = ET.parse(f)
        root = tree.getroot()
        
        # Extract text from paragraphs
        ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
        paragraphs = []
        for p in root.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
            texts = []
            for t in p.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'):
                if t.text:
                    texts.append(t.text)
            line = ''.join(texts).strip()
            if line:
                paragraphs.append(line)

result = '\n\n'.join(paragraphs)
print(result)
print(f"\n\n--- TOTAL LENGTH: {len(result)} chars ---")
