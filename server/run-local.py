#!/usr/bin/env python3
"""Run the local server with .env values, without shell evaluation or secret output."""
import argparse
import os
from pathlib import Path
import re


def load_env(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$", line)
        if match:
            value = match[2].strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            result[match[1]] = value
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=".env")
    parser.add_argument("--java", default=None)
    args = parser.parse_args()
    server_dir = Path(__file__).resolve().parent
    explicit_java = str(Path(args.java).expanduser().resolve()) if args.java else None
    portable_java = next((server_dir.parent / ".local/tools").glob("jdk*/Contents/Home/bin/java"), None)
    os.chdir(server_dir)
    runtime_env = dict(os.environ)
    runtime_env.update(load_env(Path(args.env_file)))
    runtime_env.setdefault("SPRING_PROFILES_ACTIVE", "local")
    java = explicit_java or (str(Path(runtime_env["JAVA_HOME"]) / "bin/java") if runtime_env.get("JAVA_HOME") else str(portable_java) if portable_java else "java")
    jar = Path("build/libs/server-0.0.1-SNAPSHOT.jar")
    if not jar.is_file():
        raise SystemExit("Build the server with: bash gradlew bootJar")
    os.execvpe(java, [java, "-jar", str(jar), "--spring.profiles.active=local"], runtime_env)
