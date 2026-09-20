"""Run the confirmed legal golden cases against the local assessment endpoint."""
import argparse
import json
import math
import pathlib
import time
import urllib.error
import urllib.request

import yaml


def request_json(url, token, payload, timeout):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Safeguard-Service-Token": token,
        },
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def ranked_stage_ids(row, stage_name):
    """Preserve trace order while merging multiple rewritten sub-question traces."""
    seen = set()
    ranked = []
    for sub_question in (row.get("trace") or {}).get("retrievalTraces") or []:
        for stage in sub_question.get("stages") or []:
            if stage.get("stage") != stage_name:
                continue
            for chunk_id in stage.get("chunkIds") or []:
                if chunk_id and chunk_id not in seen:
                    seen.add(chunk_id)
                    ranked.append(chunk_id)
    return ranked


def ranked_ids(row, stage_name):
    if stage_name == "FINAL_EVIDENCE":
        return list(dict.fromkeys(row.get("returnedChunkIds") or []))
    return ranked_stage_ids(row, stage_name)


def first_gold_rank(ranked, gold_ids, cutoff):
    for index, chunk_id in enumerate(ranked[:cutoff], 1):
        if chunk_id in gold_ids:
            return index
    return None


def average(values):
    return sum(values) / len(values) if values else None


def ranking_metrics(rows, stage_name):
    """Metrics for answerable single-evidence-style rows."""
    if not rows:
        return None
    hit_at_1 = []
    hit_at_5 = []
    recall_at_5 = []
    mrr_at_5 = []
    mrr_at_20 = []
    for row in rows:
        ranked = ranked_ids(row, stage_name)
        gold_ids = set(row.get("goldChunkIds") or [])
        top_5 = set(ranked[:5])
        rank_5 = first_gold_rank(ranked, gold_ids, 5)
        rank_20 = first_gold_rank(ranked, gold_ids, 20)
        hit_at_1.append(bool(ranked and ranked[0] in gold_ids))
        hit_at_5.append(bool(top_5.intersection(gold_ids)))
        recall_at_5.append(len(top_5.intersection(gold_ids)) / len(gold_ids) if gold_ids else 0)
        mrr_at_5.append(1 / rank_5 if rank_5 else 0)
        mrr_at_20.append(1 / rank_20 if rank_20 else 0)
    return {
        "caseCount": len(rows),
        "hitAt1": average(hit_at_1),
        "goldHitAt5": average(hit_at_5),
        "recallAt5": average(recall_at_5),
        "mrrAt5": average(mrr_at_5),
        "mrrAt20": average(mrr_at_20),
    }


def compound_metrics(rows, stage_name):
    if not rows:
        return None
    all_gold = []
    group_recall = []
    mrr_at_20 = []
    ndcg_at_5 = []
    for row in rows:
        ranked = ranked_ids(row, stage_name)
        top_5 = ranked[:5]
        top_5_set = set(top_5)
        groups = [set(group) for group in row.get("goldEvidenceGroups") or []]
        hits = [bool(group.intersection(top_5_set)) for group in groups]
        all_gold.append(bool(groups) and all(hits))
        group_recall.append(sum(hits) / len(groups) if groups else 0)
        gold_ids = set(row.get("goldChunkIds") or [])
        rank_20 = first_gold_rank(ranked, gold_ids, 20)
        mrr_at_20.append(1 / rank_20 if rank_20 else 0)
        relevance = row.get("goldRelevance") or {}
        dcg = sum(((2 ** relevance.get(chunk_id, 0)) - 1) / math.log2(index + 1)
                  for index, chunk_id in enumerate(top_5, 1))
        ideal_relevance = sorted(relevance.values(), reverse=True)[:5]
        ideal_dcg = sum(((2 ** score) - 1) / math.log2(index + 1)
                        for index, score in enumerate(ideal_relevance, 1))
        ndcg_at_5.append(dcg / ideal_dcg if ideal_dcg else 0)
    return {
        "caseCount": len(rows),
        "allGoldHitAt5": average(all_gold),
        "evidenceGroupRecallAt5": average(group_recall),
        "mrrAt20": average(mrr_at_20),
        "ndcgAt5": average(ndcg_at_5),
        "averageMissingEvidenceGroupsAt5": average([1 - value for value in group_recall]),
    }


