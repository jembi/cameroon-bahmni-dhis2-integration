package com.possible.dhis2int.web;

import static com.possible.dhis2int.audit.Submission.Status.Failure;
import static com.possible.dhis2int.web.Cookies.BAHMNI_USER;
import static com.possible.dhis2int.web.Messages.CONFIG_FILE_NOT_FOUND;
import static com.possible.dhis2int.web.Messages.DHIS_SUBMISSION_FAILED;
import static com.possible.dhis2int.web.Messages.FILE_READING_EXCEPTION;
import static com.possible.dhis2int.web.Messages.REPORT_DOWNLOAD_FAILED;
import static java.lang.String.format;
import static org.apache.log4j.Logger.getLogger;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.possible.dhis2int.Properties;
import com.possible.dhis2int.audit.Recordlog;
import com.possible.dhis2int.audit.Submission;
import com.possible.dhis2int.audit.Submission.Status;
import com.possible.dhis2int.audit.SubmissionLog;
import com.possible.dhis2int.audit.SubmittedDataStore;
import com.possible.dhis2int.date.DateConverter;
import com.possible.dhis2int.date.ReportDateRange;
import com.possible.dhis2int.db.DatabaseDriver;
import com.possible.dhis2int.db.Results;
import com.possible.dhis2int.dhis.DHISClient;
import com.possible.dhis2int.exception.NotAvailableException;

@RestController
public class DHISIntegrator {

	private final Logger logger = getLogger(DHISIntegrator.class);

	private final String DOWNLOAD_FORMAT = "application/vnd.ms-excel";

	private final String SUBMISSION_ENDPOINT = "/api/dataValueSets";

	private final DHISClient dHISClient;

	private final DatabaseDriver databaseDriver;

	private final Properties properties;

	private final SubmissionLog submissionLog;

	private final SubmittedDataStore submittedDataStore;

	@Autowired
	public DHISIntegrator(DHISClient dHISClient, DatabaseDriver databaseDriver, Properties properties,
			SubmissionLog submissionLog, SubmittedDataStore submittedDataStore) {
		this.dHISClient = dHISClient;
		this.databaseDriver = databaseDriver;
		this.properties = properties;
		this.submissionLog = submissionLog;
		this.submittedDataStore = submittedDataStore;
	}

    @RequestMapping(path = "submit-to-dhis")
    public String submitToDHIS(@RequestParam("name") String program,
            @RequestParam("period") String period,
			HttpServletRequest clientReq, HttpServletResponse clientRes)
			throws IOException, JSONException {
		String userName = new Cookies(clientReq).getValue(BAHMNI_USER);
		Submission submission = new Submission();
		String filePath = submittedDataStore.getAbsolutePath(submission);
		Status status;
		try {
            submitToDHIS(submission, program, period);
			status = submission.getStatus();
		} catch (DHISIntegratorException | JSONException e) {
			status = Failure;
			submission.setException(e);
			logger.error(DHIS_SUBMISSION_FAILED, e);
		} catch (Exception e) {
			status = Failure;
			submission.setException(e);
			logger.error(Messages.INTERNAL_SERVER_ERROR, e);
		}

		submittedDataStore.write(submission);
        submissionLog.log(program, userName, "Daily schedule", status, filePath);
        // recordLog(userName, program, day, day.toString(), submission.getInfo(), status, "");

		return submission.getInfo();
	}

	@RequestMapping(path = "/is-logged-in")
	public String isLoggedIn() {
		logger.info("Inside isLoggedIn");
		return "Logged in";
	}
	
	@RequestMapping(path = "/hasReportingPrivilege")
	public Boolean hasReportSubmissionPrivilege(HttpServletRequest request, HttpServletResponse response) {
    	return dHISClient.hasDhisSubmitPrivilege(request, response);
    }
	
	@RequestMapping(path = "/submit-to-dhis_report_status")
    public String submitToDHISLOG(@RequestParam("name") String program,
            @RequestParam("period") String period, @RequestParam("comment") String comment,
            HttpServletRequest clientReq, HttpServletResponse clientRes)
            throws IOException, JSONException {
		String userName = new Cookies(clientReq).getValue(BAHMNI_USER);
		Submission submission = new Submission();
		Status status;
		try {
            submitToDHIS(submission, program, period);
			status = submission.getStatus();
		} catch (DHISIntegratorException | JSONException e) {
			status = Failure;
			submission.setException(e);
			logger.error(DHIS_SUBMISSION_FAILED, e);
		} catch (Exception e) {
			status = Failure;
			submission.setException(e);
			logger.error(Messages.INTERNAL_SERVER_ERROR, e);
		}
		submittedDataStore.write(submission);

        // recordLog(userName, program, year, month, submission.getInfo(), status, comment);
		return submission.getInfo();
	}

	private String recordLog(String userName, String program, Integer year, Integer month, String log, Status status,
			String comment) throws IOException, JSONException {
		Date date = new Date();
		Status submissionStatus = status;
		if (status == Status.Failure) {
			submissionStatus = Status.Incomplete;
		} else if (status == Status.Success) {
			submissionStatus = Status.Complete;
		}
		Recordlog recordLog = new Recordlog(program, date, userName, log, submissionStatus, comment);
		databaseDriver.recordQueryLog(recordLog, month, year);
		return "Saved";
	}

