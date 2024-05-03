package org.frontier.teamcity.testRailIntegration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.users.SimplePropertyKey;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.util.SessionUser;
import org.apache.log4j.MDC;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.web.servlet.ModelAndView;

import javax.crypto.Cipher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TRIntegrationController extends BaseController{

    private final PluginDescriptor m_descriptor;
    private final SBuildServer m_server;
    private static Logger log;  //should be final - we only assign in constructor, but TC doesn't like that?

    public TRIntegrationController(WebControllerManager manager,
                                   PluginDescriptor descriptor,
                                   SBuildServer server,
                                   ServerPaths paths)
            throws IOException{

        log = new Logger(TRIntegrationController.class.getName(), paths);
        manager.registerController(Constants.PAGE_ADDRESS, this);

        m_descriptor = descriptor;
        m_server = server;

        log.info("Started TRIntegrationController");
    }


    @Nullable
    @Override
    protected ModelAndView doHandle(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse) throws Exception {
        SUser user = SessionUser.getUser(httpServletRequest);
        String sessionId = httpServletRequest.getSession().getId();
        MDC.put("username",user.getUsername());
        MDC.put("sessionId", sessionId);
        String queryPart = httpServletRequest.getQueryString() != null ? String.format("?%s", httpServletRequest.getQueryString()) : "";
        String fullUrl = String.format("%s%s",httpServletRequest.getRequestURL(),queryPart);

        log.info("Accessed page %s",fullUrl);

        ModelAndView mv = new ModelAndView(m_descriptor.getPluginResourcesPath("mainpage.jsp"));

        String runId = httpServletRequest.getParameter("runId");
        String serverUrl = httpServletRequest.getParameter("TRUrl");
        String mappingJson = httpServletRequest.getParameter("mappingJson");

        String apiKey = httpServletRequest.getParameter("apiKey");
        if (apiKey != null){ user.setUserProperty(new SimplePropertyKey("apiKey"), apiKey);}
        else{apiKey = user.getPropertyValue(new SimplePropertyKey("apiKey"));}

        String params = "";
        for(Map.Entry<String, String[]> param :httpServletRequest.getParameterMap().entrySet()){
            params += param.getKey() + ":\n";
            for(String val : param.getValue()){
                params += "\t" + val + "\n";
            }
        }
        log.info(params);

        String userEmail = SessionUser.getUser(httpServletRequest).getEmail();

        if(mappingJson != null && !mappingJson.isEmpty() && runId != null && apiKey != null){
            log.info("Form filled out, processing...");
            try {
                process(mappingJson,
                        userEmail,
                        apiKey,
                        serverUrl,
                        Integer.parseInt(runId)
                );
            }
            catch(IOException ex){
                ModelAndView mvError = new ModelAndView(m_descriptor.getPluginResourcesPath("errorpage.jsp"));
                mvError.addObject("recentLog", log.getRecentMessages());
                mvError.addObject("exception", ex);
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                ex.printStackTrace(new PrintStream(stream));
                String stackTrace = stream.toString();
                mvError.addObject("stackTrace", stackTrace);
                return mvError;
            }
        }


        mv.addObject("userEmail", userEmail);
        mv.addObject("recentLog", log.getRecentMessages());
        mv.addObject("apiKey", apiKey);
        //Show debug info if sysadmin or requested.
        mv.addObject("debug",
                httpServletRequest.getParameter("debug") != null
                || SessionUser.getUser(httpServletRequest).isSystemAdministratorRoleGranted());

        return mv;
    }



    public void process(String jsonMap, String username, String password, String trURL, Integer runId) throws IOException {

        //Check mapping json is valid json
        final Map<Integer,Collection<String>> testBuildMap =  parseMap(jsonMap); //Throws com.google.gson.JsonParseException

        TRClient client = new TRClient(trURL, username, password);

        //Check can access TR
        client.tryConnect(); //throws IOException if we can't connect
        log.info("Client Connected");
        //Check run exists
        //Get all the test cases in the run

        Collection<Test> tests;
        try {
            tests = client.get_tests(runId.toString());
        } catch (Exception ex){
            log.error("Failed to get tests for runId %s", runId);
            throw ex;
        }
        Collection<Integer> caseIds = tests.stream().map(t -> t.case_id).collect(Collectors.toList());
        log.info("Got tests for Run %s", runId);
        //Error if test case is in run more than once
        if(Utils.hasDuplicates(caseIds)){
            throw new RuntimeException(String.format("Run ID %s has more than one test with the same caseId(s) (%s).  Can't tell which one to update", runId, Utils.getDuplicates(caseIds)));
        }
        log.info("No duplicates found");
        //Get the subset of the mapping for which the run has test cases
        testBuildMap.keySet().retainAll(caseIds);

        //Get the build configs those cases require
        Collection<String> buildConfigIds = new ArrayList<>();
        testBuildMap.values().forEach(buildConfigIds::addAll);

        //Get the latest finished builds of those configs
        Collection<SFinishedBuild> builds = getLatestBuildsForConfigs(buildConfigIds);

        Map<String,SFinishedBuild> buildTypeToUseableBuild = builds.stream().collect(Collectors.toMap(
                b -> b.getBuildTypeExternalId(),
                b-> b
        ));
        log.info("Relevant Builds in the last day %s", buildTypeToUseableBuild.keySet());


        //TODO:  Fix this - this ignores them *entirely* when we probably want to instead flag this up
       Map <Integer, Collection<SFinishedBuild>> casesToBuilds = testBuildMap.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey(),
                entry -> entry.getValue().stream().map(v -> Utils.tryGet(buildTypeToUseableBuild,v)).collect(Collectors.toList())
        ));

        log.info("Cases to Builds map: %s", casesToBuilds);

        //Go through the mapping and get a {status, comment, version, device} mapping
        Map<Integer, TRResult> results = casesToBuilds.entrySet().stream().collect(Collectors.toMap(
           entry -> entry.getKey(),
           entry -> toTRResult(entry.getValue())
        ));


        //upload this

        client.add_results_for_cases(runId, results);

    }

    private Map<Integer,Collection<String>> parseMap(String json){
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String,Collection<String>>>(){}.getType();
        Map<String,Collection<String>> result = gson.fromJson(json, mapType);
        Map<Integer, Collection<String>> parsed = result.entrySet().stream()
                .collect(Collectors.toMap(
                        entry-> Integer.parseInt(entry.getKey()),
                        entry -> entry.getValue()
                ));
        return parsed;
    }

    private Collection<SFinishedBuild> getLatestBuildsForConfigs(Collection<String> buildConfigIds){
        return buildConfigIds.stream()
                .map(s->m_server.getProjectManager().findBuildTypeByExternalId(s))
                .map(SBuildType::getLastChangesFinished)
                .collect(Collectors.toList());

    }

    private TRResult toTRResult(Collection<SFinishedBuild> builds){
        Map<String, String> platformMap = new HashMap<String, String>() {{
            put("xb1", "Xbox One");
            put("win64", "Windows");
            put("ps4", "PS4");
        }};
        String platforms = builds.stream()
                .map(b-> b.getBuildOwnParameters().getOrDefault("platform",""))
                .collect(Collectors.joining(", "));

        String version = builds.stream().map(b-> Integer.parseInt(b.getBuildNumber()))
                .min(Integer::compare).get().toString();

        boolean passed = builds.stream().allMatch(b->b.getStatusDescriptor().isSuccessful());

        //TODO: Make this less hacky
        int status = passed ? 1 : 5;  //the numeric constants for TR's Passed/fail statuses

        //A link to the build and it's status
        String comment = builds.stream().map(b->
                String.format(
                        "[%s](%s/viewLog.html?buildId=%s) %s at %s",
                        b.getBuildTypeExternalId(),
                        m_server.getRootUrl(),
                        b.getBuildId(),
                        b.getBuildStatus().getText(),
                        b.getFinishDate()
                )).collect(Collectors.joining("\n"));

        return new TRResult(status, comment, version, platforms);
    }

    private enum TRResultStatus{
        PASSED, BLOCKED, UNTESTED, RETEST, FAILED
    }

    private class TRClient{
        private final String m_server;

        private final String basicAuth;
        TRClient(String server, String username, String apiKey){
            m_server = server;
            basicAuth = "Basic " +  new String(
                    Base64.getEncoder().encode((username + ":" + apiKey).getBytes())
            );
        }

        boolean canConnect(){
            try {
                this.tryConnect();
            }
            catch(Exception ex){
                return false;
            }
            return true;
        }

        void tryConnect() throws IOException {this.Get("index.php?/api/v2/get_projects");}

        public List<Test> get_tests(String runId){
            Type collClass = new TypeToken<ArrayList<Test>>(){}.getType();
            log.info("Collection class %s", collClass.getTypeName());
            String json;
            try {
                json = this.Get(String.format("index.php?/api/v2/get_tests/%s", runId));
            }catch(IOException ex){ throw new RuntimeException(ex);}
            Gson gson = new Gson();
            List<Test> result =  gson.fromJson(json, collClass);
            log.info(result.toString());
            return result;
        }

        private String Get(String uri)  throws IOException{
            URL endpoint  = new URL(m_server + "/" + uri);
            log.info("Accessing %s",endpoint);
            try{
                    HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                    conn.setConnectTimeout(5000);
                    //add authentication stuff
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty ("Authorization", basicAuth);
                    log.info("Start request to %s", endpoint.toString());

                    Integer responseCode = conn.getResponseCode();
                    log.info("response code: %s", responseCode);
                    BufferedReader bufferedReader;
                    if (isGoodResponseCode(responseCode)) {
                        bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    } else {
                        bufferedReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    }
                    // To receive the response
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    bufferedReader.close();
                    // Prints the response
                    System.out.println(content.toString());
                    log.info("content: %s", content.toString());
                if (!isGoodResponseCode(responseCode)){
                    throw new IOException(String.format("request to %s failed with response code %s and response %s",
                            endpoint, responseCode, content.toString()
                    ));
                }
                    return content.toString();
                }
            catch(ProtocolException ex){throw new RuntimeException(ex);} //if we hit this something has gone Terribly Wrong

        }

        private boolean isGoodResponseCode(int responseCode){ return responseCode > 199 && responseCode < 300;}


        public void add_results_for_cases(Integer runId, Map<Integer, TRResult> results){
            Results res = new Results(results.entrySet().stream().map(kv->new Result(kv.getKey(),kv.getValue())).collect(Collectors.toList()));
            Gson gson = new Gson();
            String json = gson.toJson(res);
            log.info("Adding results with json %s", json);
           try{
              Post(String.format("index.php?/api/v2/add_results_for_cases/%s",runId),json);
            }catch(Exception ex){ throw new RuntimeException("Attempting to add results with json "  + json, ex);}
        }

        //Todo:  Refactor TRResult so we don't need this
        private class Result{
            public int case_id;
            public int status_id;
            public String comment;
            //public String custom_device;
            public String version;

//TODO:  Add device stuff later - need to work out the mapping between strings and Ids in TR.

            public Result(int case_id, int status_id, String comment, String custom_device) {
                this.case_id = case_id;
                this.status_id = status_id;
                this.comment = comment;
                //this.custom_device = custom_device;
                this.version = null;
            }
            public Result(int case_id, TRResult result){
                this.case_id = case_id;
                this.status_id = result.status;
                this.comment = result.comment;
                //this.custom_device = result.device;
                this.version = result.version;
            }
        }

        //Yes, this seems redundant, but makes the JSON easier - add_results_for_cases needs {results:[{...},{...},...]} which this makes GSON do automatically.
        private class Results{
            public List<Result> results;

            public Results(List<Result> result) {
                this.results = result;
            }
        }

        private void Post(String uri, String body) throws IOException{

            URL endpoint  = new URL(m_server + "/" + uri);
            log.info("Accessing %s",endpoint);
            try{
                HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                conn.setConnectTimeout(5000);
                //add authentication stuff
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestMethod("POST");
                conn.setRequestProperty ("Authorization", basicAuth);
                log.info("Start request to %s", endpoint.toString());
                //Send body
                try {
                    DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
                    log.info("body is: %s",  body);
                    wr.writeBytes(body);
                } // Creates a reader buffer
                catch (Exception e) {
                    log.error(e.getClass().getSimpleName());
                    log.error(e.getMessage());
                    System.out.println(e.getClass().getSimpleName());
                    System.out.println(e.getMessage());
                }
                Integer responseCode = conn.getResponseCode();
                log.info("response code: %s", responseCode);
                BufferedReader bufferedReader;
                if (responseCode > 199 && responseCode < 300) {
                    bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    bufferedReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }
                // To receive the response
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                bufferedReader.close();
                // Prints the response
                if (responseCode < 200 || responseCode >= 300){ throw new IOException(content.toString());}
                System.out.println(content.toString());
                log.info("content: %s", content.toString());
                //return content.toString();
            }
            catch(ProtocolException ex){throw new RuntimeException(ex);} //if we hit this something has gone Terribly Wrong

        }
    }

    private class Test{
        Test(){}
        public Integer assignedto_id; 	// The ID of the user the test is assigned to
        public Integer case_id; 	 	    // The ID of the related test case
        public String estimate;	 	    // The estimate of the related test case, e.g. “30s” or “1m 45s”
        public String estimate_forecast;// The estimate forecast of the related test case, e.g. “30s” or “1m 45s”
        public Integer id;	 	        // The unique ID of the test
        public Integer  milestone_id;	// The ID of the milestone that is linked to the test case
        public Integer priority_id;	 	// The ID of the priority that is linked to the test case
        public String refs;	 	        // A comma-separated list of references/requirements that are linked to the test case
        public Integer run_id;	 	    //The ID of the test run the test belongs to
        public Integer status_id;	 	//The ID of the current status of the test, also see get_statuses
        public String title;	 	    //The title of the related test case
        public Integer type_id;	 	    //The ID of the test case type that is linked to the test case


        @Override
        public String toString() {
            return "Test{" +
                    "assignedto_id=" + assignedto_id +
                    ", case_id=" + case_id +
                    ", estimate='" + estimate + '\'' +
                    ", estimate_forecast='" + estimate_forecast + '\'' +
                    ", id=" + id +
                    ", milestone_id=" + milestone_id +
                    ", priority_id=" + priority_id +
                    ", refs='" + refs + '\'' +
                    ", run_id=" + run_id +
                    ", status_id=" + status_id +
                    ", title='" + title + '\'' +
                    ", type_id=" + type_id +
                    '}';
        }
    }

    private class TRResult{
        public int status;
        public String comment;
        public String version;
        public String device;


        public TRResult(int status, String comment, String version, String device) {
            this.status = status;
            this.comment = comment;
            this.version = version;
            this.device = device;
        }
    }
}


