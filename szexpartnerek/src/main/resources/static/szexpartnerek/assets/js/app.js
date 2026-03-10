const url = new URL(location.href);
const table = url.searchParams.get('t') || url.pathname.replace(/^\//, "").replaceAll('/', '_');
const prj = url.searchParams.get('p') || "*";
const q = "p=" + encodeURIComponent(prj);
var rootUrl = "/api/szexpartnerek/" + table;
var colsUrl = rootUrl + "?" + q + "&pg.size=0";
if (window.location.protocol === "file:") {
    const baseFile = "example/" + table;
    rootUrl = baseFile + ".csv";
    colsUrl = baseFile + ".json";
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
    fmt_location: function (cell, formatterParams, onRendered) {
        var cells = cell.getData();
        var data = cell.getValue();
        try {
            data = JSON.parse(data);
        } catch (e) {
        }
        var mapslink = "";
        var q = data.formatted || data.name;
        if (data.bbox && data.bbox.lat1 && data.bbox.lon1) {
            if (data.bbox.lat2 && data.bbox.lon2) {
                var lat = (data.bbox.lat1 + data.bbox.lat2) / 2;
                var lon = (data.bbox.lon1 + data.bbox.lon2) / 2;
            } else {
                lat = data.bbox.lat1;
                lon = data.bbox.lon1;
            }
            mapslink = `/@${lat},${lon},14z`;
        } else if (q) {
            mapslink = `?q=${encodeURIComponent(q)}`;
        } else {
            return data;
        }
        return `<a target="map_${cells.partner_id}" href="https://www.google.com/maps${mapslink}">${q}</a>`;
    },
    fmt_call_number: function (cell, formatterParams, onRendered) {
        return "<a href=\"callto:" + cell.getValue() + "\">" + cell.getValue() + "</a>";
    },
};
tabulatorFunctions.fmt_partner_ext_rowid = tabulatorFunctions.fmt_partner_rowid;
tabulatorFunctions.fmt_partner_ext_view_rowid = tabulatorFunctions.fmt_partner_rowid;

function urlGenerator(url, config, params) {
    const query = [];
    if (params.page) query.push(`pg=${params.page}/${params.size}`);
    if (params.sort && params.sort.length > 0) query.push("o=" + encodeURIComponent(JSON.stringify(params.sort)));
    if (params.filter && params.filter.length > 0) query.push("w=" + encodeURIComponent(JSON.stringify(params.filter)));
    return url + '?' + q + (query.length ? "&" + query.join('&') : '');
}

function initializeFields(columns) {
    columns.forEach(function (col) {
        if (col.formatter === "datetime") {
            var params = tabulatorFunctions.datetime;
            col.formatterParams = Object.assign({}, col.formatterParams, params);
            col.sorter = "datetime";
        }
        var fs = tabulatorFunctions["fmt_" + table + "_" + col.field] || tabulatorFunctions["fmt_" + col.field];
        if (fs) col.formatter = fs;
        col.headerMenu = tabulatorFunctions.headerMenu;
    });
}

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

        tabulator = new Tabulator("#results-table", {
            layout: "fitColumns",
            persistenceMode: true, persistenceID: table, persistence: true,
            responsiveLayout: false,
            history: true,
            pagination: true,
            paginationInitialPage: 1,
            paginationSize: 25,
            paginationCounter: "rows",
            paginationMode: "remote",
            sortMode: "remote",
            filterMode: "remote",
            movableColumns: true,
            columnDefaults: {tooltip: true},
            columns: columns,
            initialSort: tabulatorSort,
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
                            return {
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
    }
});