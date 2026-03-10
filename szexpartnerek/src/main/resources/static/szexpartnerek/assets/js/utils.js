const url = new URL(location.href);
const isLocal = window.location.protocol === "file:";
const target = isLocal ? url.searchParams.get('t') || "" : url.pathname.replace(/^\//, "").replaceAll('/', '_');
let rootUrl = "example/";
let bRootUrl = "/api";
if (isLocal) bRootUrl = rootUrl
else rootUrl = bRootUrl + "/" + rootUrl;
rootUrl += target;