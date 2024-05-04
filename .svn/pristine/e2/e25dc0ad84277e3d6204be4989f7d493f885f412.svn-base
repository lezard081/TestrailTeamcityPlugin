<%@ page contentType="text/html;charset=UTF-8" language="java" session="true" errorPage="/runtimeError.html"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags"%>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms"%>

<bs:page>
<jsp:attribute name="page_title">TestRail Integration - ERROR</jsp:attribute>

<jsp:attribute name="body_include">

${stackTrace}
</br>
<c:forEach items="${recentLog}" var="logLine">
${logLine}
</br>
</c:forEach>
</jsp:attribute>
</bs:page>
