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
require_env STORAGE_TYPE
require_env STORAGE_ENDPOINT
require_env STORAGE_PATH
require_env STORAGE_ACCESS_KEY_ID
require_env STORAGE_ACCESS_KEY_SECRET
require_env STORAGE_OVERWRITE

case "${ARCHIVE_FORMAT}" in
  docker-archive|oci-archive) ;;
  *) echo "unsupported ARCHIVE_FORMAT: ${ARCHIVE_FORMAT}" >&2; exit 2 ;;
esac

case "${COMPRESSION}" in
  none|gzip|zstd) ;;
  *) echo "unsupported COMPRESSION: ${COMPRESSION}" >&2; exit 2 ;;
esac

case "${STORAGE_TYPE}" in
  s3) ;;
  *) echo "unsupported STORAGE_TYPE: ${STORAGE_TYPE}" >&2; exit 2 ;;
esac

case "${STORAGE_PATH}" in
  s3://*) ;;
  *) echo "STORAGE_PATH must start with s3://" >&2; exit 2 ;;
esac

mkdir -p "$(dirname "${OUTPUT_FILE}")"

tmp_dir="$(mktemp -d)"
auth_file="${tmp_dir}/containers-auth.json"
mc_config_dir="${tmp_dir}/mc"
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

export MC_CONFIG_DIR="${mc_config_dir}"
mkdir -p "${MC_CONFIG_DIR}"
path_mode="auto"
if [[ "${STORAGE_FORCE_PATH_STYLE:-false}" == "true" ]]; then
  path_mode="on"
fi
if [[ -n "${STORAGE_REGION:-}" ]]; then
  export MC_REGION="${STORAGE_REGION}"
fi
mc alias set --api S3v4 --path "${path_mode}" object-storage "${STORAGE_ENDPOINT}" \
  "${STORAGE_ACCESS_KEY_ID}" "${STORAGE_ACCESS_KEY_SECRET}" >/dev/null

storage_target="object-storage/${STORAGE_PATH#s3://}"
if [[ "${STORAGE_OVERWRITE}" != "true" ]] && mc stat "${storage_target}" >/dev/null 2>&1; then
  echo "storage object already exists and overwrite is false: ${STORAGE_PATH}" >&2
  exit 3
fi

echo "using storage endpoint ${STORAGE_ENDPOINT}"
echo "uploading archive to ${STORAGE_PATH}"
mc cp "${upload_file}" "${storage_target}"

echo "image export uploaded: ${STORAGE_PATH}"
