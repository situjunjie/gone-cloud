#!/usr/bin/env bash
set -euo pipefail

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "missing required environment variable: ${name}" >&2
    exit 2
  fi
}

require_env FILE_URL
require_env IMAGE_REF
require_env ARCHIVE_FORMAT
require_env COMPRESSION
require_env REGISTRY_USERNAME
require_env REGISTRY_PASSWORD
require_env REGISTRY_TLS_VERIFY

case "${ARCHIVE_FORMAT}" in
  docker-archive|oci-archive) ;;
  *) echo "unsupported ARCHIVE_FORMAT: ${ARCHIVE_FORMAT}" >&2; exit 2 ;;
esac

case "${COMPRESSION}" in
  none|gzip|zstd) ;;
  *) echo "unsupported COMPRESSION: ${COMPRESSION}" >&2; exit 2 ;;
esac

tmp_dir="$(mktemp -d)"
download_file="${tmp_dir}/image-archive.download"
archive_file="${tmp_dir}/image-archive"
auth_file="${tmp_dir}/containers-auth.json"
cleanup() {
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

max_download_bytes="${MAX_DOWNLOAD_BYTES:-5368709120}"
echo "downloading image archive from ${FILE_URL}"
curl --http1.1 -fL --retry 3 --retry-delay 2 --connect-timeout 20 --max-time "${DOWNLOAD_TIMEOUT_SECONDS:-1800}" \
  --max-filesize "${max_download_bytes}" \
  -o "${download_file}" "${FILE_URL}"

case "${COMPRESSION}" in
  none)
    mv "${download_file}" "${archive_file}"
    ;;
  gzip)
    echo "decompressing archive with gzip"
    gzip -dc "${download_file}" > "${archive_file}"
    ;;
  zstd)
    echo "decompressing archive with zstd"
    zstd -dc "${download_file}" > "${archive_file}"
    ;;
esac

if [[ ! -s "${archive_file}" ]]; then
  echo "downloaded image archive is empty" >&2
  exit 3
fi

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

skopeo_tls_arg="--dest-tls-verify=true"
if [[ "${REGISTRY_TLS_VERIFY}" == "false" ]]; then
  skopeo_tls_arg="--dest-tls-verify=false"
fi

echo "importing ${ARCHIVE_FORMAT} archive to ${IMAGE_REF}"
skopeo copy \
  "${skopeo_tls_arg}" \
  --dest-authfile "${auth_file}" \
  "${ARCHIVE_FORMAT}:${archive_file}" \
  "docker://${IMAGE_REF}"

echo "image archive imported: ${IMAGE_REF}"
