#!/bin/sh
set -eu

REPOSITORY="klaeufer/scrai-scala"
ASSET="scrai-standalone-scala3.9.zip"
RELEASE_URL="https://github.com/$REPOSITORY/releases/latest/download"
VERSION="3.9.0"

if ! command -v curl >/dev/null 2>&1; then
  echo "scrai installation requires curl." >&2
  exit 1
fi
if ! command -v unzip >/dev/null 2>&1; then
  echo "scrai installation requires unzip." >&2
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "scrai requires Java 17 or newer. Install Java, then run this installer again." >&2
  exit 1
fi

case "$(java -version 2>&1 | awk -F'[.\"]' '/version/ { print $2; exit }')" in
  ''|[0-9]|1[0-6])
    echo "scrai requires Java 17 or newer." >&2
    exit 1
    ;;
esac

checksum_tool=
if command -v sha256sum >/dev/null 2>&1; then
  checksum_tool=sha256sum
elif command -v shasum >/dev/null 2>&1; then
  checksum_tool="shasum -a 256"
else
  echo "scrai installation requires sha256sum or shasum." >&2
  exit 1
fi

data_dir=${SCRAI_HOME:-"$HOME/.local/share/scrai"}
bin_dir=${SCRAI_BIN_DIR:-"$HOME/.local/bin"}
install_dir="$data_dir/$VERSION"
tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/scrai-install.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

archive="$tmp_dir/$ASSET"
checksum="$tmp_dir/$ASSET.sha256"
curl -fsSL "$RELEASE_URL/$ASSET" -o "$archive"
curl -fsSL "$RELEASE_URL/$ASSET.sha256" -o "$checksum"

expected=$(awk 'NF { print $1; exit }' "$checksum")
actual=$($checksum_tool "$archive" | awk '{ print $1 }')
if [ "$expected" != "$actual" ]; then
  echo "scrai checksum verification failed." >&2
  exit 1
fi

extracted="$tmp_dir/extracted"
mkdir -p "$extracted" "$bin_dir" "$data_dir"
unzip -q "$archive" -d "$extracted"
rm -rf "$install_dir"
mv "$extracted"/* "$install_dir"
ln -sfn "$install_dir/bin/scrai" "$bin_dir/scrai"

echo "Installed scrai (Scala $VERSION) to $install_dir"
case ":${PATH:-}:" in
  *:"$bin_dir":*) ;;
  *) echo "Add $bin_dir to PATH to run: scrai" ;;
esac