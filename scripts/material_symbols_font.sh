#!/bin/sh
# Rebuilds res/font/material_symbols_rounded{,_outline}.ttf, the icon fonts of the control buttons (see ControlIcon):
# the filled and the outlined, weight 500, 24 px optical size instances of Google's Material Symbols Rounded
# (Apache License 2.0).
# Needs Docker; run from the repo root. SOURCE_DATE_EPOCH keeps the output byte for byte the same.
set -eu
commit=10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7
sha256=7e74e36533ccee12c2b81e75bfcf3ccf123d3bb7e7e46d65d94445f23db0c97c
out=app_pojavlauncher/src/main/res/font/material_symbols_rounded
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

curl -sfL -o "$tmp/var.ttf" \
    "https://github.com/google/material-design-icons/raw/$commit/variablefont/MaterialSymbolsRounded%5BFILL,GRAD,opsz,wght%5D.ttf"
echo "$sha256  $tmp/var.ttf" | sha256sum -c -
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e SOURCE_DATE_EPOCH=0 -v "$tmp:/f" -w /f python:3.12-slim sh -c \
    'pip install -q --user fonttools==4.55.3 &&
     python -m fontTools.varLib.instancer var.ttf FILL=1 GRAD=0 opsz=24 wght=500 -o filled.ttf &&
     python -m fontTools.varLib.instancer var.ttf FILL=0 GRAD=0 opsz=24 wght=500 -o outline.ttf'
cp "$tmp/filled.ttf" "$out.ttf"
cp "$tmp/outline.ttf" "${out}_outline.ttf"
