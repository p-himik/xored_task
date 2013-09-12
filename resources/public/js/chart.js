function getData(type, params, fn) {
	if ($.isFunction(params)) {
		fn = params;
		params = undefined;
	}
	var url = "/get-data/" + type;
	if (params) {
		url += '?' + $.param(params);
	}
	$.getJSON(url, fn);
}

function sendAction(type, params) {
	var url = "/action/" + type;
	if (params) {
		url += '?' + $.param(params);
	}
	$.getJSON(url);
}

var tasks = [{intervals: []}];/*[
    {intervals: [
        {from: 1, to: 3, label: 'Breakfast'},
        {from: 22, to: 24},
        {from: 7, to: 8}},]}];*/

var wnToSeries = {};
var wnToN = {};
var defaultColors = [
   '#2f7ed8', 
   '#0d233a', 
   '#8bbc21', 
   '#910000', 
   '#1aadce', 
   '#492970',
   '#f28f43', 
   '#77a1e5', 
   '#c42525', 
   '#a6c96a'
];
var currentColor = 0;
var pnToColor = {};

function tasksToSeries(tasks) {
    var series = [];
    $.each(tasks.reverse(), function(i, task) {
        var item = {
            name: task.name,
            data: [],
			color: task.color
        };
        $.each(task.intervals, function(j, interval) {
			var y = (task.y == null ? i : task.y);
            item.data.push({
                x: interval.from,
                y: y,
				color: task.color,
                from: interval.from,
                to: interval.to
            }, {
                x: interval.to,
                y: y,
				color: task.color,
                from: interval.from,
                to: interval.to
            });
            
            // add a null value between intervals
            if (task.intervals[j + 1])
                item.data.push([(interval.to + task.intervals[j + 1].from) / 2, null]);
        });
    
        series.push(item);
    });
    return series;
}

var firstEventTime = 0;
var lastEventN = 0;
var starts = {};

function updateEvents() {
	getData('events', {len: lastEventN}, function(data) {
		for (var i = 0; i < data.length; i++) {
			var e = data[i];
			if (firstEventTime == 0)
				firstEventTime = parseInt(e.time);
			switch (e.type) {
				case "project-add":
					addProject(e.data.name);
				break;
				case "project-finish":
					finishProject(e.data.name);
				break;
				case "task-start":
					var pn = e.data['project-name'];
					if (!starts[pn])
						starts[pn] = {};
					starts[pn][e.data['name']] = parseInt(e.time);
				break;
				case "task-finish":
					var pn = e.data['project-name'];
					var tn = e.data['name'];
					if (!starts[pn]) {
						starts[pn] = {};
						starts[pn][tn] = firstEventTime;
					}
					addTaskToChart(e.data['worker-name'], pn, tn, starts[pn][tn], parseInt(e.time));
				break;
				case "worker-add":
					addWorker(e.data.name);
				break;
				case "worker-remove":
					removeWorker(e.data.name);
				break;
				case "exception":
					alert("Exception: " + e.data.text);
					return;
				default:
					alert("Uknown event type: " + e.type);
					return;
				break;
			}
		}
		lastEventN += data.length;
		setTimeout(updateEvents, $("#update-interval").val());
	});
}

function addProject(pn) {
	++currentColor;
	if (currentColor >= defaultColors.length)
		currentColor = 0;
	pnToColor[pn] = defaultColors[currentColor];
	$('#projects').append(new Option(pn, pn));
	$('#projects option[value="' + pn + '"]')
		.css("background-color", pnToColor[pn])
		.css("color", "white");
}

function finishProject(pn) {
	$('#projects option[value="' + pn + '"]').remove();
	$('#completed-projects').append(new Option(pn, pn));
	$('#completed-projects option[value="' + pn + '"]')
		.css("background-color", pnToColor[pn])
		.css("color", "white");
}

function addWorker(wn) {
	var chart = $('#container').highcharts();
	var workerN = Object.keys(wnToN).length;
	wnToN[wn] = workerN;
	var cats = chart.yAxis[0].categories;
	if (typeof cats == 'boolean')
		cats = [];
	cats[workerN] = wn;
	chart.yAxis[0].setCategories(cats);
	chart.yAxis[0].setExtremes(-0.5, workerN + 1 - 0.5);
	$('#workers').append(new Option(wn, wn));
}

function removeWorker(wn) {
	console.log(wn);
	$('#workers option[value="' + wn + '"]').remove();
}

