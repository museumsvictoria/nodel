import re
pattern = r"^((.*):.*)"
prog = re.compile(pattern)

def find(orig, substr, infile, outfile):
  with open(infile) as a, open(outfile, 'ab+') as b:
    for line in a:
      r = prog.match(line)
      if r and r.group(2) == substr:
        b.write(line)
        return True
  with open(outfile, 'a+') as b:
    print 'not found: ' + substr
    b.write(orig + '\n')
 
with open('variables.src.less') as x:
  with open('variables.less', 'wb+') as b:
    pass
  for line in x:
    r = prog.match(line)
    if(r):
      find(r.group(0), r.group(2), 'variables.orig.less', 'variables.less')
    else:
      with open('variables.less', 'ab+') as b:
        b.write(line)