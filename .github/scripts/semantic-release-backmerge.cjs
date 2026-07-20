const { execFileSync } = require("node:child_process");
const path = require("node:path");

async function success(_pluginConfig, context) {
  if (context.branch.name !== "main") return;

  context.logger.log("Back-merging main into dev.");
  execFileSync(
    "bash",
    [path.join(__dirname, "backmerge-main-to-dev.sh")],
    { stdio: "inherit" },
  );
}

module.exports = { success };
