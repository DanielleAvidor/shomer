import json, sys
w = sys.argv[1]
py = [json.loads(l) for l in open(f"{w}/py.jsonl", encoding="utf-8")]
kt = [json.loads(l) for l in open(f"{w}/kt.jsonl", encoding="utf-8")]
ins = [json.loads(l) for l in open(f"{w}/in.jsonl", encoding="utf-8")]
bad = [(i, k) for i, (a, b) in enumerate(zip(py, kt)) for k in a if a[k] != b[k]]
for i, k in bad[:10]:
    print("DIFF", k, repr(ins[i])[:80], py[i][k], kt[i][k])
print(f"{len(py)} messages, {len(bad)} differences between the Python and Kotlin engines")
sys.exit(1 if bad or len(py) != len(kt) else 0)
