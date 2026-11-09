@ECHO OFF
SET APP_HOME=%~dp0
IF DEFINED JAVA_HOME (SET JAVA=%JAVA_HOME%\bin\java.exe) ELSE (SET JAVA=java.exe)
"%JAVA%" -cp "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
EXIT /B %ERRORLEVEL%
