const target = isLocal ? url.searchParams.get('t') || "" : url.pathname.replace(/^\//, "").replaceAll('/', '_');
rootUrl += target;

// Google maps loader
function googleLoadMapCallback() {
    const map = new google.maps.Map(document.getElementById("map"), {
        center: {lat: 47.4979, lng: 19.0402}, // Example: Budapest
        zoom: 6
    });
}

function startGoogleMap() {
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY&language=${langCode}&region=${countryCode}&callback=googleLoadMapCallback`;
    script.async = true;
    document.head.appendChild(script);
}

const prj = url.searchParams.get('p') || "*";
let colsUrl = null;
if (isLocal) {
    colsUrl = rootUrl + ".json";
    rootUrl += ".csv?";
} else {
    rootUrl = rootUrl.replace("/example", "/szexpartnerek");
    rootUrl += "?p=" + encodeURIComponent(prj);
    colsUrl = rootUrl + "&pg.size=0";
}
tabulatorFunctions = {
    headerMenu: function () {
        var menu = [];
        var columns = this.getColumns();

        for (let column of columns) {
            let $icon = $("<span>")
                .addClass("ui-icon")
                .addClass(column.isVisible() ? "ui-icon-check" : "ui-icon-blank")
                .css({
                    display: "inline-block",
                    "vertical-align": "middle",
                    "margin-right": "4px"
                });

            let $label = $("<span>");
            let $title = $("<span>").text(" " + column.getDefinition().title);
            $label.append($icon).append($title);
            menu.push({
                label: $label[0],
                action: function (e) {
                    e.stopPropagation();
                    column.toggle();
                    if (column.isVisible()) {
                        $icon.removeClass("ui-icon-blank").addClass("ui-icon-check");
                    } else {
                        $icon.removeClass("ui-icon-check").addClass("ui-icon-blank");
                    }
                }
            });
        }
        return menu;
    },
    datetime: {
        inputFormat: "yyyy-MM-dd HH:mm:ss",
        outputFormat: "yyyy-MM-dd HH:mm:ss",
        timezone: "Europe/Budapest"
    },
    //cell - the cell component
    //formatterParams - parameters set for the column
    //onRendered - function to call when the formatter has been rendered
    fmt_path: function (cell, formatterParams, onRendered) {
        var path = formatterParams.urlPrefix + cell.getValue();
        var data = cell.getData();
        return `<a target="img_${data.rowid}" href="${path.replace("_thumb", "")}"><img src="${path}"/></a>`;
    },
    fmt_partner_rowid: function (cell, formatterParams, onRendered) {
        var id = cell.getValue();
        return `<a target="p_${id}" href="https://rosszlanyok.hu/rosszlanyok.php?pid=szexpartner-data&id=${id}">${id}</a>`;
    },
    col_extra_languages: {tooltip: false},
    fmt_extra_languages: function (cell, formatterParams, onRendered) {
        var data = cell.getValue().split(", ");
        var html = [];
        data.forEach(function (lang) {
            let iso2 = lang;
            lang = attempt2To3(false, lang).toLowerCase();
            const cs = findCountriesByLang(lang);
            if (cs) {
                if (cs.length > 1) html.push("<span class='infinite-scroll'>")
                for (const c of cs) {
                    let l, n, a;
                    a = locale([iso2, c.cca3], 0);
                    if (a) n = a[1];
                    else n = c.name.nativeName[lang] ? c.name.nativeName[lang].common : c.name.official;
                    if (a) l = a[0];
                    else {
                        l = Object.values(c.languages)[0];
                        if (c.languages[lang]) l = c.languages[lang];
                    }
                    html.push(`<span title='${l.cap()} / ${n}'>${c.flag}</span>`);
                }
                if (cs.length > 1) html.push("</span>")
            }
        });
        return html.join(" ");
    },
    col_location: {
        tooltip: function (e, cell, onRendered) {
            var data = cell.getValue();
            try {
                data = JSON.parse(data);
                data = data.formatted || data.name;
                if (data) data = "Kb: " + data;
                return data;
            } catch (ex) {
                return data;
            }
        }
    },
    fmt_location: function (cell, formatterParams, onRendered) {
        var cells = cell.getData();
        var data = cell.getValue();
        try {
            data = JSON.parse(data);
        } catch (e) {
        }
        var q = null;
        if (data.city) q = data.city + (data.district ? ", " + data.district : "");
        if (!q) q = data.formatted;
        if (!q) q = data.name;
        var ex = data.oextra ? "(" + data.oextra + ")" : (data.extra || "");
        if (data.bbox && data.bbox.lat1 && data.bbox.lon1) {
            if (data.bbox.lat2 && data.bbox.lon2) {
                var lat = (data.bbox.lat1 + data.bbox.lat2) / 2;
                var lon = (data.bbox.lon1 + data.bbox.lon2) / 2;
            } else {
                lat = data.bbox.lat1;
                lon = data.bbox.lon1;
            }
            mapslink = `@${lat},${lon}`;
        } else if (q) {
            mapslink = `${encodeURIComponent(q)}`;
        } else {
            return data;
        }
        return `<a target="map_${cells.partner_id}" href="https://www.google.com/maps?q=${mapslink}">${q}</a> ${ex}`;
    },
    col_call_number: {tooltip: false},
    fmt_call_number: function (cell, formatterParams, onRendered) {
        let val = cell.getValue().split(" ");
        let ret = "📲 <a href=\"tel:" + val[0] + "\">" + val[0] + "</a>";
        const obj = {
            WHATSAPP: `<a href="https://api.whatsapp.com/send?phone=${val[0].replace("+", "")}" target="_blank" title="On WhatsApp"><img src="assets/whatsapp.svg" alt="WhatsApp" class="phone"></a>`,
            VIBER: `<a href="viber://call?number=${val[0]}" title="On Viber"><img src="assets/viber.svg" alt="Viber" class="phone"></a>`
        };
        for (const c of val.slice(1)) if (obj[c]) ret += " " + obj[c]; else ret += " " + c;
        return ret;
    },
};
tabulatorFunctions.fmt_partner_ext_rowid = tabulatorFunctions.fmt_partner_rowid;
tabulatorFunctions.fmt_partner_ext_view_rowid = tabulatorFunctions.fmt_partner_rowid;

function urlGenerator(url, config, params) {
    const query = [];
    if (params.page) query.push(`pg=${params.page}/${params.size}`);
    if (params.sort && params.sort.length > 0) query.push("o=" + encodeURIComponent(JSON.stringify(params.sort)));
    if (params.filter && params.filter.length > 0) query.push("w=" + encodeURIComponent(JSON.stringify(params.filter)));
    return url + (query.length ? "&" + query.join('&') : '');
}

function initializeFields(columns) {
    columns.forEach(function (col) {
        const params = tabulatorFunctions["fmp_" + target + "_" + col.field] || tabulatorFunctions["fmp_" + col.field];
        if (params) {
            col.formatterParams = Object.assign({}, col.formatterParams, params);
            col.sorter = col.formatter;
        }
        const cold = tabulatorFunctions["col_" + target + "_" + col.field] || tabulatorFunctions["col_" + col.field];
        if (cold) Object.assign(col, cold);
        var fs = tabulatorFunctions["fmt_" + target + "_" + col.field] || tabulatorFunctions["fmt_" + col.field];
        if (fs) col.formatter = fs;
        col.headerMenu = tabulatorFunctions.headerMenu;
    });
}

function calculatePageSize() {
    var tableHolder = window.table.element.querySelector('.tabulator-tableholder');
    var bodyHeight = tableHolder.clientHeight;
    var row = tableHolder.querySelector('.tabulator-row');
    if (!row) return null;
    var rowHeight = row.offsetHeight;
    return Math.floor(bodyHeight / rowHeight);
}

function updatePageSize() {
    var pageSize = calculatePageSize();
    if (pageSize) window.table.setPageSize(pageSize);
}

$(function () {
    $("#main-accordion").accordion({
        active: 1, heightStyle: "fill"
    });
});
jQuery.get({
    url: colsUrl,
    dataType: 'json',
    success: function (def) {
        var columns = def.cols || [];
        initializeFields(columns);
        var initialSort = def.sort || [];

        var tabulatorSort = [];
        if (initialSort && initialSort.length) {
            initialSort.forEach(function (s) {
                var parts = s.split(' ');
                if (parts.length === 2) {
                    tabulatorSort.push({column: parts[0], dir: parts[1]});
                }
            });
        }
        const pgMode = isLocal ? "local" : "remote";
        tabulator = new Tabulator("#results-table", {
            initialSort: tabulatorSort,
            layout: "fitColumns",
            responsiveLayout: false,
            history: true,
            movableColumns: true,
            columnDefaults: {tooltip: true},
            columns: columns,
            // Remember everything
            persistenceMode: true, persistenceID: target, persistence: true,
            // Pagination is enabled
            pagination: true,
            paginationInitialPage: 1,
            paginationSize: 25,
            paginationCounter: "rows",
            // Set pagination style:
            paginationMode: pgMode,
            sortMode: pgMode,
            filterMode: pgMode,
            // Setup ajax
            ajaxURLGenerator: urlGenerator,
            ajaxURL: rootUrl,
            ajaxConfig: {
                method: "GET",
                headers: {
                    "Accept": "text/csv"
                }
            },
            ajaxRequestFunc: function (url, config, params) {
                url = urlGenerator(url, config, params);
                return fetch(url).then(response => {
                    const hds = response.headers;
                    return response.text().then(data => {
                        const impm = tabulator.modules.import;
                        const imp = impm.lookupImporter("csv");
                        return impm.importData(imp, data).then(parsedData => {
                            const keys = parsedData.shift();
                            const dataAsObjects = parsedData.map(row =>
                                Object.fromEntries(keys.map((key, i) => [key.trim(), row[i]]))
                            );
                            return isLocal ? dataAsObjects : {
                                data: dataAsObjects,
                                last_page: parseInt(hds.get('X-Page-Count') || 1, 10),
                                current_page: parseInt(hds.get('X-Page-Current') || 1, 10),
                                page_size: parseInt(hds.get('X-Page-Size') || 25, 10),
                                total: parseInt(hds.get('X-Total') || 0, 10)
                            };
                        });
                    });
                });
            }
        });
        window.table = tabulator;
        tabulator.on("tableBuilt", updatePageSize);
        $(window).on('resize', function () {
            $("#main-accordion").accordion("refresh");
            updatePageSize();
        });
    }
});
