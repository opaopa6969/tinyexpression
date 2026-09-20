import sys,re,collections
txt=open(sys.argv[1]).read(); events=txt.split('jdk.ExecutionSample')[1:]
top=collections.Counter(); grp=collections.Counter(); incl=collections.Counter(); n=0
TX=('org.unlaxer.context.Transaction.begin','org.unlaxer.context.Transaction.commit','org.unlaxer.context.Transaction.rollback','org.unlaxer.context.ParseContext.checkpointTransactionalState','org.unlaxer.context.ParseContext.finishTransactionalState','org.unlaxer.TransactionElement.','org.unlaxer.ParserCursor.<init>','org.unlaxer.context.ParseContext.recordMemoTransaction')
TOKEN=('org.unlaxer.parser.CollectingParser.collect','org.unlaxer.Token.','org.unlaxer.TokenList.','org.unlaxer.StringSource','org.unlaxer.context.ParseContext.onCommit','org.unlaxer.context.ParseContext.onRollback','org.unlaxer.context.ParseContext.onBegin','org.unlaxer.context.TransactionListenerContainer')
DIAG=('org.unlaxer.context.ParseContext.trackCursorProgress','org.unlaxer.context.ParseContext.registerFailureCandidate','org.unlaxer.context.ParseContext.snapshotStackElements','org.unlaxer.context.ParseContext.expectedHintCandidatesFor','org.unlaxer.context.ParseContext.collectExpectedCandidatesIterative','org.unlaxer.context.ParseContext.deepestTerminalHintCandidate','org.unlaxer.context.ParseContext.addExpectedHint','org.unlaxer.context.ParseContext.mergeFailureDiagnostic','org.unlaxer.context.ParseContext.replayFailureDiagnostic','org.unlaxer.context.ParseContext.localStackSnapshot','org.unlaxer.context.PackratMemoTable','org.unlaxer.context.ParseContext.newExpectedHintCandidate','org.unlaxer.context.ParseContext.expectedCandidateKey','org.unlaxer.context.ParseContext.isValidExpectedHintCandidate','org.unlaxer.context.ParseContext.shouldExpandForExpected')
MEMO=('org.unlaxer.context.PackratMemoTable',)
for e in events:
    frames=[f.split('(')[0] for f in re.findall(r'^\s+([\w.$<>]+\([^)]*\)) line: \d+',e,re.M)]
    if not frames: continue
    n+=1; top[frames[0]]+=1
    tag='other(parser dispatch etc.)'
    for i,f in enumerate(frames):
        if f.startswith(DIAG): tag='diagnostics (hint collection / snapshots / memo replay)'; break
        if f.startswith(TOKEN): tag='commit token collection / listeners'; break
        if f.startswith(TX): tag='transaction bookkeeping'; break
    grp[tag]+=1
    seen=set()
    for f in frames:
        if f.startswith('org.unlaxer') and f not in seen: incl[f]+=1; seen.add(f)
print(f"samples={n}")
for k,v in grp.most_common(): print(f"{100*v/n:6.2f}%  {k}")
print("--- top self frames ---")
for k,v in top.most_common(16): print(f"{100*v/n:6.2f}%  {k}")
print("--- inclusive (org.unlaxer frames within depth 8) ---")
for k,v in incl.most_common(16): print(f"{100*v/n:6.2f}%  {k}")
