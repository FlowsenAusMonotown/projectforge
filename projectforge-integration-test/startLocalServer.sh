#!/bin/bash
cd /var/lib/jenkins/jobs/ProjectForge Selenium Tests/workspace/projectforge-application
nohup java -jar target/projectforge-application-${projectforge.version}.jar &
echo $! > save_pid.txt