	@RequestMapping(path = "/log")
	public String getLog(@RequestParam String programName, @RequestParam("year") Integer year,
			@RequestParam("month") Integer month) throws SQLException {
		logger.info("Inside getLog method");
		return databaseDriver.getQuerylog(programName, month, year);
	}

	@RequestMapping(path = "/submission-log/download", produces = "text/csv")
	public FileSystemResource downloadSubmissionLog(HttpServletResponse response) throws FileNotFoundException {
		response.setHeader("Content-Disposition", "attachment; filename=" + submissionLog.getDownloadFileName());
		return submissionLog.getFile();
	}

	@RequestMapping(path = "/download")
	public void downloadReport(@RequestParam("name") String name, @RequestParam("period") String period, HttpServletResponse response)
            throws JSONException, IOException {
		ReportDateRange reportDateRange = DateConverter.getDateRange(period);
		try {
			String redirectUri = UriComponentsBuilder.fromHttpUrl(properties.reportsUrl)
					.queryParam("responseType", DOWNLOAD_FORMAT).queryParam("name", name)
					.queryParam("startDate", reportDateRange.getStartDate())
					.queryParam("endDate", reportDateRange.getEndDate()).toUriString();
			response.sendRedirect(redirectUri);

		} catch (Exception e) {
			logger.error(format(REPORT_DOWNLOAD_FAILED, name), e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

    private Submission submitToDHIS(Submission submission, String name, String period)
            throws JSONException, IOException {
		JSONObject reportConfig = getConfig(properties.reportsJson);

		List<JSONObject> childReports = new ArrayList<JSONObject>();
		childReports = jsonArrayToList(reportConfig.getJSONObject(name).getJSONObject("config").getJSONArray("reports"));

		JSONObject dhisConfig = getDHISConfig(name);

        ReportDateRange dateRange = DateConverter.getDateRange(period);

		List<Object> programDataValue = getProgramDataValues(childReports, dhisConfig.getJSONObject("reports"),
				dateRange);

		JSONObject programDataValueSet = new JSONObject();
		programDataValueSet.put("orgUnit", dhisConfig.getString("orgUnit"));
		programDataValueSet.put("dataValues", programDataValue);
        programDataValueSet.put("period", period);

		ResponseEntity<String> responseEntity = dHISClient.post(SUBMISSION_ENDPOINT, programDataValueSet);
		submission.setPostedData(programDataValueSet);
		submission.setResponse(responseEntity);
		return submission;
	}

	private JSONObject getConfig(String configFile) throws DHISIntegratorException {
		try {
			return new JSONObject(new JSONTokener(new FileReader(configFile)));
		} catch (FileNotFoundException e) {
			throw new DHISIntegratorException(format(CONFIG_FILE_NOT_FOUND, configFile), e);
		}
	}

	private Results getResult(String sql, String type, ReportDateRange dateRange) throws DHISIntegratorException {
		String formattedSql = sql.replaceAll("#startDate#", dateRange.getStartDate()).replaceAll("#endDate#",
				dateRange.getEndDate());
		return databaseDriver.executeQuery(formattedSql, type);
	}

	private String getContent(String filePath) throws DHISIntegratorException {
		try {
			return Files.readAllLines(Paths.get(filePath)).stream().reduce((x, y) -> x + "\n" + y).get();
		} catch (IOException e) {
			throw new DHISIntegratorException(format(FILE_READING_EXCEPTION, filePath), e);
		}
	}

	private List<Object> getProgramDataValues(List<JSONObject> reportSqlConfigs, JSONObject reportDHISConfigs,
			ReportDateRange dateRange) throws DHISIntegratorException, JSONException, IOException {
		ArrayList<Object> programDataValues = new ArrayList<>();

		for (JSONObject report : reportSqlConfigs) {
			JSONArray dataValues = getReportDataElements(reportDHISConfigs, dateRange, report);
			programDataValues.addAll(jsonArrayToList(dataValues));
		}
		return programDataValues;
	}

	private JSONArray getReportDataElements(JSONObject reportDHISConfigs, ReportDateRange dateRange, JSONObject report)
			throws DHISIntegratorException, JSONException, IOException {
		JSONArray dataValues = new JSONArray();
		try {
			dataValues = reportDHISConfigs.getJSONObject(report.getString("name")).getJSONArray("dataValues");
		} catch (JSONException e) {
			throw new DHISIntegratorException(e.getMessage(), e);
		}
		String sqlPath = report.getJSONObject("config").getString("sqlPath");
		String type = report.getString("type");
		Results results = getResult(getContent(sqlPath), type, dateRange);
		for (Object dataValue_ : jsonArrayToList(dataValues)) {
			JSONObject dataValue = (JSONObject) dataValue_;
			updateDataElements(results, dataValue);
		}
		return dataValues;
	}

	private List<JSONObject> jsonArrayToList(JSONArray elements) {
		List<JSONObject> list = new ArrayList<>();
		elements.forEach((element) -> list.add((JSONObject) element));
		return list;
	}

	private void updateDataElements(Results results, JSONObject dataElement) throws JSONException {
		String value = results.get(dataElement.getInt("row"), dataElement.getInt("column"));
		dataElement.put("value", value);
	}

	private JSONObject getDHISConfig(String programName) throws DHISIntegratorException {
		String DHISConfigFile = properties.dhisConfigDirectory + programName.replaceAll(" ", "_") + ".json";
		return getConfig(DHISConfigFile);
	}

}