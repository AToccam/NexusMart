#!/usr/bin/env bash
set -euo pipefail

NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
NACOS_GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"

publish_config() {
  local data_id="$1"
  local source_file="$2"

  if [[ ! -f "$source_file" ]]; then
    echo "config file not found: $source_file" >&2
    exit 1
  fi

  curl -sS -X POST "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=${NACOS_GROUP}" \
    --data-urlencode "tenant=${NACOS_NAMESPACE}" \
    --data-urlencode "content=$(cat "$source_file")" >/dev/null

  echo "published ${data_id} from ${source_file}"
}

publish_config "nexusmart-seckill-dev.yml" "nacos/configs/nexusmart-seckill-dev.yml"
publish_config "nexusmart-gateway-dev.yml" "nacos/configs/nexusmart-gateway-dev.yml"

echo "nacos config publish done"
