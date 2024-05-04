<%@ page contentType="text/html;charset=UTF-8" language="java" session="true" errorPage="/runtimeError.html"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms"%>

<bs:page>
<jsp:attribute name="page_title">TestRail Integration</jsp:attribute>

<jsp:attribute name="body_include">
<form action = "testRailIntegration.html"  method = "POST">
    TestRail username: ${userEmail}<br/>
    TestRail API Key: <input type="text" required="true" name="apiKey"/><br/>
    Testrail URL: <input type="text" required="true" name="TRUrl" value="https://tr.corp.frontier.co.uk/"/><br/>
    Testrail Run To Fill <input type="text" required="true" name="runId" value="${runId}"/><br/>
    Mapping Json </br> <forms:textField expandable="true" noAutoComplete="true" name="mappingJson" />
    <forms:submit label="Submit"/>
</form>

<div <c:if test="${!debug}">hidden</c:if> id="log">
<c:forEach items="${recentLog}" var="logLine">
${logLine}
</br>
</c:forEach>
</jsp:attribute>
</bs:page>
