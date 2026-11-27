#!/bin/bash
# Espera a que acabe una ejecución de CI y muestra el resultado y los errores. esperar.sh RUN
cd "$(dirname "$0")"
until [ "$(gh run view "$1" --json status --jq .status 2>/dev/null)" = completed ]; do sleep 30; done
gh run view "$1" --json conclusion --jq .conclusion
gh run view "$1" --log-failed 2>&1 | grep -E "error:|Test Case.*failed" | sed 's/.*Z //' | sort -u | head -20
