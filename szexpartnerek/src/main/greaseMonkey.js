// ==UserScript==
// @name        Rosszlanyok
// @namespace   DeToX
// @version     1
// @match       https://rosszlanyok.hu/*
// @match       https://m.rosszlanyok.hu/*
// @require     http://ajax.googleapis.com/ajax/libs/jquery/1.8.1/jquery.min.js
// @grant       GM_addStyle
// @grant       GM_getResourceText
// @grant       none
// @run-at      document-idle
// ==/UserScript==

$("#highlightedListHeader").parent().remove();
$("#kiemelet-jobb, #homeGirlList").remove();
$(".listaKiemelt>div").unwrap();

const urlParams = new URLSearchParams(window.location.search);
if (urlParams.get("htext") == 39) return; // Not liked users

var MutationObserver = window.MutationObserver;
var myObserver = new MutationObserver(mutationHandler);
var obsConfig = {childList: true, attributes: false, subtree: true};

myObserver.observe(document, obsConfig);

function mutationHandler(mutationRecords) {
    mutationRecords.forEach(function (mutation) {
        if (mutation.type == "childList"
            && typeof mutation.addedNodes == "object"
            && mutation.addedNodes.length
        ) {
            for (var J = 0, L = mutation.addedNodes.length; J < L; ++J) {
                onChange(mutation.addedNodes[J]);
            }
        }
    });
}

function onChange(node) {
    var n = $('img[src$="donotlike.jpg"]');
    n.parent().parent().parent().remove();
}

onChange(document);