def bucket_metrics(successful, stage_name):
    answerable = [row for row in successful if row.get("expectedAnswerability") != "UNANSWERABLE"]
    result = {}
    for bucket in ("SINGLE_CLAUSE", "NUMERIC"):
        result[bucket] = ranking_metrics([row for row in answerable if row.get("bucket") == bucket], stage_name)
    result["COMPOUND"] = compound_metrics([row for row in answerable if row.get("bucket") == "COMPOUND"], stage_name)
    if stage_name == "FINAL_EVIDENCE":
        unanswerable = [row for row in successful if row.get("expectedAnswerability") == "UNANSWERABLE"]
        result["UNANSWERABLE"] = {
            "caseCount": len(unanswerable),
            "correctEmptyEvidenceRate": average([not row.get("returnedChunkIds") for row in unanswerable]),
            "falseEvidenceRate": average([bool(row.get("returnedChunkIds")) for row in unanswerable]),
            "falseCitationRate": average([bool(row.get("returnedChunkIds")) for row in unanswerable]),
        } if unanswerable else None
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", default="rag/src/test/resources/fixtures/legal-retrieval/golden-set-v0.1-seed.yaml")
    parser.add_argument("--output", default="outputs/legal-evaluation-v0.1/report.json")
    parser.add_argument("--base-url", default="http://127.0.0.1:9090/api/safeguard-agent")
    parser.add_argument("--token", default="local-eval-token")
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--start-index", type=int, default=1)
    parser.add_argument("--end-index", type=int, default=0)
    parser.add_argument("--report-input", help="Recalculate metrics from an existing report without calling the endpoint.")
    args = parser.parse_args()

    root = pathlib.Path(__file__).resolve().parents[1]
    manifest = yaml.safe_load((root / args.manifest).read_text(encoding="utf-8"))
    all_cases = manifest["cases"]
    end_index = args.end_index or len(all_cases)
    cases = all_cases[max(0, args.start_index - 1):end_index]
    case_by_id = {case["case_id"]: case for case in cases}

    def enrich_row(row, case):
        groups = [
            {positive["chunk_id"] for positive in group.get("positive_chunks", [])}
            for group in case.get("gold_evidence_groups", [])
        ]
        relevance = {
            positive["chunk_id"]: positive.get("relevance", 0)
            for group in case.get("gold_evidence_groups", [])
            for positive in group.get("positive_chunks", [])
        }
        row.update({
            "bucket": case.get("evaluation_bucket"),
            "sourceType": case.get("source_type"),
            "expectedAnswerability": case.get("answerability"),
            "goldChunkIds": sorted({chunk_id for group in groups for chunk_id in group}),
            "goldEvidenceGroups": [sorted(group) for group in groups],
            "goldRelevance": relevance,
            "hardNegativeChunkIds": [chunk.get("chunk_id") for chunk in case.get("hard_negative_chunks", [])],
            "requiredClaims": case.get("required_claims", []),
        })
        return row

    if args.report_input:
        source = root / args.report_input
        loaded = json.loads(source.read_text(encoding="utf-8"))
        results = [enrich_row(dict(row), case_by_id[row["caseId"]]) for row in loaded.get("results", []) if row.get("caseId") in case_by_id]
    else:
        results = []
        for index, case in enumerate(cases, 1):
            case_id = case["case_id"]
            groups = [
                {positive["chunk_id"] for positive in group.get("positive_chunks", [])}
                for group in case.get("gold_evidence_groups", [])
            ]
            gold_ids = {chunk_id for group in groups for chunk_id in group}
            payload = {
                "hazardDescription": case["question"],
                "executionContext": {
                    "actorUserId": 1,
                    "enterpriseId": 1,
                    "projectId": 1,
                    "teamId": 1,
                    "sourceHazardId": f"legal-golden:{case_id}",
                    "traceId": f"legal-golden:{case_id}",
                },
            }
            started = time.perf_counter()
            row = enrich_row({"caseId": case_id, "question": case["question"]}, case)
            try:
                response = request_json(f"{args.base_url}/agent/hazard-assessment", args.token, payload, args.timeout)
                evidence = response.get("evidence") or []
                returned_ids = [item.get("chunkId") for item in evidence if item.get("chunkId")]
                trace = response.get("trace") or {}
                row.update(
                    {
                        "status": "OK",
                        "elapsedMs": round((time.perf_counter() - started) * 1000),
                        "returnedChunkIds": returned_ids,
                        "hitFinalEvidence": bool(gold_ids.intersection(returned_ids)),
                        "groupHit": [bool(group.intersection(returned_ids)) for group in groups],
                        "allEvidenceGroupsHit": all(group.intersection(returned_ids) for group in groups) if groups else case.get("answerability") == "UNANSWERABLE" and not returned_ids,
                        "falseCitationForUnanswerable": case.get("answerability") == "UNANSWERABLE" and bool(returned_ids),
                        "trace": trace,
                        "riskLevel": response.get("riskLevel"),
                        "answer": response.get("riskExplanation"),
                    }
                )
            except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, ValueError) as error:
                row.update({"status": "ERROR", "elapsedMs": round((time.perf_counter() - started) * 1000), "error": str(error)})
            results.append(row)
            print(f"[{index}/{len(cases)}] {case_id} {row['status']} {row.get('returnedChunkIds', [])}", flush=True)

    successful = [row for row in results if row["status"] == "OK"]
    answerable = [row for row in successful if row["expectedAnswerability"] != "UNANSWERABLE"]
    compounds = [row for row in successful if row["bucket"] == "COMPOUND"]

    def stage_ids(row, stage_name):
        return set(ranked_stage_ids(row, stage_name))

    stage_names = ["RAW_RECALL", "Deduplication", "Fusion", "Rerank", "LexicalCoverage", "EvidenceGate"]
    stage_hit_rates = {}
    compound_stage_rates = {}
    for stage_name in stage_names:
        answerable_stage_rows = [row for row in answerable if row.get("status") == "OK"]
        values = [
            bool(set(row.get("goldChunkIds") or []).intersection(stage_ids(row, stage_name)))
            for row in answerable_stage_rows
        ]
        stage_hit_rates[stage_name] = sum(values) / len(values) if values else None
        compound_values = []
        for row in compounds:
            ids = stage_ids(row, stage_name)
            # Reconstruct group-level coverage from the manifest-derived gold groups stored below.
            gold_groups = row.get("goldEvidenceGroups") or []
            if gold_groups:
                compound_values.append(all(set(group).intersection(ids) for group in gold_groups))
        compound_stage_rates[stage_name] = (
            sum(compound_values) / len(compound_values) if compound_values else None
        )
    bucket_metrics_by_stage = {
        stage_name: bucket_metrics(successful, stage_name)
        for stage_name in ["RAW_RECALL", "Fusion", "Rerank", "LexicalCoverage", "EvidenceGate", "FINAL_EVIDENCE"]
    }
    report = {
        "dataset": manifest["dataset"],
        "endpoint": args.base_url,
        "caseCount": len(cases),
        "successfulCaseCount": len(successful),
        "errorCaseCount": len(results) - len(successful),
        "metrics": {
            "finalEvidenceHitRate": sum(row.get("hitFinalEvidence", False) for row in answerable) / len(answerable) if answerable else None,
            "compoundAllEvidenceGroupsHitRate": sum(row.get("allEvidenceGroupsHit", False) for row in compounds) / len(compounds) if compounds else None,
            "unanswerableFalseCitationRate": sum(row.get("falseCitationForUnanswerable", False) for row in successful if row["expectedAnswerability"] == "UNANSWERABLE") / max(1, sum(row["expectedAnswerability"] == "UNANSWERABLE" for row in successful)),
            "averageElapsedMs": sum(row.get("elapsedMs", 0) for row in successful) / len(successful) if successful else None,
            "stageHitRates": stage_hit_rates,
            "compoundStageAllEvidenceGroupsHitRates": compound_stage_rates,
            "bucketMetricsByStage": bucket_metrics_by_stage,
        },
        "notes": [
            "bucketMetricsByStage uses ordered, de-duplicated chunk IDs from each retrieval trace; compound rows merge rewritten sub-question traces in trace order.",
            "FINAL_EVIDENCE is an ordered evidence list rather than a retrieval Top-K list; its ranking metrics are diagnostic only.",
            "Generation metrics (claim correctness, faithfulness, citation alignment, numeric exactness) require a separate per-claim judge and are intentionally not inferred from retrieval evidence.",
        ],
        "results": results,
    }
    output = root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["metrics"], ensure_ascii=False))


if __name__ == "__main__":
    main()
