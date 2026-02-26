const url = new URL(location.href);
const table = url.searchParams.get('t') || "partner";
const prj = url.searchParams.get('p') || "*";
const q = "p=" + encodeURIComponent(prj) + "&t=" + encodeURIComponent(table);
var rootUrl = "/api/szexpartnerek/" + table;
var colsUrl = rootUrl + "?" + q + "&pg.size=0";
if (window.location.protocol === "file:") {
    const baseFile = "example/" + table;
    rootUrl = baseFile + ".csv";
    colsUrl = baseFile + ".json";
}

function tableDone(x) {
    const t = window.table;
    t.hideColumn("name");
}

function setField(columns, what) {
    columns.forEach(function (col) {
        // If this column uses the datetime formatter
        if (col.formatter === what) {
            var params = window.stuff[what];
            col.formatterParams = Object.assign({}, col.formatterParams, params);
            col.sorter = what;
        }
        var fs = window.stuff["fmt_" + table + "_" + col.field] || window.stuff["fmt_" + col.field];
        if (fs) col.formatter = fs;
        // If this column has subcolumns (nested columns)
        if (col.columns) {
            setDatetimeFormatterParams(col.columns, what);
        }
    });
}

window.stuff = {
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
        var data = cell.getData();
        return `<a target="p_${id}" href="https://rosszlanyok.hu/rosszlanyok.php?pid=szexpartner-data&id=${id}">${data.name} (${id})</a>`;
    },
    fmt_location: function (cell, formatterParams, onRendered) {
        var cells = cell.getData();
        var data = JSON.parse(cell.getValue());
        var mapslink = "";
        if (data.bbox && data.bbox.lat1 && data.bbox.lon1) {
            if (data.bbox.lat2 && data.bbox.lon2) {
                var lat = (data.bbox.lat1 + data.bbox.lat2) / 2;
                var lon = (data.bbox.lon1 + data.bbox.lon2) / 2;
            } else {
                lat = data.bbox.lat1;
                lon = data.bbox.lon1;
            }
            mapslink = `https://www.google.com/maps/@${lat},${lon},14z`;
        } else if (data.formatted) {
            mapslink = `https://www.google.com/maps?q=${encodeURIComponent(data.formatted)}`;
        } else {
            return data.formatted;
        }

        return `<a target="map_${cells.partner_id}" href="${mapslink}">${data.formatted}</a>`;
    },
    fmt_call_number: function (cell, formatterParams, onRendered) {
        return "<a href=\"callto:" + cell.getValue() + "\">" + cell.getValue() + "</a>";
    },
};
window.stuff.fmt_partner_ext_rowid = window.stuff.fmt_partner_rowid;
window.stuff.fmt_partner_ext_view_rowid = window.stuff.fmt_partner_rowid;
jQuery.get({
    url: colsUrl,
    dataType: 'json',
    success: function (def) {
        // Prepare columns and initialSort
        var columns = def.cols || [];
        setField(columns, "datetime");
        var initialSort = def.sort || [];

        // Convert sort array to Tabulator format if present
        var tabulatorSort = [];
        if (initialSort && initialSort.length) {
            initialSort.forEach(function (s) {
                var parts = s.split(' ');
                if (parts.length === 2) {
                    tabulatorSort.push({column: parts[0], dir: parts[1] === 'desc' ? 'desc' : 'asc'});
                }
            });
        }

        // Initialize Tabulator
        window.table = new Tabulator("#results-table", {
            layout: "fitColumns",
            responsiveLayout: false,
            history: true,
            pagination: true,
            paginationInitialPage: 1,
            paginationSize: 25,
            paginationCounter: "rows",
            paginationMode: "remote",
            sortMode: "remote",
            filterMode: "remote",
            ajaxURL: rootUrl,
            ajaxConfig: {
                method: "GET",
                headers: {
                    "Accept": "text/csv"
                }
            },
            movableColumns: true,
            columnDefaults: {tooltip: true},
            columns: columns,
            initialSort: tabulatorSort,
            ajaxURLGenerator: function (url, config, params) {
                const query = [];
                if (params.page) query.push(`pg=${params.page}/${params.size}`);
                if (params.sort && params.sort.length > 0) query.push("o=" + encodeURIComponent(JSON.stringify(params.sort)));
                if (params.filter && params.filter.length > 0) query.push("w=" + encodeURIComponent(JSON.stringify(params.filter)));
                return url + '?' + q + (query.length ? "&" + query.join('&') : '');
            },
            ajaxRequestFunc: function (url, config, params) {
                url = window.table.options.ajaxURLGenerator(url, config, params);
                return fetch(url)
                    .then(response => {
                        const hds = response.headers;
                        return response.text().then(data => {
                            const impm = window.table.modules.import
                            const imp = impm.lookupImporter("csv");
                            return impm.importData(imp, data).then(parsedData => {
                                const keys = parsedData.shift(); // Fejléc sor kiszedése
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
        window.table.on("tableBuilt", tableDone);
    }
});