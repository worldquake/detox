String.prototype.cap = function () {
    if (this.length === 0) return this;
    if (!/[a-zA-Z]/.test(this.charAt(0))) return this;
    return this.charAt(0).toUpperCase() + this.slice(1);
}

const url = new URL(location.href);
const isLocal = window.location.protocol === "file:";
const target = isLocal ? url.searchParams.get('t') || "" : url.pathname.replace(/^\//, "").replaceAll('/', '_');
let rootUrl = "example/";
let bRootUrl = "/api";
if (isLocal) bRootUrl = rootUrl
else rootUrl = bRootUrl + "/" + rootUrl;
rootUrl += target;