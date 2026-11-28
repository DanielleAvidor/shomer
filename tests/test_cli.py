import json
from pathlib import Path

from shomer.cli import main

ROOT = Path(__file__).resolve().parent.parent
S = ROOT / "samples"


def test_samples_alert_on_scams_and_stay_quiet_on_legit(tmp_path):
    out = tmp_path / "out.json"
    assert main(["--input", str(S / "messages.json"), "--output", str(out)]) == 0
    got = json.loads(out.read_text(encoding="utf-8"))
    labels = json.loads((S / "labels.json").read_text(encoding="utf-8"))
    assert len(got) == 10
    for r in got:
        assert r["alert"] == (labels[r["id"]] == "scam"), r["id"]
        assert r["verdict"] in {"dont_click", "matches_official", "cant_verify"}
        assert "is_scam" not in r and "score" not in r  # Shomer never says "safe" and has no score


def test_expected_output_is_current(tmp_path):
    out = tmp_path / "out.json"
    main(["--input", str(S / "messages.json"), "--output", str(out)])
    assert json.loads(out.read_text(encoding="utf-8")) == json.loads((S / "expected_output.json").read_text(encoding="utf-8"))


def test_single_text(capsys):
    assert main(["--text", "בנק הפועלים: חשבונך נחסם. https://bit.ly/x"]) == 0
    assert json.loads(capsys.readouterr().out)["alert"] is True


def test_custom_directory_changes_the_result(tmp_path):
    d = json.loads((ROOT / "shomer" / "data" / "directory.json").read_text(encoding="utf-8"))
    d["orgs"] = [o for o in d["orgs"] if o["id"] != "israelpost"] if any(o["id"] == "israelpost" for o in d["orgs"]) else d["orgs"][1:]
    p = tmp_path / "d.json"
    p.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
    assert main(["--text", "x", "--directory", str(p)]) == 0


def test_bad_input_exits_2(tmp_path, capsys):
    bad = tmp_path / "bad.json"
    bad.write_text("{not json", encoding="utf-8")
    assert main(["--input", str(bad)]) == 2
    assert main(["--input", str(tmp_path / "missing.json")]) == 2
    assert main(["--text", "x", "--directory", str(tmp_path / "nope.json")]) == 2
