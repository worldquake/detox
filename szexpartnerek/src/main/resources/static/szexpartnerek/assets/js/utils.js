function corsOff(url) {
    if (window.location.protocol === "file:") {
        return "https://api.allorigins.win/get?url=" + encodeURIComponent(url);
    }
    return url;
}