function addTaskToChart(wn, pn, tn, ts, te) {
	var chart = $('#container').highcharts();
	var workerN = wnToN[wn];
	var tasks = [{name: pn, y: workerN, color: pnToColor[pn], intervals: [{from: ts, to: te, label: pn}]}];
	var series = tasksToSeries(tasks);
	var s = wnToSeries[workerN];
	if (!s)
		s = chart.addSeries(series[0]);
	else
		for (var i = 0; i < series[0].data.length; ++i) {
			s.addPoint([series[0].data[i].from, null]);
			s.addPoint(series[0].data[i]);
		}
}

function init() {
	var c = $('#container').highcharts();
	c.yAxis[0].watch('translationSlope', function(prop, oldval, val) {
		if (oldval != val) {
			c.options.plotOptions.line.lineWidth = val;
			c.options.plotOptions.line.states.hover.lineWidth = val;
			c.series.forEach(function(s) {
				s.options.lineWidth = val;
				s.options.states.hover.lineWidth = val;
				s.state = "dirty";
				s.setState();
			});
		}
	});	

	getData('len', function(data) {
		lastEventN = data;
		updateEvents();
	});
	
	getData('workers', function(data) {
		for (var i = 0; i < data.length; i++)
			addWorker(data[i]);
	});
	
	getData('projects', function(data) {
		for (var i = 0; i < data.length; i++)
			$('#projects').append(new Option(data[i], data[i]));
	});
}

// re-structure the tasks into line seriesvar series = [];
var series = tasksToSeries(tasks);


var i = 30;
var c;

$(function() {
	$('#container').highcharts({
		chart: {
			borderWidth: 2,
			//marginTop: 40,
			zoomType: 'x',
			animation: false
		},
		title: {
			text: null
		},
		xAxis: {
			labels: {
				formatter: function () {
					return (this.value - firstEventTime) / 1000;
					//function pad(n) {return n < 10 ? '0' + n : n;}
					//function padMs(ms) {return ms < 10 ? '00' + ms : (ms < 100 ? '0' + ms : ms);}
					//var d = new Date(this.value);
					//return pad(d.getUTCMinutes()) + ':'
					//+ pad(d.getUTCSeconds()) + '.'
					//+ padMs(d.getUTCMilliseconds());
				}
			},
			tickInterval: 1000
			//tickPixelInterval: 10
		},
		yAxis: {
			type: 'category',
			endOnTick: false,
			startOnTick: false,
			showLastLabel: true,
			gridLineWidth: 2,
			gridZIndex: 10,
			events: {
				afterSetExtremes: function(e) {
					if (e.min == 0) {
						console.log(this);
						this.setExtremes(-0.5, this.dataMax + 0.5);
					}
				}
			}
		},
		tooltip: {
			enabled: false
		},
		legend: {
			enabled: false
		},
		scrollbar: {
			enabled: true
		},
		navigation: {
            buttonOptions: {
                align: 'left',
				verticalAlign: 'bottom'
            }
        },
		plotOptions: {
			line: {
				animation: false,
				zIndex: 1,
				marker: {
					enabled: false
				},
				dataLabels: {
					enabled: false,
					align: 'left',
					formatter: function() {
						return this.point.options && this.point.options.label;
					}
				}
			}
		},
		series: series
	});
	
	c = $('#container').highcharts();
	
	$('#add-workers').click(function() {
		sendAction('add-workers', {n: $('#workers-amount').val()});
	});
	
	$('#remove-workers').click(function() {
		sendAction('remove-workers', {names: $('#workers').val()});
	});
	
	$('#task-type').change(function() {
		var tt = $('#task-type').val();
		var code = '; Provided code will be wrapped in\n' +
				   '; (fn [task-name] ...)\n' +
				   '; sleep function merely calls Thread/sleep\n' +
				   '; and catches InterruptedException in case\n' +
				   '; the task was interrupted (e.g. a worker has\n' +
				   '; been deleted.\n\n';
		switch ($('#task-type').val()) {
			case "fixed":
				code += '(let [ms-to-wait 500]\n' +
						'  (Thread/sleep ms-to-wait))';
			break;
			case "random":
				code += '(let [min-ms-to-wait 100\n' +
						'      max-ms-to-wait 1000]\n' +
						'  (Thread/sleep\n' +
						'    (+ (rand (- max-ms-to-wait min-ms-to-wait))\n' +
						'       min-ms-to-wait)))';
			break;
		}
		$('#task-code').val(code);
	}).change();
	
	$('#add-projects').click(function() {
		sendAction('add-projects', {
			"pref-time": $('#pref-time').val(),
			n: $('#projects-amount').val(),
			"tasks-n-type": $('#tasks-in-project-type').val(),
			"tasks-n": $('#tasks-in-project-amount').val(),
			code: $('#task-code').val()
		});
	});
	
	init();
});
