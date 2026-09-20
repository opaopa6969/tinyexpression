import sys,re,collections
txt=open(sys.argv[1]).read()
events=txt.split('jdk.ObjectAllocationSample')
bycls=collections.Counter(); site=collections.Counter(); frame=collections.Counter(); total=0; n=0
FRAME_CLASSES={'org.unlaxer.TransactionElement','org.unlaxer.ParserCursor','org.unlaxer.EndExclusiveCursorImpl','org.unlaxer.TokenList','org.unlaxer.CodePointIndex'}
FRAME_PATHS=('org.unlaxer.TransactionElement.','org.unlaxer.ParserCursor.','org.unlaxer.TokenList.<init>','org.unlaxer.context.Transaction.begin','org.unlaxer.context.Transaction.commit','org.unlaxer.context.Transaction.rollback','org.unlaxer.context.ParseContext.checkpointTransactionalState','org.unlaxer.context.ParseContext.finishTransactionalState')
for e in events[1:]:
    m=re.search(r'weight = ([\d.]+) ([kMG]?B)',e)
    if not m: continue
    w=float(m.group(1))*{'B':1,'kB':1e3,'MB':1e6,'GB':1e9}[m.group(2)]
    cls=re.search(r'objectClass = ([^\s]+)',e).group(1)
    frames=[f.split('(')[0] for f in re.findall(r'^\s+([\w.$<>]+\([^)]*\)) line: \d+',e,re.M)]
    total+=w; n+=1
    bycls[cls]+=w
    site[(cls,)+tuple(frames[:3])]+=w
    if cls in FRAME_CLASSES: frame['object:'+cls]+=w
    elif any(f.startswith(FRAME_PATHS) for f in frames[:4]):
        frame['path:'+next(f for f in frames if f.startswith('org.unlaxer'))+'<-'+cls]+=w
print(f"samples={n} total_weight={total/1e9:.2f}GB")
print("## by object class"); [print(f"{100*v/total:6.2f}%  {k}") for k,v in bycls.most_common(12)]
print("## by site (class <- top 3 frames)"); [print(f"{100*v/total:6.2f}%  {k}") for k,v in site.most_common(16)]
fs=sum(frame.values()); print(f"## transaction-frame related total: {100*fs/total:.2f}%"); [print(f"{100*v/total:6.2f}%  {k}") for k,v in frame.most_common(12)]
