#!/usr/bin/env bash
# Copyright 2026 Netflix, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.

set -euo pipefail

if (($# != 4)); then
    echo "Usage: $0 <source-java-home> <output-java-home> <ja-version> <jig-version>" >&2
    exit 2
fi
source_java_home="$1"
output_java_home="$2"
ja_version="$3"
jig_version="$4"

if [[ -e "$output_java_home" ]]; then
    echo "Output path already exists: $output_java_home" >&2
    exit 1
fi
java="$source_java_home/bin/java"
jlink="$source_java_home/bin/jlink"
if [[ ! -x "$java" || ! -x "$jlink" || ! -f "$source_java_home/jmods/java.base.jmod" ]]; then
    echo "The source Java installation must provide java, jlink, and JMODs" >&2
    exit 1
fi

work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/ja-toolchain.XXXXXX")"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/home" "$(dirname "$output_java_home")"

bootstrap_jig="$work/com.netflix.tools.jig-$jig_version.jar"
curl --fail --silent --show-error --location \
    "https://repo.maven.apache.org/maven2/com/netflix/com.netflix.tools.jig/$jig_version/com.netflix.tools.jig-$jig_version.jar" \
    --output "$bootstrap_jig"

module_arguments="$work/modules.args"
"$java" \
    -Duser.home="$work/home" \
    --module-path "$bootstrap_jig" \
    --module com.netflix.tools.jig/com.netflix.tools.jig.Jig \
    --add-requires "com.netflix.tools.jig@$jig_version" \
    --add-requires "com.netflix.tools.ja@$ja_version" \
    --prefer-jmod \
    --target-platform CURRENT \
    --resolve-options module-path \
    --write-argfile "$module_arguments"

resolved=()
while IFS= read -r line; do
    resolved+=("$line")
done < "$module_arguments"
if ((${#resolved[@]} != 2)) || [[ "${resolved[0]}" != --module-path ]]; then
    echo "jig did not produce one module path" >&2
    exit 1
fi

"$jlink" \
    --module-path "$source_java_home/jmods:${resolved[1]}" \
    --add-modules ALL-MODULE-PATH \
    --generate-cds-archive \
    --output "$output_java_home"

[[ -x "$output_java_home/bin/ja" && -x "$output_java_home/bin/jig" ]]
"$output_java_home/bin/java" --list-modules | grep -Fqx "com.netflix.tools.ja@$ja_version"
"$output_java_home/bin/java" --list-modules | grep -Fqx "com.netflix.tools.jig@$jig_version"
if ! find "$output_java_home/lib" -type f -name '*.jsa' -print -quit | grep -q .; then
    echo "The linked toolchain has no CDS archive" >&2
    exit 1
fi
