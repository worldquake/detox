new Tabulator("#test-table", {
            layout: "fitColumns",
            responsiveLayout: false,
            history: true,
            pagination: "remote",
            paginationMode: "remote",
            paginationInitialPage: 1,
            paginationSize: 25,
            paginationCounter: "rows",
            sortMode: "remote",
  height: 205, // set height of table (in CSS or here), this enables the Virtual DOM and improves render speed dramatically (can be any valid css height value)
  ajaxURL: "a.json",
  layout: "fitColumns", //fit columns to width of table (optional)
  columns: [
    //Define Table Columns
    { title: "Name", field: "name", width: 150 },
    { title: "Age", field: "age", hozAlign: "left", formatter: "progress" },
    { title: "Favourite Color", field: "col" },
    { title: "Date Of Birth", field: "dob", sorter: "date", hozAlign: "center" }
  ],
  sortMode: "remote",
  ajaxRequestFunc: function (url, config, params) {
    alert("ajaxRequestFunc called");
  }
});
