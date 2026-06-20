#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "missing required environment variable: ${name}" >&2
    exit 2
  fi
}

require_env IMAGE_REF
require_env ARCHIVE_FORMAT
require_env COMPRESSION
require_env OUTPUT_FILE
require_env REGISTRY_USERNAME
require_env REGISTRY_PASSWORD
require_env REGISTRY_TLS_VERIFY
require_env OSS_ENDPOINT
require_env OSS_PATH
require_env OSS_ACCESS_KEY_ID
require_env OSS_ACCESS_KEY_SECRET
require_env OSS_OVERWRITE

case "${ARCHIVE_FORMAT}" in
  docker-archive|oci-archive) ;;
  *) echo "unsupported ARCHIVE_FORMAT: ${ARCHIVE_FORMAT}" >&2; exit 2 ;;
esac

case "${COMPRESSION}" in
  none|gzip|zstd) ;;
  *) echo "unsupported COMPRESSION: ${COMPRESSION}" >&2; exit 2 ;;
esac

case "${OSS_PATH}" in
  oss://*) ;;
  *) echo "OSS_PATH must start with oss://" >&2; exit 2 ;;
esac

mkdir -p "$(dirname "${OUTPUT_FILE}")"

tmp_dir="$(mktemp -d)"
auth_file="${tmp_dir}/containers-auth.json"
oss_config="${tmp_dir}/ossutil-config"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

umask 077
auth_b64="$(printf '%s:%s' "${REGISTRY_USERNAME}" "${REGISTRY_PASSWORD}" | base64 | tr -d '\n')"
cat > "${auth_file}" <<EOF
{
  "auths": {
    "$(printf '%s' "${IMAGE_REF}" | cut -d/ -f1)": {
      "auth": "${auth_b64}"
    }
  }
}
EOF

skopeo_tls_arg="--src-tls-verify=true"
if [[ "${REGISTRY_TLS_VERIFY}" == "false" ]]; then
  skopeo_tls_arg="--src-tls-verify=false"
fi

echo "exporting image ${IMAGE_REF} as ${ARCHIVE_FORMAT}"
skopeo copy \
  "${skopeo_tls_arg}" \
  --src-authfile "${auth_file}" \
  "docker://${IMAGE_REF}" \
  "${ARCHIVE_FORMAT}:${OUTPUT_FILE}:${IMAGE_REF}"

upload_file="${OUTPUT_FILE}"
case "${COMPRESSION}" in
  gzip)
    echo "compressing archive with gzip"
    gzip -f "${OUTPUT_FILE}"
    upload_file="${OUTPUT_FILE}.gz"
    ;;
  zstd)
    echo "compressing archive with zstd"
    zstd -f --rm "${OUTPUT_FILE}"
    upload_file="${OUTPUT_FILE}.zst"
    ;;
esac

cat > "${oss_config}" <<EOF
[Credentials]
language=CH
endpoint=${OSS_ENDPOINT}
accessKeyID=${OSS_ACCESS_KEY_ID}
accessKeySecret=${OSS_ACCESS_KEY_SECRET}
EOF

if [[ "${OSS_OVERWRITE}" != "true" ]] && ossutil stat "${OSS_PATH}" -c "${oss_config}" >/dev/null 2>&1; then
  echo "OSS object already exists and overwrite is false: ${OSS_PATH}" >&2
  exit 3
fi

echo "uploading archive to ${OSS_PATH}"
if [[ "${OSS_OVERWRITE}" == "true" ]]; then
  ossutil cp "${upload_file}" "${OSS_PATH}" -f -c "${oss_config}"
else
  ossutil cp "${upload_file}" "${OSS_PATH}" -c "${oss_config}"
fi

echo "image export uploaded: ${OSS_PATH}"
