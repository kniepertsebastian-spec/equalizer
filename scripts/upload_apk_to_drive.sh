#!/usr/bin/env bash
# Uploads (or, if it already exists, updates) a single APK in a Google Drive
# folder using a Google service account (JWT bearer flow, RS256,
# drive.file scope). No non-standard tooling required beyond curl, jq and
# openssl, which are preinstalled on GitHub-hosted runners.
#
# Required environment variables:
#   GDRIVE_SA_KEY_JSON  - full service account JSON key content
#   GDRIVE_FOLDER_ID    - target Drive folder ID
#   APK_PATH            - local path to the .apk to upload
#   DRIVE_FILE_NAME     - file name to use in Drive (kept stable across builds
#                          so the folder doesn't accumulate one file per run)
#   DRIVE_DESCRIPTION   - optional description text set on the Drive file
set -euo pipefail

: "${GDRIVE_SA_KEY_JSON:?missing}"
: "${GDRIVE_FOLDER_ID:?missing}"
: "${APK_PATH:?missing}"
: "${DRIVE_FILE_NAME:?missing}"
DRIVE_DESCRIPTION="${DRIVE_DESCRIPTION:-}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

SA_KEY_FILE="$(mktemp)"
KEY_FILE="$(mktemp)"
trap 'rm -f "$SA_KEY_FILE" "$KEY_FILE"' EXIT

printf '%s' "$GDRIVE_SA_KEY_JSON" > "$SA_KEY_FILE"
CLIENT_EMAIL="$(jq -r '.client_email' "$SA_KEY_FILE")"
jq -r '.private_key' "$SA_KEY_FILE" > "$KEY_FILE"

NOW=$(date +%s)
EXP=$((NOW + 3000))

HEADER_B64=$(printf '%s' '{"alg":"RS256","typ":"JWT"}' | b64url)
CLAIMS_B64=$(jq -n --arg iss "$CLIENT_EMAIL" \
  --arg scope "https://www.googleapis.com/auth/drive.file" \
  --arg aud "https://oauth2.googleapis.com/token" \
  --argjson iat "$NOW" --argjson exp "$EXP" \
  '{iss:$iss, scope:$scope, aud:$aud, iat:$iat, exp:$exp}' | b64url)

SIGNING_INPUT="${HEADER_B64}.${CLAIMS_B64}"
SIGNATURE=$(printf '%s' "$SIGNING_INPUT" | openssl dgst -sha256 -sign "$KEY_FILE" | b64url)
JWT="${SIGNING_INPUT}.${SIGNATURE}"

ACCESS_TOKEN=$(curl -sS -X POST https://oauth2.googleapis.com/token \
  -d "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" \
  --data-urlencode "assertion=$JWT" | jq -r '.access_token // empty')

if [ -z "$ACCESS_TOKEN" ]; then
  echo "Failed to obtain a Google Drive access token (check GDRIVE_SA_KEY secret)." >&2
  exit 1
fi

EXISTING_ID=$(curl -sS -G "https://www.googleapis.com/drive/v3/files" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode "q='${GDRIVE_FOLDER_ID}' in parents and name='${DRIVE_FILE_NAME}' and trashed=false" \
  --data-urlencode "fields=files(id,name)" | jq -r '.files[0].id // empty')

if [ -n "$EXISTING_ID" ]; then
  echo "Updating existing Drive file ($EXISTING_ID)."
  curl -sS -X PATCH \
    "https://www.googleapis.com/upload/drive/v3/files/${EXISTING_ID}?uploadType=media" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary "@${APK_PATH}" > /dev/null

  jq -n --arg d "$DRIVE_DESCRIPTION" '{description:$d}' | curl -sS -X PATCH \
    "https://www.googleapis.com/drive/v3/files/${EXISTING_ID}" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d @- > /dev/null
else
  echo "Creating new Drive file."
  METADATA=$(jq -n --arg name "$DRIVE_FILE_NAME" --arg parent "$GDRIVE_FOLDER_ID" --arg desc "$DRIVE_DESCRIPTION" \
    '{name:$name, parents:[$parent], description:$desc}')

  curl -sS -X POST "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -F "metadata=${METADATA};type=application/json;charset=UTF-8" \
    -F "file=@${APK_PATH};type=application/vnd.android.package-archive" > /dev/null
fi

echo "Drive upload done."
