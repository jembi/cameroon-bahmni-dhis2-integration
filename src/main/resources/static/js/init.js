var reportConfigUrl = '/bahmni_config/openmrs/apps/reports/reports.json';
var downloadUrl = '/dhis-integration/download?name=NAME&period=PERIOD';
var submitUrl = '/dhis-integration/submit-to-dhis';
var loginRedirectUrl = '/bahmni/home/index.html#/login?showLoginMessage&from=';
var logUrl = '/dhis-integration/log';
var spinner = spinner || {};

var period = "";
var hasReportingPrivilege = false;
var emptyPeriod = true;
var emptyComment = true;

$(document).ready(
		function() {
			isAuthenticated().then(isSubmitAuthorized).then(renderReport).then(
				registerOnchangeOnPeriod).then(registerOnchangeOnComment).then(getLogStatus);
		});

function isAuthenticated() {
	return $.get("is-logged-in").then(function(response) {
		if (response != 'Logged in') {
			window.location.href = loginRedirectUrl + window.location.href;
		}
	}).fail(function(response) {
		if (response && response.status != 200) {
			window.location.href = loginRedirectUrl;
		}
	});
}

function isSubmitAuthorized() {
	return $.get("hasReportingPrivilege").then(function(response) {
		hasReportingPrivilege = response;
		if (!hasReportingPrivilege) {
			$(".submit").remove();
		}
	});
}

function renderReport() {
	return $.get('html/programs.html').then(function(template) {
		var canSubmitReport = hasReportingPrivilege;
		return getContent(canSubmitReport).then(function(content) {
			$("#programs").html(Mustache.render(template, content));
		});
	});
}

function getContent(canSubmitReport) {
	return getDHISPrograms().then(function(programs) {
		
			return {
				period : period,
				programs : programs,
				canSubmitReport : canSubmitReport
			};
		
	});
}

function getDHISPrograms() {
	return $.getJSON(reportConfigUrl).then(function(reportConfigs) {
		var DHISPrograms = [];
		Object.keys(reportConfigs).forEach(function(reportKey) {
			if (reportConfigs[reportKey].DHISProgram) {
				reportConfigs[reportKey].index = DHISPrograms.length;
				DHISPrograms.push(reportConfigs[reportKey]);
			}
		});
		return DHISPrograms;
	});
}

function putStatus(data, index) {
	element('comment', index).html(data.comment).html();
	if (data.status == 'Success' || data.status == 'Complete') {
		var template = $('#success-status-template').html();
		Mustache.parse(template);
		element('status', index).html(Mustache.render(template, data));
		return;
	}
	var template = $('#failure-status-template').html();
	Mustache.parse(template);
	data.message = JSON.stringify(data.exception || data.response);
	element('status', index).html(Mustache.render(template, data));
	element('status', index).find('.status-failure').on('click', function() {
		alert(data.message);
		console.log(data.message);
	});
}

function download(index) {
	var period = element('period', index).val();
	var programName = element('program-name', index).html();
	var url = downloadUrl.replace('NAME', programName).replace('PERIOD', period);
	downloadCommon(url);
}

function downloadCommon(url) {
	var a = document.createElement('a');
	a.href = url;
	a.target = '_blank';
	a.click();
	return false;
}

function submit(index, attribute) {
	spinner.show();
	var period = element('period', index).val();
	var programName = element('program-name', index).html();
	var comment = element('comment', index).val();

	var parameters = {
		period : period,
		name : programName,
		comment : comment
	};

	disableBtn(element('submit', index));
	var submitTo = submitUrl;
	$.get(submitTo, parameters).done(function(data) {
		data = JSON.parse(data)
		if (!$.isEmptyObject(data)) {
			putStatus(data, index);
		}
	}).fail(function(response) {
		if (response.status == 403) {
			putStatus({
				status : 'Failure',
				exception : 'Not Authenticated'
			}, index);
		}
		putStatus({
			status : 'Failure',
			exception : response
		}, index);
	}).always(function() {
		enableBtn(element('submit', index));
		spinner.hide();
	});
}

function confirmAndSubmit(index, attribute) {
	if (confirm("This action cannot be reversed. Are you sure, you want to submit?")) {
		submit(index, attribute);
	}
}

function getStatus(index) {
	var programName = element('program-name', index).html();
	var period = element('period', index).val();

	var parameters = {
		programName : programName,
		period : period
	};
	spinner.show();
	$.get(logUrl, parameters).done(function(data) {
		data = JSON.parse(data);
		if ($.isEmptyObject(data)) {
			element('comment', index).html('');
			element('status', index).html('');
		} else {
			putStatus(data, index);
		}
	}).fail(function(response) {
		console.log("failure response");
		if (response.status == 403) {
			putStatus({
				status : 'Failure',
				exception : 'Not Authenticated'
			}, index);
		}
		putStatus({
			status : 'Failure',
			exception : response
		}, index);
	}).always(function() {
		spinner.hide();
	})
}

function element(name, index) {
	var id = name + '-' + index;
	return $('[id="' + id + '"]');
}

function enableBtn(btn) {
	return btn.attr('disabled', false).removeClass('btn-disabled');
}

function disableBtn(btn) {
	return btn.attr('disabled', true).addClass('btn-disabled');
}

function disableAllSubmitBtns() {
	disableBtn($("[id*='submit-']"));
}

function registerOnchangeOnComment() {
	disableAllSubmitBtns();
	$("[id*='comment-']").on('change keyup paste', function(event) {
		var index = $(event.target).attr('index');
		if ($(event.target).val().trim() != "") {
			emptyComment = false;
		} else {
			emptyComment = true;
		}

		if (emptyPeriod || emptyComment) {
			disableBtn(element('submit', index));
		} else {
			enableBtn(element('submit', index));
		}
	});
}

function registerOnchangeOnPeriod() {
	disableAllSubmitBtns();
	$("[id*='period-']").on('change keyup paste', function(event) {
		var index = $(event.target).attr('index');
		if ($(event.target).val().trim() != "") {
			emptyPeriod = false;
		} else {
			emptyPeriod = true;
		}

		if (emptyPeriod || emptyComment) {
			disableBtn(element('submit', index));
		} else {
			enableBtn(element('submit', index));
		}
	});
}

function getLogStatus() {
	$('#programs .month-selector').each(function(index) {
		getStatus(index);
	});
}
