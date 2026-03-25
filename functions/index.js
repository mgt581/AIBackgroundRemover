/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const https = require("https");

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({maxInstances: 10});

const TIKTOK_INIT_URL = "https://open.tiktokapis.com/v2/post/publish/video/init/";

/**
 * Makes an HTTPS POST request and returns the parsed JSON response.
 * @param {string} url - The request URL.
 * @param {Object} headers - Request headers.
 * @param {string} body - JSON-serialised request body.
 * @return {Promise<Object>} Parsed JSON response body.
 */
function httpsPost(url, headers, body) {
  return new Promise((resolve, reject) => {
    const parsedUrl = new URL(url);
    const options = {
      hostname: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      method: "POST",
      headers,
    };
    const req = https.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(new Error(`Failed to parse TikTok response: ${data}`));
        }
      });
    });
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}

/**
 * Publishes a video to TikTok using the PULL_FROM_URL source type.
 *
 * Expected call data:
 *   - videoUrl {string}   Publicly accessible URL of the video to publish.
 *   - accessToken {string} TikTok OAuth access token for the target account.
 *
 * Returns:
 *   - publishId {string}  The TikTok publish_id for the created post.
 */
exports.tiktokPublishVideo = onCall(async (request) => {
  const {videoUrl, accessToken} = request.data;

  if (!videoUrl || typeof videoUrl !== "string") {
    throw new HttpsError(
        "invalid-argument",
        "videoUrl is required and must be a string.",
    );
  }
  if (!accessToken || typeof accessToken !== "string") {
    throw new HttpsError(
        "invalid-argument",
        "accessToken is required and must be a string.",
    );
  }

  const requestBody = JSON.stringify({
    post_info: {
      title: "",
      privacy_level: "SELF_ONLY",
      disable_duet: false,
      disable_comment: false,
      disable_stitch: false,
    },
    source_info: {
      source: "PULL_FROM_URL",
      video_url: videoUrl,
    },
  });

  logger.info("Initialising TikTok video publish", {videoUrl});

  let initData;
  try {
    initData = await httpsPost(
        TIKTOK_INIT_URL,
        {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json; charset=UTF-8",
          "Content-Length": Buffer.byteLength(requestBody),
        },
        requestBody,
    );
  } catch (err) {
    logger.error("TikTok init request failed", err);
    throw new HttpsError("internal", `TikTok init request failed: ${err.message}`);
  }

  const publishId = initData?.data?.publish_id;
  const uploadUrl = initData?.data?.upload_url;

  if (!publishId) {
    logger.error("TikTok init missing publish_id", {initData});
    throw new HttpsError(
        "internal",
        `TikTok init missing publish_id: ${JSON.stringify(initData)}`,
    );
  }

  logger.info("TikTok publish initialised", {publishId, uploadUrl});

  return {publishId, uploadUrl};
});
