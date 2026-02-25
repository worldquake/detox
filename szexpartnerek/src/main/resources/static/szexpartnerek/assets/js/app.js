const url = new URL(location.href);
const table = url.searchParams.get('table') || "partner";
var rootUrl = "/api/szexpartnerek/" + table;
var colsUrl = rootUrl + "?pg.size=0";
if (window.location.protocol === "file:") {
    rootUrl = rootUrl.split("/");
    rootUrl = "../" + rootUrl[rootUrl.length - 1] + ".csv";
    colsUrl = rootUrl + ".json";
}

jQuery.get({
    url: colsUrl,
    dataType: 'json',
    success: function (def) {
        // Prepare columns and initialSort
        var columns = def.cols || [];
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
                query.push("t=" + encodeURIComponent(table));
                return url + (query.length ? '?' + query.join('&') : '');
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
    }
});