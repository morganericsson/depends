#!/usr/bin/env python3
import argparse
import json
import pathlib
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description="Compare Java Tree-sitter output against java-antlr output.")
    parser.add_argument("source", help="Java source tree to analyze")
    parser.add_argument("output_root", help="Directory where per-frontend outputs will be written")
    parser.add_argument("--jar", default="target/depends-0.9.8-jar-with-dependencies.jar",
                        help="Depends jar to run")
    parser.add_argument("--java", default="java", help="Java executable")
    parser.add_argument("--granularity", default="file,method,structure",
                        help="Comma-separated granularities to compare")
    parser.add_argument("--sample-size", type=int, default=10,
                        help="Number of java-antlr-only typed edges to print per granularity")
    args = parser.parse_args()

    source = pathlib.Path(args.source).resolve()
    output_root = pathlib.Path(args.output_root).resolve()
    jar = pathlib.Path(args.jar).resolve()
    granularities = [item.strip() for item in args.granularity.split(",") if item.strip()]

    if not source.exists():
        parser.error("source does not exist: %s" % source)
    if not jar.is_file():
        parser.error("jar does not exist: %s" % jar)

    outputs = {
        "java": output_root / "java",
        "java-antlr": output_root / "java-antlr",
    }
    for output in outputs.values():
        if output.exists():
            shutil.rmtree(output)
        output.mkdir(parents=True)

    for lang, output in outputs.items():
        run_depends(args.java, jar, lang, source, lang, output, args.granularity)

    for granularity in granularities:
        tree_sitter = load_graph(outputs["java"] / ("java-%s.json" % granularity))
        antlr = load_graph(outputs["java-antlr"] / ("java-antlr-%s.json" % granularity))
        report(granularity, tree_sitter, antlr, args.sample_size)


def run_depends(java_bin, jar, lang, source, output_name, output_dir, granularity):
    command = [
        java_bin, "-jar", str(jar),
        lang, str(source), output_name,
        "-d", str(output_dir),
        "-f", "json",
        "-g", granularity,
        "--strip-leading-path",
    ]
    print("Running %s..." % lang, file=sys.stderr)
    subprocess.run(command, check=True)


def load_graph(path):
    with path.open() as handle:
        data = json.load(handle)
    variables = data.get("variables", [])
    typed_edges = set()
    type_weights = {}
    for cell in data.get("cells", []):
        source = variables[cell["src"]]
        target = variables[cell["dest"]]
        for dep_type, weight in cell.get("values", {}).items():
            typed_edges.add((source, target, dep_type))
            type_weights[dep_type] = type_weights.get(dep_type, 0.0) + float(weight)
    return {
        "nodes": len(variables),
        "edges": len(data.get("cells", [])),
        "typed_edges": typed_edges,
        "type_weights": type_weights,
    }


def report(granularity, tree_sitter, antlr, sample_size):
    ts_edges = tree_sitter["typed_edges"]
    antlr_edges = antlr["typed_edges"]
    missing = sorted(antlr_edges - ts_edges)
    extra = sorted(ts_edges - antlr_edges)

    print("\n[%s]" % granularity)
    print("  java:       nodes=%d edges=%d typed_edges=%d" %
          (tree_sitter["nodes"], tree_sitter["edges"], len(ts_edges)))
    print("  java-antlr: nodes=%d edges=%d typed_edges=%d" %
          (antlr["nodes"], antlr["edges"], len(antlr_edges)))
    print("  common=%d java_only=%d java_antlr_only=%d" %
          (len(ts_edges & antlr_edges), len(extra), len(missing)))
    print("  java weights:       %s" % format_weights(tree_sitter["type_weights"]))
    print("  java-antlr weights: %s" % format_weights(antlr["type_weights"]))
    for edge in missing[:sample_size]:
        print("  missing: %s -> %s [%s]" % edge)


def format_weights(weights):
    return ", ".join("%s=%d" % (key, value) for key, value in sorted(
        weights.items(), key=lambda item: (-item[1], item[0]))[:8])


if __name__ == "__main__":
    main()
