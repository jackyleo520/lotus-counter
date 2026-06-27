import docx

doc = docx.Document(r'I:\佛经\个人精选\佛说圣佛母般若波罗蜜多经.docx')
text = '\n'.join([p.text for p in doc.paragraphs])
print(text)
