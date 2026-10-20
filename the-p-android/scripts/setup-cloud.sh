#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export GRADLE_USER_HOME=/workspace/.gradle
export ANDROID_HOME=/workspace/.android-sdk
export ANDROID_USER_HOME=/workspace/.android-user
mkdir -p /workspace/.tools "$GRADLE_USER_HOME" "$ANDROID_USER_HOME"
thep_download_dir=$(mktemp -d /tmp/the-p-setup.XXXXXX)
trap 'rm -rf "$thep_download_dir"' EXIT
if [[ ! -x /workspace/.tools/thep-jdk21/bin/javac ]]; then
  if [[ -x /workspace/.tools/jdk-21.0.12.1/bin/javac ]]; then
    ln -s /workspace/.tools/jdk-21.0.12.1 /workspace/.tools/thep-jdk21
  else
    curl -fLsS --retry 2 https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz -o "$thep_download_dir/jdk.tar.gz"
    curl -fLsS --retry 2 https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz.sha256 -o "$thep_download_dir/jdk.sha256"
    python3 - "$thep_download_dir" <<'PY'
import hashlib, pathlib, sys, tarfile
root = pathlib.Path(sys.argv[1])
archive = root / 'jdk.tar.gz'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == (root / 'jdk.sha256').read_text().strip(), 'JDK checksum mismatch'
with tarfile.open(archive) as package:
    package.extractall(root / 'jdk', filter='data')
(root / 'jdk').iterdir().__next__().rename('/workspace/.tools/thep-jdk21')
PY
  fi
fi
export JAVA_HOME=/workspace/.tools/thep-jdk21
# Trust the machine's managed CA store; do not bypass TLS or artifact verification.
python3 - <<'PY'
import os, pathlib, urllib.parse
p = pathlib.Path('/workspace/.gradle/gradle.properties')
lines = p.read_text().splitlines() if p.exists() else []
keys = ['systemProp.https.proxyHost=', 'systemProp.https.proxyPort=', 'systemProp.http.proxyHost=', 'systemProp.http.proxyPort=', 'systemProp.javax.net.ssl.trustStore=']
lines = [line for line in lines if not any(line.startswith(key) for key in keys)]
u = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
if u.hostname:
    for protocol in ('https', 'http'):
        lines.extend([f'systemProp.{protocol}.proxyHost={u.hostname}', f'systemProp.{protocol}.proxyPort={u.port or 80}'])
if pathlib.Path('/etc/ssl/certs/java/cacerts').is_file():
    lines.append('systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
p.write_text('\n'.join(lines) + '\n')
PY
if [[ ! -x "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" ]]; then
  curl -fLsS --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$thep_download_dir/tools.zip"
  printf '%s  %s\n' 5fdcc763663eefb86a5b8879697aa6088b041e70 "$thep_download_dir/tools.zip" | sha1sum -c -
  unzip -q "$thep_download_dir/tools.zip" -d "$thep_download_dir/android-tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$thep_download_dir/android-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/19.0"
fi
# SDK Manager performs package checksum verification. Accept licenses for this development SDK.
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
  export JAVA_OPTS="${JAVA_OPTS:-} -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses > "$thep_download_dir/licenses.log"
thep_sdk_status=${PIPESTATUS[1]}
set -o pipefail
[[ "$thep_sdk_status" == 0 ]]
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-36' 'build-tools;35.0.0' 'build-tools;36.0.0' 'platform-tools'
if [[ ! -e local.properties ]]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
fi
bash scripts/cloud-gradle.sh spotlessCheck assembleDebug testDebugUnitTest :core:model:test
bash scripts/cloud-gradle.sh lintDebug --no-parallel
